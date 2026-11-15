package app.pusre;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;

/** Pusre'nin tek ekranı: uygulama dosyasını https kökenli bir adresten WebView'de çalıştırır. */
public class MainActivity extends Activity {
    static final String HOST = "pusre.local";
    static final String START = "https://pusre.local/index.html";
    private static final int REQ_NOTIF = 7, REQ_FILE = 11, REQ_MIC = 12, REQ_LOC = 13, REQ_SPEECH = 14;

    private WebView web;
    private String token;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;
    private PermissionRequest micReq;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    private String speechId;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        byte[] rnd = new byte[18];
        new SecureRandom().nextBytes(rnd);
        StringBuilder sb = new StringBuilder();
        for (byte b : rnd) sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1));
        token = sb.toString();

        FrameLayout root = new FrameLayout(this);
        setContentView(root);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        web.addJavascriptInterface(new Bridge(this, token), "PusreNative");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        Notify.channels(this);
        Alarms.rescheduleAll(this);
        QuakeJob.ensure(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        web.loadUrl(START);
    }

    /** Sayfada JavaScript çalıştırır (her iş parçacığından çağrılabilir). */
    void js(final String code) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    /** Uygulama dosyasını okur ve köprü anahtarını yalnızca ana sayfaya gömer; gömülü haritalar (iframe) bu anahtarı göremez. */
    private byte[] page() {
        try (InputStream in = getAssets().open("pusre.html"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String html = new String(out.toByteArray(), StandardCharsets.UTF_8);
            String inject = "<script>window.__PT=" + JSONObject.quote(token) + ";</script>";
            int i = html.indexOf("<head>");
            html = i >= 0 ? html.substring(0, i + 6) + inject + html.substring(i + 6) : inject + html;
            return html.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ("<html><body style='font-family:sans-serif;padding:24px'>Uygulama dosyası okunamadı: " + e + "</body></html>").getBytes(StandardCharsets.UTF_8);
        }
    }

    private final class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (u == null || !HOST.equals(u.getHost())) return null;
            String p = u.getPath();
            if (request.isForMainFrame() && (p == null || p.isEmpty() || "/".equals(p) || "/index.html".equals(p))) {
                return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(page()));
            }
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (u != null && HOST.equals(u.getHost())) return false;
            if (!request.isForMainFrame()) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception ignored) { }
            return true;
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            recreate();
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCb != null) fileCb.onReceiveValue(null);
            fileCb = callback;
            dropCam();
            String type = "*/*";
            String[] types = params.getAcceptTypes();
            if (types != null && types.length > 0 && types[0] != null && types[0].contains("/")) type = types[0];
            boolean image = type.startsWith("image/");
            Intent get = new Intent(Intent.ACTION_GET_CONTENT);
            get.addCategory(Intent.CATEGORY_OPENABLE);
            get.setType(type);
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) get.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            Intent chooser = Intent.createChooser(get, image ? "Resim seç ya da çek" : "Dosya seç");
            if (image && Build.VERSION.SDK_INT >= 29) {
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "pusre_" + System.currentTimeMillis() + ".jpg");
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                    camUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (camUri != null) {
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                    }
                } catch (Exception e) {
                    camUri = null;
                }
            }
            try {
                startActivityForResult(chooser, REQ_FILE);
            } catch (Exception e) {
                fileCb = null;
                dropCam();
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            boolean audio = false;
            for (String r : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) audio = true;
            Uri o = request.getOrigin();
            if (!audio || o == null || !HOST.equals(o.getHost())) {
                request.deny();
                return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                if (micReq != null) micReq.deny();
                micReq = request;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            }
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (origin == null || !origin.contains(HOST)) {
                callback.invoke(origin, false, false);
                return;
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                callback.invoke(origin, true, false);
            } else {
                geoCb = callback;
                geoOrigin = origin;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            }
        }
    }

    /** Kullanılmayan kamera kaydını galeriden siler. */
    private void dropCam() {
        if (camUri == null) return;
        try {
            getContentResolver().delete(camUri, null, null);
        } catch (Exception ignored) { }
        camUri = null;
    }

    private boolean hasBytes(Uri u) {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            return in != null && in.read() != -1;
        } catch (Exception e) {
            return false;
        }
    }

    /** Sesle yazma: telefonun konuşma tanıma penceresini açar, sonucu sayfaya verir. */
    void startSpeech(final String id) {
        runOnUiThread(() -> {
            speechId = id;
            try {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuş");
                startActivityForResult(i, REQ_SPEECH);
            } catch (Exception e) {
                speechId = null;
                js("window.__ps&&__ps(" + JSONObject.quote(id) + ",null)");
            }
        });
    }

    void shareText(final String title, final String text) {
        runOnUiThread(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, title);
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Paylaş"));
            } catch (Exception ignored) { }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            Uri[] res = null;
            if (resultCode == RESULT_OK) {
                ClipData cd = data != null ? data.getClipData() : null;
                if (cd != null && cd.getItemCount() > 0) {
                    res = new Uri[cd.getItemCount()];
                    for (int i = 0; i < res.length; i++) res[i] = cd.getItemAt(i).getUri();
                } else if (data != null && data.getData() != null) {
                    res = new Uri[]{data.getData()};
                } else if (camUri != null && hasBytes(camUri)) {
                    res = new Uri[]{camUri};
                    camUri = null;
                }
            }
            dropCam();
            if (fileCb != null) fileCb.onReceiveValue(res);
            fileCb = null;
        } else if (requestCode == REQ_SPEECH) {
            String id = speechId;
            speechId = null;
            String text = null;
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) text = r.get(0);
            }
            if (id != null) js("window.__ps&&__ps(" + JSONObject.quote(id) + "," + (text == null ? "null" : JSONObject.quote(text)) + ")");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean ok = false;
        for (int g : grantResults) if (g == PackageManager.PERMISSION_GRANTED) ok = true;
        if (requestCode == REQ_MIC && micReq != null) {
            if (ok) micReq.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else micReq.deny();
            micReq = null;
        } else if (requestCode == REQ_LOC && geoCb != null) {
            geoCb.invoke(geoOrigin, ok, false);
            geoCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            moveTaskToBack(true);
            return;
        }
        // Önce sayfa kendi içinde geri gitsin (açık pencereyi kapat, ana sayfaya dön); gidecek yer yoksa uygulamayı arka plana al.
        web.evaluateJavascript("(window.pusreBack&&window.pusreBack())?1:0", v -> {
            if (!"1".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.evaluateJavascript("window.pusreVis&&pusreVis(true)", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.pusreVis&&pusreVis(false)", null);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            ViewGroup p = (ViewGroup) web.getParent();
            if (p != null) p.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
