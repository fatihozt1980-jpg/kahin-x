package app.kahinx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Programın çalıştığı tek WebView. Ekrandan bağımsız yaşar: arayüz kapansa da servis onu canlı tutar,
 * böylece tarama, öğrenme ve canlı akış arka planda sürer.
 */
final class Engine {
    /** Sayfaya https kökenli bir adres verir: Binance istekleri normal CORS ile çalışır, kayıtlar (localStorage) kalıcı olur. */
    static final String BASE = "https://kahinx.local/";

    private static MutableContextWrapper wrapper;
    private static WebView web;

    private Engine() {}

    /** Arka planda da "görünür" sayılan WebView: sayfa gizlendi sanıp zamanlayıcılarını kısmaz. */
    static final class BgWebView extends WebView {
        BgWebView(Context c) { super(c); }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(View.VISIBLE);
        }
    }

    static WebView peek() { return web; }

    @SuppressLint("SetJavaScriptEnabled")
    static WebView get(Context ctx) {
        if (web != null) return web;
        final Context app = ctx.getApplicationContext();
        wrapper = new MutableContextWrapper(app);
        WebView w = new BgWebView(wrapper);
        w.setBackgroundColor(0xFF0A0E16);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        w.addJavascriptInterface(new Bridge(app), "KahinNative");
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && u.toString().startsWith(BASE)) return false;
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, u);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    app.startActivity(i);
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Sistem WebView sürecini öldürdüyse (bellek baskısı) motoru yeniden kur.
                destroy();
                get(app);
                MainActivity a = MainActivity.current();
                if (a != null) a.attachWeb();
                return true;
            }
        });
        web = w;
        w.loadDataWithBaseURL(BASE, readAsset(app, "kahin-x.html"), "text/html", "UTF-8", null);
        return w;
    }

    /** Arayüz açıldığında etkinliğin bağlamını ver (açılır listeler, dosya seçici için gerekir); kapanınca uygulama bağlamına dön. */
    static void setUi(Context activityOrNull) {
        if (wrapper == null) return;
        wrapper.setBaseContext(activityOrNull != null ? activityOrNull : wrapper.getApplicationContext());
    }

    static void detach() {
        if (web == null) return;
        ViewParent p = web.getParent();
        if (p instanceof ViewGroup) ((ViewGroup) p).removeView(web);
    }

    static void destroy() {
        WebView w = web;
        web = null;
        if (w == null) return;
        try {
            ViewParent p = w.getParent();
            if (p instanceof ViewGroup) ((ViewGroup) p).removeView(w);
            w.stopLoading();
            w.destroy();
        } catch (Exception ignored) { }
        wrapper = null;
    }

    private static String readAsset(Context c, String name) {
        try (InputStream in = c.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<html><body style='background:#0a0e16;color:#fff;font-family:sans-serif;padding:24px'>Program dosyası okunamadı: " + e + "</body></html>";
        }
    }
}
