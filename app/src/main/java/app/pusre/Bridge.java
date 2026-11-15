package app.pusre;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sayfadaki uygulamanın Android'e seslendiği köprü: window.PusreNative.
 * Her çağrı, yalnızca ana sayfaya gömülen anahtarı ister; gömülü üçüncü taraf çerçeveler köprüyü kullanamaz.
 */
final class Bridge {
    private static final int MAX_BODY = 4 * 1024 * 1024;
    private final MainActivity act;
    private final String token;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Map<String, OutputStream> saves = new ConcurrentHashMap<>();
    private final Map<String, String> saveNames = new ConcurrentHashMap<>();
    private final Map<String, Uri> saveUris = new ConcurrentHashMap<>();
    private int saveSeq = 0;

    Bridge(MainActivity act, String token) {
        this.act = act;
        this.token = token;
    }

    private boolean ok(String t) { return token.equals(t); }

    @JavascriptInterface
    public boolean isApp() { return true; }

    /** Tarayıcının köken kısıtına (CORS) takılan adresleri doğrudan indirir; sonucu __pn(id, durum, base64, karakterKümesi) ile verir. */
    @JavascriptInterface
    public void httpGet(String tok, final String id, final String url) {
        if (!ok(tok)) return;
        pool.execute(() -> {
            int status = 0;
            String b64 = "";
            String charset = "";
            HttpURLConnection c = null;
            try {
                URL u = new URL(url);
                if (!"https".equals(u.getProtocol())) throw new IllegalArgumentException("https değil");
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36");
                c.setRequestProperty("Accept", "application/json,text/html,application/xml;q=0.9,*/*;q=0.8");
                c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.6");
                status = c.getResponseCode();
                String ct = c.getContentType();
                if (ct != null) {
                    int i = ct.toLowerCase().indexOf("charset=");
                    if (i >= 0) charset = ct.substring(i + 8).replace("\"", "").split(";")[0].trim();
                }
                InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0 && out.size() < MAX_BODY) out.write(buf, 0, n);
                    in.close();
                    b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                }
            } catch (Exception e) {
                status = 0;
                b64 = "";
            } finally {
                if (c != null) c.disconnect();
            }
            act.js("window.__pn&&__pn(" + JSONObject.quote(id) + "," + status + ",\"" + b64 + "\"," + JSONObject.quote(charset) + ")");
        });
    }

    /** Hatırlatmaları telefon alarmına işler. json: [{id, t (ms), title, body}] */
    @JavascriptInterface
    public void syncReminders(String tok, String json) {
        if (!ok(tok)) return;
        try {
            Alarms.sync(act.getApplicationContext(), json);
        } catch (Exception ignored) { }
    }

    /** Arka plan deprem takibi için konumu ve açık/kapalı durumunu kaydeder. */
    @JavascriptInterface
    public void quakeWatch(String tok, double lat, double lon, boolean on) {
        if (!ok(tok)) return;
        try {
            SharedPreferences p = act.getSharedPreferences(QuakeJob.PREF, 0);
            p.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon).putBoolean("on", on).apply();
            QuakeJob.ensure(act.getApplicationContext());
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void speech(String tok, String id) {
        if (!ok(tok)) return;
        act.startSpeech(id);
    }

    @JavascriptInterface
    public void share(String tok, String title, String text) {
        if (!ok(tok)) return;
        act.shareText(title, text);
    }

    /** Dosya kaydetme (yedek, resim): parça parça yazılır. Dönen değer işlem numarasıdır; boşsa başlatılamadı. */
    @JavascriptInterface
    public String saveBegin(String tok, String name, String mime) {
        if (!ok(tok)) return "";
        try {
            String id = String.valueOf(++saveSeq);
            String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            OutputStream out;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = act.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return "";
                out = act.getContentResolver().openOutputStream(uri);
                if (out == null) return "";
                saveUris.put(id, uri);
                saveNames.put(id, "İndirilenler/" + safe);
            } else {
                File f = new File(act.getExternalFilesDir(null), safe);
                out = new FileOutputStream(f);
                saveNames.put(id, f.getAbsolutePath());
            }
            saves.put(id, out);
            return id;
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public boolean saveChunk(String tok, String id, String b64) {
        if (!ok(tok)) return false;
        OutputStream out = saves.get(id);
        if (out == null) return false;
        try {
            out.write(Base64.decode(b64, Base64.DEFAULT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Kaydı bitirir; kullanıcıya gösterilecek mesajı döndürür. okFlag false ise yarım dosya silinir. */
    @JavascriptInterface
    public String saveEnd(String tok, String id, boolean okFlag) {
        if (!ok(tok)) return "";
        OutputStream out = saves.remove(id);
        String name = saveNames.remove(id);
        Uri uri = saveUris.remove(id);
        try {
            if (out != null) out.close();
        } catch (Exception ignored) { }
        if (out == null) return "Kaydedilemedi";
        if (!okFlag) {
            try {
                if (uri != null) act.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) { }
            return "Kaydedilemedi";
        }
        return "Kaydedildi: " + name;
    }
}
