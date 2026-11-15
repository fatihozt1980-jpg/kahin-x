package app.anindaceviri;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Arka planda çalışan çeviri servisi: ekranı yakalar, yazıyı okur, çevirir ve kutuları çizer.
 * Neyin ne zaman okunup çevrileceğine Motor karar verir; bu sınıf telefon tarafını sağlar.
 */
public class CeviriServisi extends Service {
    static final String BASLAT = "app.anindaceviri.BASLAT";
    static final String DURDUR = "app.anindaceviri.DURDUR";
    static final String EK_KOD = "kod";
    static final String EK_VERI = "veri";
    /** Ana ekranın durumu göstermesi için. */
    static volatile boolean calisiyor = false;

    private static final String TAG = "AnindaCeviri";
    private static final String KANAL = "ceviri";
    private static final int BILDIRIM = 41;
    private final Handler ana = new Handler(Looper.getMainLooper());
    private HandlerThread isIpligi, kareIpligi;
    private Handler isci, kareci;
    private WindowManager wm;
    private Display ekran;
    private float yogunluk;

    private MediaProjection projeksiyon;
    private MediaProjection.Callback projeksiyonGeri;
    private VirtualDisplay sanalEkran;

    // --- kareKilidi ile korunanlar: ekran görüntüsü kaynağı ---
    private final Object kareKilidi = new Object();
    private ImageReader okuyucu;
    private Image sonGoruntu;
    private long kareNo;

    private int ekranDpi;
    private long islenenKare = -1;

    private TextRecognizer tanici;
    private Translator cevirmen;

    private KatmanGorunumu katman;
    private BalonGorunumu balon;
    private WindowManager.LayoutParams balonLp;
    private int balonBoy;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ekran = ((DisplayManager) getSystemService(DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
        yogunluk = getResources().getDisplayMetrics().density;
        motor.yogunluk = yogunluk;
        isIpligi = new HandlerThread("ceviri-is");
        isIpligi.start();
        isci = new Handler(isIpligi.getLooper());
        kareIpligi = new HandlerThread("ceviri-kare");
        kareIpligi.start();
        kareci = new Handler(kareIpligi.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String eylem = intent == null ? null : intent.getAction();
        if (!BASLAT.equals(eylem)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            // Android 14+: ekran yakalamadan önce "ekran yakalama" türünde ön plan servisi şart.
            Notification n = bildirim();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(BILDIRIM, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(BILDIRIM, n);
            }
            if (projeksiyon == null) {
                int kod = intent.getIntExtra(EK_KOD, 0);
                Intent veri = intent.getParcelableExtra(EK_VERI);
                if (veri == null) throw new IllegalStateException("ekran izni yok");
                kur(kod, veri);
            }
        } catch (Throwable t) {
            Log.e(TAG, "başlatma", t);
            Toast.makeText(this, "Çeviri başlatılamadı: " + t.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void kur(int kod, Intent veri) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        MediaProjection p = mpm.getMediaProjection(kod, veri);
        if (p == null) throw new IllegalStateException("ekran yakalama açılamadı");
        projeksiyon = p;
        projeksiyonGeri = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                ana.post(() -> stopSelf());
            }
        };
        p.registerCallback(projeksiyonGeri, ana);

        tanici = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cevirmen = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(Ayarlar.kaynakDil(this))
                .setTargetLanguage(TranslateLanguage.TURKISH)
                .build());
        // Ana ekran zaten indirdi; burada yalnızca emniyet için (inmişse hemen döner).
        cevirmen.downloadModelIfNeeded(new DownloadConditions.Builder().build());

        motor.olc();
        motor.yakalamaKur();
        katmanEkle();
        balonEkle();
        calisiyor = true;
        isci.postDelayed(tikGorevi, 300);
    }

    // ------------------------------------------------------------------ motorun telefon tarafı

    private final Motor motor = new Motor() {
        @Override
        boolean olc() {
            DisplayMetrics dm = new DisplayMetrics();
            ekran.getRealMetrics(dm);
            if (dm.widthPixels == ekranW && dm.heightPixels == ekranH) return false;
            ekranW = dm.widthPixels;
            ekranH = dm.heightPixels;
            ekranDpi = dm.densityDpi;
            return true;
        }

        /** Yakalama yüzeyini ekran boyutuna göre kurar ya da telefon dönünce yeniden boyutlar. */
        @Override
        void yakalamaKur() {
            int kisa = Math.min(ekranW, ekranH);
            // Çok yüksek çözünürlüklü ekranlarda kısa kenarı 1080 piksele indir: yazı okuma hızlanır.
            float o = kisa > 1080 ? 1080f / kisa : 1f;
            int w = Math.max(2, Math.round(ekranW * o));
            int h = Math.max(2, Math.round(ekranH * o));
            ImageReader yeni = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
            ImageReader eski;
            synchronized (kareKilidi) {
                if (sonGoruntu != null) {
                    sonGoruntu.close();
                    sonGoruntu = null;
                }
                eski = okuyucu;
                okuyucu = yeni;
                yakW = w;
                yakH = h;
            }
            yeni.setOnImageAvailableListener(CeviriServisi.this::kareGeldi, kareci);
            int dpi = Math.max(120, Math.round(ekranDpi * o));
            if (sanalEkran == null) {
                sanalEkran = projeksiyon.createVirtualDisplay("aninda-ceviri", w, h, dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, yeni.getSurface(), null, null);
            } else {
                sanalEkran.resize(w, h, dpi);
                sanalEkran.setSurface(yeni.getSurface());
            }
            if (eski != null) eski.close();
        }

        @Override
        int kareAl() {
            synchronized (kareKilidi) {
                Image g = sonGoruntu;
                if (g == null) return 0;
                if (islenenKare == kareNo && ham != null) return 1;
                try {
                    Image.Plane d = g.getPlanes()[0];
                    ByteBuffer tampon = d.getBuffer();
                    int piksel = d.getPixelStride();
                    if (piksel != 4) return 0;
                    // Satırlar belleğe hizalı olabilir: genişlik ekran genişliğinden biraz büyük çıkabilir.
                    int gen = d.getRowStride() / piksel;
                    int yuk = g.getHeight();
                    if (ham == null || ham.getWidth() != gen || ham.getHeight() != yuk) {
                        ham = Bitmap.createBitmap(gen, yuk, Bitmap.Config.ARGB_8888);
                    }
                    tampon.rewind();
                    int gerek = ham.getByteCount();
                    if (tampon.remaining() >= gerek) {
                        ham.copyPixelsFromBuffer(tampon);
                    } else {
                        // Son satırda hizalama boşluğu yoksa tampon birkaç bayt kısa kalır.
                        ByteBuffer tam = ByteBuffer.allocate(gerek);
                        tam.put(tampon);
                        tam.rewind();
                        ham.copyPixelsFromBuffer(tam);
                    }
                    islenenKare = kareNo;
                    return 2;
                } catch (Throwable t) {
                    Log.w(TAG, "kare kopyalama", t);
                    return 0;
                }
            }
        }

        @Override
        List<Blok> oku(Bitmap b) throws Exception {
            TextRecognizer t = tanici;
            if (t == null) throw new IllegalStateException("okuyucu kapalı");
            Text sonuc = Tasks.await(t.process(InputImage.fromBitmap(b, 0)), 10, TimeUnit.SECONDS);
            ArrayList<Blok> liste = new ArrayList<>();
            for (Text.TextBlock blok : sonuc.getTextBlocks()) {
                Rect kutu = blok.getBoundingBox();
                List<Text.Line> satirlar = blok.getLines();
                if (kutu == null || satirlar.isEmpty()) continue;
                float guven = 0, yuk = 0;
                int n = 0;
                for (Text.Line s : satirlar) {
                    guven += s.getConfidence();
                    Rect sk = s.getBoundingBox();
                    if (sk != null) {
                        yuk += sk.height();
                        n++;
                    }
                }
                liste.add(new Blok(blok.getText(), kutu, satirlar.size(), n > 0 ? yuk / n : 0f,
                        guven / satirlar.size(), blok.getRecognizedLanguage()));
            }
            return liste;
        }

        @Override
        String cevir(String metin) throws Exception {
            Translator c = cevirmen;
            if (c == null) throw new IllegalStateException("çevirmen kapalı");
            return Tasks.await(c.translate(metin), 8, TimeUnit.SECONDS);
        }

        @Override
        void goster(final ArrayList<Etiket> kopya) {
            ana.post(() -> {
                if (katman != null) katman.ayarla(kopya);
            });
        }

        @Override
        void mesgul(final boolean m) {
            ana.post(() -> {
                if (balon != null) balon.mesgul(m);
            });
        }

        @Override
        void ekranDondu() {
            ana.postDelayed(CeviriServisi.this::balonAlaniOlc, 400);
        }
    };

    /** Ayrı iş parçacığında: her yeni ekran görüntüsünde en günceli elde tutulur. */
    private void kareGeldi(ImageReader r) {
        synchronized (kareKilidi) {
            try {
                Image yeni = r.acquireLatestImage();
                if (yeni == null) return;
                if (r != okuyucu) {
                    yeni.close();
                    return;
                }
                if (sonGoruntu != null) sonGoruntu.close();
                sonGoruntu = yeni;
                kareNo++;
            } catch (Throwable t) {
                Log.w(TAG, "kare", t);
            }
        }
    }

    private final Runnable tikGorevi = new Runnable() {
        @Override
        public void run() {
            if (motor.bitti) return;
            long sonra = Motor.TIK;
            try {
                sonra = motor.adim();
            } catch (Throwable t) {
                Log.e(TAG, "adım", t);
            }
            if (!motor.bitti) isci.postDelayed(this, sonra);
        }
    };

    // ------------------------------------------------------------------ katman ve baloncuk

    private void katmanEkle() {
        katman = new KatmanGorunumu(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // Android 12+: dokunmaların alttaki uygulamaya geçmesi için pencere en fazla %80 opak olabilir.
        float alfa = 0.8f;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                InputManager im = (InputManager) getSystemService(INPUT_SERVICE);
                alfa = Math.min(alfa, im.getMaximumObscuringOpacityForTouch());
            } catch (Throwable ignored) {
            }
        }
        lp.alpha = alfa;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0);
        }
        wm.addView(katman, lp);
    }

    private void balonEkle() {
        balon = new BalonGorunumu(this);
        balonBoy = Math.round(52 * yogunluk);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                balonBoy, balonBoy,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.max(0, motor.ekranW - balonBoy - Math.round(10 * yogunluk));
        lp.y = motor.ekranH / 3;
        balonLp = lp;
        balon.setOnTouchListener(new BalonDokunma());
        wm.addView(balon, lp);
        balon.post(this::balonAlaniOlc);
        ana.postDelayed(this::balonAlaniOlc, 500);
    }

    /** Baloncuğun ekrandaki yerini kaydeder: yazı okurken ve değişim ölçerken o alan yok sayılır. */
    private void balonAlaniOlc() {
        BalonGorunumu b = balon;
        if (b == null || !b.isAttachedToWindow()) return;
        int[] k = new int[2];
        b.getLocationOnScreen(k);
        int p = Math.round(6 * yogunluk);
        synchronized (motor.balonAlan) {
            motor.balonAlan.set(k[0] - p, k[1] - p, k[0] + b.getWidth() + p, k[1] + b.getHeight() + p);
        }
    }

    /** Baloncuğa dokunuldu: çeviriyi aç ya da kapat. */
    private void dokunuldu() {
        motor.aktif = !motor.aktif;
        balon.acik(motor.aktif);
        isci.post(motor::sifirla);
    }

    /** Baloncuğa uzun basıldı: uygulamayı tamamen kapat. */
    private void uzunBasildi() {
        Toast.makeText(this, "Anında Çeviri kapatıldı", Toast.LENGTH_SHORT).show();
        stopSelf();
    }

    private final class BalonDokunma implements View.OnTouchListener {
        private final int esik = ViewConfiguration.get(CeviriServisi.this).getScaledTouchSlop();
        private int bx, by;
        private float tx, ty;
        private boolean surukle, uzun;
        private final Runnable uzunGorev = new Runnable() {
            @Override
            public void run() {
                uzun = true;
                uzunBasildi();
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    bx = balonLp.x;
                    by = balonLp.y;
                    tx = e.getRawX();
                    ty = e.getRawY();
                    surukle = false;
                    uzun = false;
                    ana.postDelayed(uzunGorev, 900);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - tx, dy = e.getRawY() - ty;
                    if (!surukle && (Math.abs(dx) > esik || Math.abs(dy) > esik)) {
                        surukle = true;
                        ana.removeCallbacks(uzunGorev);
                    }
                    if (surukle && !uzun) {
                        balonLp.x = Math.max(0, Math.min(motor.ekranW - balonBoy, bx + Math.round(dx)));
                        balonLp.y = Math.max(0, Math.min(motor.ekranH - balonBoy, by + Math.round(dy)));
                        try {
                            wm.updateViewLayout(v, balonLp);
                        } catch (Throwable ignored) {
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    ana.removeCallbacks(uzunGorev);
                    if (surukle) {
                        ana.postDelayed(CeviriServisi.this::balonAlaniOlc, 150);
                    } else if (!uzun) {
                        dokunuldu();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    ana.removeCallbacks(uzunGorev);
                    return true;
                default:
                    return false;
            }
        }
    }

    // ------------------------------------------------------------------ bildirim ve kapanış

    private Notification bildirim() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(KANAL, "Çeviri", NotificationManager.IMPORTANCE_LOW));
        PendingIntent dur = PendingIntent.getService(this, 1,
                new Intent(this, CeviriServisi.class).setAction(DURDUR),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent ac = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, KANAL)
                .setSmallIcon(R.drawable.ic_bildirim)
                .setContentTitle("Anında Çeviri çalışıyor")
                .setContentText("Baloncuğa dokun: çeviri açılır ya da kapanır")
                .setContentIntent(ac)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_bildirim), "Durdur", dur).build())
                .build();
    }

    @Override
    public void onDestroy() {
        motor.bitti = true;
        motor.aktif = false;
        calisiyor = false;
        ana.removeCallbacksAndMessages(null);
        if (isci != null) isci.removeCallbacksAndMessages(null);
        try {
            if (projeksiyon != null && projeksiyonGeri != null) projeksiyon.unregisterCallback(projeksiyonGeri);
        } catch (Throwable ignored) {
        }
        try {
            if (sanalEkran != null) sanalEkran.release();
        } catch (Throwable ignored) {
        }
        try {
            if (projeksiyon != null) projeksiyon.stop();
        } catch (Throwable ignored) {
        }
        synchronized (kareKilidi) {
            try {
                if (sonGoruntu != null) sonGoruntu.close();
            } catch (Throwable ignored) {
            }
            sonGoruntu = null;
            try {
                if (okuyucu != null) okuyucu.close();
            } catch (Throwable ignored) {
            }
            okuyucu = null;
        }
        try {
            if (katman != null) wm.removeView(katman);
        } catch (Throwable ignored) {
        }
        try {
            if (balon != null) wm.removeView(balon);
        } catch (Throwable ignored) {
        }
        katman = null;
        balon = null;
        if (isIpligi != null) isIpligi.quitSafely();
        if (kareIpligi != null) kareIpligi.quitSafely();
        try {
            if (tanici != null) tanici.close();
        } catch (Throwable ignored) {
        }
        try {
            if (cevirmen != null) cevirmen.close();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
