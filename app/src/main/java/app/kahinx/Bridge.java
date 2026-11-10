package app.kahinx;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Sayfadaki programın Android'e seslendiği köprü: window.KahinNative */
final class Bridge {
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());

    Bridge(Context app) { this.app = app; }

    @JavascriptInterface
    public boolean isApp() { return true; }

    /** Sinyal / ani hareket bildirimi. Aynı etiketli yeni bildirim eskisinin yerine geçer. */
    @JavascriptInterface
    public void push(String title, String body, String tag) {
        try { Notes.alert(app, title, body, tag); } catch (Exception ignored) { }
    }

    /** Sabit "çalışıyor" satırındaki durum metni. */
    @JavascriptInterface
    public void status(String text) {
        try { Notes.status(app, text); } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void copy(final String text) {
        main.post(() -> {
            try {
                ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("KAHİN X yedek", text));
            } catch (Exception ignored) { }
        });
    }

    /** Yedeği İndirilenler klasörüne yazar; kullanıcıya gösterilecek mesajı döndürür. */
    @JavascriptInterface
    public String saveFile(String name, String text) {
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = app.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return "Yedek kaydedilemedi";
                try (OutputStream out = app.getContentResolver().openOutputStream(uri)) {
                    if (out == null) return "Yedek kaydedilemedi";
                    out.write(data);
                }
                return "Yedek kaydedildi: İndirilenler/" + name;
            }
            File dir = app.getExternalFilesDir(null);
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
            return "Yedek kaydedildi: " + f.getAbsolutePath();
        } catch (Exception e) {
            return "Yedek kaydedilemedi: " + e.getMessage();
        }
    }

    @JavascriptInterface
    public boolean ignoringBattery() {
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(app.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /** Telefonun uygulamayı uyutmaması için pil kısıtlamasını kaldırma penceresini açar. */
    @JavascriptInterface
    public void batteryOpt() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + app.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(i);
            } catch (Exception ignored) { }
        }
    }
}
