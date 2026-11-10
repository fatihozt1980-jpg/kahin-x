package app.kahinx;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.WebView;

/**
 * Ön plan servisi: bildirim çubuğunda sabit bir satırla uygulamayı canlı tutar. Ekran kapalıyken de
 * işlemciyi uyanık tutar ve dakikada bir programın kalp atışını tetikler (otomatik tarama + öğrenme).
 */
public class KeepAliveService extends Service {
    static final String ACTION_STOP = "app.kahinx.STOP";
    static volatile boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    private final Runnable beat = new Runnable() {
        @Override
        public void run() {
            try {
                WebView w = Engine.get(KeepAliveService.this);
                w.evaluateJavascript("window.kxTick&&window.kxTick()", null);
            } catch (Throwable ignored) { }
            handler.postDelayed(this, 60000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Notes.channels(this);
        running = true;
        goForeground("Başlatılıyor…");
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kahinx:tarama");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignored) { }
        Engine.get(this);
        handler.postDelayed(beat, 60000);
    }

    private void goForeground(String text) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notes.ID_RUN, Notes.running(this, text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(Notes.ID_RUN, Notes.running(this, text));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    /** Bildirimdeki "Durdur": arka plan çalışmasını tamamen kapatır. */
    private void shutdown() {
        running = false;
        handler.removeCallbacks(beat);
        MainActivity a = MainActivity.current();
        if (a != null) a.finishAndRemoveTask();
        Engine.destroy();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(beat);
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
