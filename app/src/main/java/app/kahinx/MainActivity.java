package app.kahinx;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/** Arayüz: çalışan WebView'i ekrana bağlar. Kapansa bile program serviste yaşamaya devam eder. */
public class MainActivity extends Activity {
    private static final int REQ_NOTIF = 7;
    private static final int REQ_FILE = 11;
    private static WeakReference<MainActivity> cur = new WeakReference<>(null);

    private FrameLayout root;
    private ValueCallback<Uri[]> fileCb;

    static MainActivity current() { return cur.get(); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cur = new WeakReference<>(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0E16);
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        startForegroundService(new Intent(this, KeepAliveService.class));
        attachWeb();
    }

    /** Motorun WebView'ini bu ekrana bağlar (daha önce başka bir yere bağlıysa oradan söker). */
    void attachWeb() {
        WebView w = Engine.get(this);
        Engine.detach();
        Engine.setUi(this);
        root.removeAllViews();
        root.addView(w, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = callback;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(Intent.createChooser(i, "Yedek dosyasını seç"), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        w.requestFocus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCb != null) {
            Uri[] res = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) res = new Uri[]{data.getData()};
            fileCb.onReceiveValue(res);
            fileCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        WebView w = Engine.peek();
        if (w == null) { moveTaskToBack(true); return; }
        // Önce sayfa kendi içinde geri gitsin (açık detayı kapat, Radar'a dön); gidecek yer yoksa uygulamayı arka plana al.
        w.evaluateJavascript("(window.kxBack&&window.kxBack())?1:0", v -> {
            if (!"1".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    protected void onDestroy() {
        WebView w = Engine.peek();
        if (w != null) {
            w.setWebChromeClient(null);
            if (w.getParent() == root) root.removeView(w);
        }
        Engine.setUi(null);
        if (cur.get() == this) cur = new WeakReference<>(null);
        super.onDestroy();
    }
}
