package app.anindaceviri;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

/** Ana ekran: dil seçimi, izinler ve başlat/durdur. */
public class MainActivity extends Activity {
    private static final int IST_EKRAN = 11;
    private static final int IST_BILDIRIM = 12;

    private final Handler el = new Handler(Looper.getMainLooper());
    private TextView durum;
    private Button baslat, izin;
    private Spinner dil;
    private boolean hazirlaniyor;

    private final Runnable yenile = new Runnable() {
        @Override
        public void run() {
            guncelle();
            el.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle kayit) {
        super.onCreate(kayit);
        setContentView(R.layout.ana);
        durum = findViewById(R.id.durum);
        baslat = findViewById(R.id.baslat);
        izin = findViewById(R.id.izin);
        dil = findViewById(R.id.dil);

        ArrayAdapter<String> a = new ArrayAdapter<>(this, R.layout.dil_satiri, Ayarlar.ADLAR);
        a.setDropDownViewResource(R.layout.dil_satiri_acik);
        dil.setAdapter(a);
        dil.setSelection(Ayarlar.dilSirasi(this));
        dil.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int sira, long id) {
                Ayarlar.dilSirasiYaz(MainActivity.this, sira);
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        izin.setOnClickListener(v -> izinIste());
        baslat.setOnClickListener(v -> baslatTikla());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, IST_BILDIRIM);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        el.removeCallbacks(yenile);
        yenile.run();
    }

    @Override
    protected void onPause() {
        el.removeCallbacks(yenile);
        super.onPause();
    }

    private void guncelle() {
        boolean ustte = Settings.canDrawOverlays(this);
        boolean calisiyor = CeviriServisi.calisiyor;
        izin.setVisibility(ustte ? View.GONE : View.VISIBLE);
        dil.setEnabled(!calisiyor && !hazirlaniyor);
        baslat.setEnabled(!hazirlaniyor);
        if (calisiyor) {
            baslat.setText(R.string.durdur);
            baslat.setBackgroundResource(R.drawable.dugme_dur);
            durum.setText(R.string.durum_calisiyor);
        } else if (hazirlaniyor) {
            baslat.setText(R.string.hazirlaniyor);
            baslat.setBackgroundResource(R.drawable.dugme);
            durum.setText(R.string.durum_indiriliyor);
        } else {
            baslat.setText(R.string.baslat);
            baslat.setBackgroundResource(R.drawable.dugme);
            durum.setText(ustte ? R.string.durum_hazir : R.string.durum_izin);
        }
    }

    private void izinIste() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable t2) {
                Toast.makeText(this, R.string.izin_acilamadi, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void baslatTikla() {
        if (CeviriServisi.calisiyor) {
            stopService(new Intent(this, CeviriServisi.class));
            el.postDelayed(this::guncelle, 300);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            izinIste();
            return;
        }
        if (hazirlaniyor) return;
        hazirlaniyor = true;
        guncelle();
        // Dil paketi telefonda yoksa bir kez indirilir (yaklaşık 30 MB); sonrası internetsiz çalışır.
        final Translator t = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(Ayarlar.kaynakDil(this))
                .setTargetLanguage(TranslateLanguage.TURKISH)
                .build());
        t.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(bos -> {
                    kapat(t);
                    hazirlaniyor = false;
                    guncelle();
                    if (!isFinishing() && !isDestroyed()) ekranIzniIste();
                })
                .addOnFailureListener(hata -> {
                    kapat(t);
                    hazirlaniyor = false;
                    guncelle();
                    Toast.makeText(this, R.string.paket_inmedi, Toast.LENGTH_LONG).show();
                });
    }

    private static void kapat(Translator t) {
        try {
            t.close();
        } catch (Throwable ignored) {
        }
    }

    private void ekranIzniIste() {
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try {
            Intent i = Build.VERSION.SDK_INT >= 34 ? Yeni.tumEkran(m) : m.createScreenCaptureIntent();
            startActivityForResult(i, IST_EKRAN);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.ekran_izni_yok, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int istek, int sonuc, Intent veri) {
        super.onActivityResult(istek, sonuc, veri);
        if (istek != IST_EKRAN) return;
        if (sonuc != RESULT_OK || veri == null) {
            Toast.makeText(this, R.string.ekran_izni_yok, Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, CeviriServisi.class)
                .setAction(CeviriServisi.BASLAT)
                .putExtra(CeviriServisi.EK_KOD, sonuc)
                .putExtra(CeviriServisi.EK_VERI, veri);
        try {
            startForegroundService(i);
            el.postDelayed(this::guncelle, 400);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.baslamadi, Toast.LENGTH_LONG).show();
        }
    }

    /** Android 14+: izin penceresinde "tek uygulama" seçeneği çıkmasın, doğrudan tüm ekran istensin. */
    private static final class Yeni {
        static Intent tumEkran(MediaProjectionManager m) {
            return m.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        }
    }
}
