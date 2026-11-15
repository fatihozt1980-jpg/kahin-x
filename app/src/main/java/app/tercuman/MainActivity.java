package app.tercuman;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/** Tek ekran, üç sekme: Konuşma (sesli tercüman), Yazı ve Kamera. */
public class MainActivity extends ComponentActivity implements Dinleyici.Olay {
    private static final int IST_MIK = 21, IST_KAM = 22;
    /** Konuşma durumları: boşta, karşı tarafı dinliyor, beni dinliyor, çeviriyi hazırlıyor ya da okuyor. */
    private static final int BOS = 0, KARSI = 1, BEN = 2, SOYLUYOR = 3;

    private final Handler el = new Handler(Looper.getMainLooper());
    private final Cevirmen cevirmen = new Cevirmen();
    private Dinleyici dinleyici;
    private Seslendirici ses;

    private Diller.Dil ben, karsi;
    private boolean hazir;
    private int sayfa;

    // Konuşma
    private int mod = BOS;
    /** Her başlatma ve durdurmada artar; eski işlerden gelen geç cevapları ayıklamak için. */
    private int tur;
    private int hataSayisi;
    private int bekleyenMod = BOS;
    private boolean bekleyenSurekli;
    /** Tercüman oturumu açık: karşı taraf sürekli dinlenir, cevaptan sonra dinlemeye dönülür. */
    private boolean surekli;
    private String canliOzgun = "", canliCeviri = "";
    private boolean canliCeviriyor;

    // Yazı
    private boolean yaziBenden = true;
    private final Runnable yaziIsi = this::yaziyiCevir;

    // Kamera
    private ProcessCameraProvider saglayici;
    private Camera kamera;
    private boolean donmus, fenerAcik;
    private int kameraTur;
    private String kameraMetin = "";
    private final TextRecognizer[] okuyucular = new TextRecognizer[5];

    private Spinner karsiDil, benDil;
    private TextView durum, canli, bosSohbet, cikti, kameraNot;
    private LinearLayout sayfaKonusma, sayfaYazi, sayfaKamera, akis;
    private ScrollView akisKaydir;
    private CheckBox sesli, ayrim;
    private Button dinle, cevap, yon, cek, sekmeKonusma, sekmeYazi, sekmeKamera;
    private EditText giris;
    private PreviewView onizleme;
    private ImageView donuk;
    private KameraKatmani katman;

    @Override
    protected void onCreate(Bundle kayit) {
        super.onCreate(kayit);
        setContentView(R.layout.ana);
        dinleyici = new Dinleyici(this, this);
        ses = new Seslendirici(this);

        karsiDil = findViewById(R.id.karsiDil);
        benDil = findViewById(R.id.benDil);
        durum = findViewById(R.id.durum);
        canli = findViewById(R.id.canli);
        bosSohbet = findViewById(R.id.bosSohbet);
        cikti = findViewById(R.id.cikti);
        kameraNot = findViewById(R.id.kameraNot);
        sayfaKonusma = findViewById(R.id.sayfaKonusma);
        sayfaYazi = findViewById(R.id.sayfaYazi);
        sayfaKamera = findViewById(R.id.sayfaKamera);
        akis = findViewById(R.id.akis);
        akisKaydir = findViewById(R.id.akisKaydir);
        sesli = findViewById(R.id.sesli);
        ayrim = findViewById(R.id.ayrim);
        dinle = findViewById(R.id.dinle);
        cevap = findViewById(R.id.cevap);
        yon = findViewById(R.id.yon);
        cek = findViewById(R.id.cek);
        sekmeKonusma = findViewById(R.id.sekmeKonusma);
        sekmeYazi = findViewById(R.id.sekmeYazi);
        sekmeKamera = findViewById(R.id.sekmeKamera);
        giris = findViewById(R.id.giris);
        onizleme = findViewById(R.id.onizleme);
        donuk = findViewById(R.id.donuk);
        katman = findViewById(R.id.katman);
        onizleme.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

        // Dil seçimi
        ArrayAdapter<String> a1 = new ArrayAdapter<>(this, R.layout.dil_satiri, Diller.adlar());
        a1.setDropDownViewResource(R.layout.dil_satiri_acik);
        karsiDil.setAdapter(a1);
        ArrayAdapter<String> a2 = new ArrayAdapter<>(this, R.layout.dil_satiri, Diller.adlar());
        a2.setDropDownViewResource(R.layout.dil_satiri_acik);
        benDil.setAdapter(a2);
        karsiDil.setSelection(Diller.sira(Ayarlar.karsi(this)));
        benDil.setSelection(Diller.sira(Ayarlar.ben(this)));
        AdapterView.OnItemSelectedListener secim = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int sira, long id) {
                dilSecildi();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        };
        karsiDil.setOnItemSelectedListener(secim);
        benDil.setOnItemSelectedListener(secim);
        findViewById(R.id.degistir).setOnClickListener(v -> {
            int k = karsiDil.getSelectedItemPosition();
            karsiDil.setSelection(benDil.getSelectedItemPosition());
            benDil.setSelection(k);
        });
        durum.setOnClickListener(v -> {
            if (!hazir && ben != null) dilleriKur();
        });

        // Konuşma
        sesli.setChecked(Ayarlar.sesli(this));
        ayrim.setChecked(Ayarlar.ayrim(this));
        sesli.setOnCheckedChangeListener((v, acik) -> Ayarlar.sesliYaz(this, acik));
        ayrim.setOnCheckedChangeListener((v, acik) -> Ayarlar.ayrimYaz(this, acik));
        dinle.setOnClickListener(v -> dinleTikla());
        cevap.setOnClickListener(v -> cevapTikla());
        findViewById(R.id.sil).setOnClickListener(v -> sohbetiSil());

        // Yazı
        yon.setOnClickListener(v -> {
            yaziBenden = !yaziBenden;
            etiketleriGuncelle();
            yaziyiCevir();
        });
        giris.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                el.removeCallbacks(yaziIsi);
                el.postDelayed(yaziIsi, 350);
            }
        });
        findViewById(R.id.yapistir).setOnClickListener(v -> yapistir());
        findViewById(R.id.temizle).setOnClickListener(v -> giris.setText(""));
        findViewById(R.id.oku).setOnClickListener(v -> {
            String c = cikti.getText().toString();
            if (c.trim().isEmpty()) return;
            oku(c, yaziBenden ? karsi : ben);
        });
        findViewById(R.id.kopyala).setOnClickListener(v -> {
            String c = cikti.getText().toString();
            if (!c.trim().isEmpty()) kopyala(c);
        });

        // Kamera
        cek.setOnClickListener(v -> cekTikla());
        findViewById(R.id.fener).setOnClickListener(v -> fenerTikla());
        findViewById(R.id.kameraOku).setOnClickListener(v -> {
            if (kameraMetin.isEmpty()) bildir("Önce ÇEVİR düğmesine bas.");
            else oku(kameraMetin, ben);
        });

        // Sekmeler
        sekmeKonusma.setOnClickListener(v -> sayfaGoster(0));
        sekmeYazi.setOnClickListener(v -> sayfaGoster(1));
        sekmeKamera.setOnClickListener(v -> sayfaGoster(2));

        dilSecildi();
    }

    @Override
    protected void onPause() {
        hepsiniDurdur();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        dinleyici.kapat();
        ses.kapat();
        cevirmen.kapat();
        for (TextRecognizer o : okuyucular) {
            try {
                if (o != null) o.close();
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- Diller

    private void dilSecildi() {
        Diller.Dil yb = Diller.HEPSI[Math.max(0, benDil.getSelectedItemPosition())];
        Diller.Dil yk = Diller.HEPSI[Math.max(0, karsiDil.getSelectedItemPosition())];
        if (ben != null && karsi != null && yb.kod.equals(ben.kod) && yk.kod.equals(karsi.kod)) return;
        ben = yb;
        karsi = yk;
        Ayarlar.dilYaz(this, ben.kod, karsi.kod);
        dilleriKur();
    }

    private void dilleriKur() {
        hepsiniDurdur();
        if (donmus) coz();
        hazir = false;
        durum.setText("Dil paketi hazırlanıyor… İlk seferde iner (dil başına yaklaşık 30 MB), internet açık olsun.");
        etiketleriGuncelle();
        final Diller.Dil b = ben, k = karsi;
        cevirmen.ayarla(b.kod, k.kod, tamam -> {
            if (b != ben || k != karsi) return;
            hazir = tamam;
            if (tamam) {
                durum.setText("Hazır: " + karsi.ad + " ⇄ " + ben.ad
                        + (ses.kulaklikVar() ? " · kulaklık bağlı" : ""));
                yaziyiCevir();
            } else {
                durum.setText("Dil paketi indirilemedi. İnterneti aç, sonra bu yazıya dokun.");
            }
        });
    }

    private void etiketleriGuncelle() {
        dugmeleriGuncelle();
        if (ben == null || karsi == null) return;
        yon.setText(yaziBenden ? ben.ad + "  →  " + karsi.ad + "   ⇄" : karsi.ad + "  →  " + ben.ad + "   ⇄");
        kameraNotuGuncelle();
    }

    private void dugmeleriGuncelle() {
        if (ben == null || karsi == null) return;
        if (surekli) {
            dinle.setText("■  DURDUR");
            dinle.setBackgroundResource(R.drawable.dugme_dur);
        } else {
            dinle.setText("DİNLE\n" + karsi.ad + " → " + ben.ad);
            dinle.setBackgroundResource(R.drawable.dugme);
        }
        if (mod == BEN) {
            cevap.setText("■  BİTİR");
            cevap.setBackgroundResource(R.drawable.dugme_dur);
        } else {
            cevap.setText("CEVAP VER\n" + ben.ad + " → " + karsi.ad);
            cevap.setBackgroundResource(R.drawable.dugme_ikincil);
        }
    }

    // ---------------------------------------------------------------- Konuşma

    private void dinleTikla() {
        if (surekli) {
            hepsiniDurdur();
            canli.setText("");
            return;
        }
        ses.sus();
        surekli = true;
        if (!basla(KARSI)) hepsiniDurdur();
        dugmeleriGuncelle();
    }

    private void cevapTikla() {
        if (mod == BEN) {
            dinleyici.bitir();
            return;
        }
        ses.sus();
        // Başlayamadıysa ve okuma yarıda kesildiyse takılı kalma; sıradaki işe dön.
        if (!basla(BEN) && mod == SOYLUYOR) dinlemeyeDon();
    }

    /** Verilen tarafı dinlemeye başlar. Başlayamadıysa false döner. */
    private boolean basla(int m) {
        if (!hazir) {
            bildir("Dil paketi henüz hazır değil.");
            return false;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            bekleyenMod = m;
            bekleyenSurekli = surekli;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, IST_MIK);
            return true;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            durum.setText("Bu telefonda ses tanıma bulunamadı. Google uygulamasını yükleyip güncelle.");
            return false;
        }
        Diller.Dil d = m == BEN ? ben : karsi;
        if (d.ses == null) {
            bildir(d.ad + " için ses tanıma yok. Yazı sekmesini kullan.");
            return false;
        }
        tur++;
        dinleyici.iptal();
        mod = m;
        canliOzgun = "";
        canliCeviri = "";
        canli.setText(m == BEN ? "Seni dinliyorum (" + ben.ad + ")…" : "Dinliyorum (" + karsi.ad + ")…");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        dugmeleriGuncelle();
        dinleyici.baslat(d.sesEtiketi());
        return true;
    }

    /** Oturum açıksa karşı tarafı dinlemeye döner, değilse boşa çıkar. */
    private void dinlemeyeDon() {
        if (surekli) {
            if (!basla(KARSI)) hepsiniDurdur();
        } else {
            mod = BOS;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            dugmeleriGuncelle();
        }
    }

    private void hepsiniDurdur() {
        tur++;
        surekli = false;
        mod = BOS;
        dinleyici.iptal();
        ses.sus();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        dugmeleriGuncelle();
    }

    @Override
    public void ara(String metin) {
        if (mod != KARSI && mod != BEN) return;
        hataSayisi = 0;
        canliOzgun = metin;
        canliGoster();
        canliCevir();
    }

    private void canliGoster() {
        canli.setText(canliCeviri.isEmpty() ? canliOzgun : canliCeviri + "\n" + canliOzgun);
    }

    /** Konuşma sürerken ara metni de çevirip gösterir; aynı anda yalnızca bir çeviri yürür. */
    private void canliCevir() {
        if (canliCeviriyor) return;
        final String m = canliOzgun;
        final int t = tur;
        canliCeviriyor = true;
        cevirmen.cevir(mod == BEN, m, new Cevirmen.Sonuc() {
            @Override
            public void tamam(String c) {
                canliCeviriyor = false;
                if (t != tur || (mod != KARSI && mod != BEN)) return;
                canliCeviri = c;
                canliGoster();
                if (!m.equals(canliOzgun)) canliCevir();
            }

            @Override
            public void hata(String neden) {
                canliCeviriyor = false;
            }
        });
    }

    @Override
    public void son(final String metin) {
        final int m = mod;
        if (m != KARSI && m != BEN) return;
        hataSayisi = 0;
        final int t = ++tur;
        mod = SOYLUYOR;
        dugmeleriGuncelle();
        cevirmen.cevir(m == BEN, metin, new Cevirmen.Sonuc() {
            @Override
            public void tamam(String c) {
                balonEkle(m == BEN, metin, c);
                if (t != tur) return;
                canli.setText("");
                seslendir(m, c);
            }

            @Override
            public void hata(String neden) {
                balonEkle(m == BEN, metin, "(çevrilemedi)");
                if (t != tur) return;
                canli.setText("");
                dinlemeyeDon();
            }
        });
    }

    /** Çeviriyi sesli okur, sonra sırayı ayarlar. m: çevirisi okunan taraf (KARSI ya da BEN). */
    private void seslendir(int m, String ceviri) {
        final int t = tur;
        Diller.Dil hedef = m == BEN ? karsi : ben;
        if (!sesli.isChecked()) {
            dinlemeyeDon();
            return;
        }
        boolean kulaklik = ses.kulaklikVar();
        Seslendirici.Bitti sonra = () -> {
            if (t == tur) dinlemeyeDon();
        };
        boolean oldu;
        if (m == BEN) {
            // Benim cevabım: kulaklık modunda telefonun hoparlöründen çıkar ki karşı taraf duysun.
            oldu = ses.soyle(ceviri, hedef.sesEtiketi(), kulaklik && ayrim.isChecked(), false, sonra);
        } else if (kulaklik && surekli) {
            // Çeviri kulaklığa gidiyor, mikrofon onu duymaz; dinlemeye ara vermeden devam et.
            oldu = ses.soyle(ceviri, hedef.sesEtiketi(), false, true, null);
            dinlemeyeDon();
        } else {
            // Hoparlörden okurken mikrofon kendi sesimizi duymasın diye okuma bitene kadar bekle.
            oldu = ses.soyle(ceviri, hedef.sesEtiketi(), false, false, sonra);
        }
        if (!oldu) durum.setText(hedef.ad + " için telefonda ses yok; çeviri yazı olarak gösteriliyor.");
    }

    @Override
    public void hata(int kod) {
        final int m = mod;
        if (m != KARSI && m != BEN) return;
        final int t = tur;
        boolean sessizlik = kod == SpeechRecognizer.ERROR_NO_MATCH || kod == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        if (sessizlik) {
            // Kimse konuşmadı: oturum açıksa dinlemeyi yeniden başlat.
            hataSayisi = 0;
            if (m == KARSI && surekli) {
                el.postDelayed(() -> {
                    if (t == tur) dinlemeyeDon();
                }, 120);
            } else {
                canli.setText("");
                dinlemeyeDon();
            }
            return;
        }
        hataSayisi++;
        // Ağ, sunucu, meşgul gibi geçici hatalarda birkaç kez yeniden dene.
        boolean gecici = kod == 1 || kod == 2 || kod == 4 || kod == 5 || kod == 8 || kod == 11;
        if (gecici && hataSayisi <= 3) {
            el.postDelayed(() -> {
                if (t != tur) return;
                if (!basla(m)) hepsiniDurdur();
            }, 600);
            return;
        }
        hepsiniDurdur();
        canli.setText("");
        durum.setText(hataYazisi(kod));
    }

    private static String hataYazisi(int kod) {
        switch (kod) {
            case 1:
            case 2:
                return "Ses tanıma internete ulaşamadı. İnterneti kontrol et.";
            case 3:
                return "Mikrofon kullanılamıyor. Başka bir uygulama mikrofonu tutuyor olabilir.";
            case 9:
                return "Mikrofon izni yok. Ayarlardan Tercüman için mikrofon iznini aç.";
            case 10:
                return "Ses tanıma çok sık çağrıldı. Biraz bekleyip tekrar dene.";
            case 12:
            case 13:
                return "Telefonun ses tanıması bu dili desteklemiyor ya da dil henüz indirilmemiş.";
            default:
                return "Ses tanıma hatası (" + kod + "). Tekrar dene.";
        }
    }

    private void balonEkle(boolean benden, String ozgun, final String ceviri) {
        bosSohbet.setVisibility(View.GONE);
        LinearLayout kutu = new LinearLayout(this);
        kutu.setOrientation(LinearLayout.VERTICAL);
        kutu.setBackgroundResource(benden ? R.drawable.balon_ben : R.drawable.kart);
        kutu.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView ust = new TextView(this);
        ust.setText(ceviri);
        ust.setTextColor(getColor(R.color.yazi));
        ust.setTextSize(19);
        TextView alt = new TextView(this);
        alt.setText(ozgun);
        alt.setTextColor(getColor(R.color.soluk));
        alt.setTextSize(14);
        alt.setPadding(0, dp(4), 0, 0);
        kutu.addView(ust);
        kutu.addView(alt);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = benden ? Gravity.END : Gravity.START;
        lp.topMargin = dp(8);
        if (benden) lp.leftMargin = dp(40);
        else lp.rightMargin = dp(40);
        akis.addView(kutu, lp);
        final Diller.Dil hedef = benden ? karsi : ben;
        kutu.setOnClickListener(v -> {
            if (mod == BOS) oku(ceviri, hedef);
            else bildir("Yeniden okutmak için önce dinlemeyi durdur.");
        });
        kutu.setOnLongClickListener(v -> {
            kopyala(ceviri);
            return true;
        });
        // Sohbet çok uzarsa en eski balonları at (ilk çocuk açıklama yazısıdır).
        while (akis.getChildCount() > 201) akis.removeViewAt(1);
        akisKaydir.post(() -> akisKaydir.fullScroll(View.FOCUS_DOWN));
    }

    private void sohbetiSil() {
        while (akis.getChildCount() > 1) akis.removeViewAt(1);
        bosSohbet.setVisibility(View.VISIBLE);
        canli.setText("");
    }

    // ---------------------------------------------------------------- Yazı

    private void yaziyiCevir() {
        final String m = giris.getText().toString();
        if (m.trim().isEmpty()) {
            cikti.setText("");
            return;
        }
        if (!hazir) return;
        final boolean yonu = yaziBenden;
        cevirmen.cevir(yonu, m, new Cevirmen.Sonuc() {
            @Override
            public void tamam(String c) {
                if (yonu == yaziBenden && m.equals(giris.getText().toString())) cikti.setText(c);
            }

            @Override
            public void hata(String neden) {
                cikti.setText("Çevrilemedi. Dil paketi inmemiş olabilir.");
            }
        });
    }

    private void yapistir() {
        try {
            ClipboardManager pano = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = pano.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) {
                bildir("Panoda yazı yok.");
                return;
            }
            CharSequence s = d.getItemAt(0).coerceToText(this);
            if (s == null) return;
            giris.setText(s);
            giris.setSelection(giris.length());
        } catch (Throwable t) {
            bildir("Pano okunamadı.");
        }
    }

    private void kopyala(String s) {
        try {
            ClipboardManager pano = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            pano.setPrimaryClip(ClipData.newPlainText("çeviri", s));
            bildir("Kopyalandı.");
        } catch (Throwable ignored) {
        }
    }

    private void oku(String metin, Diller.Dil dil) {
        if (!ses.soyle(metin, dil.sesEtiketi(), false, false, null)) {
            bildir("Sesli okunamadı. Telefonda " + dil.ad + " sesi yüklü olmayabilir.");
        }
    }

    // ---------------------------------------------------------------- Kamera

    private void kamerayaGir() {
        kameraNotuGuncelle();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, IST_KAM);
            return;
        }
        kamerayiAc();
    }

    private void kamerayiAc() {
        if (sayfa != 2) return;
        final ListenableFuture<ProcessCameraProvider> gelecek = ProcessCameraProvider.getInstance(this);
        gelecek.addListener(() -> {
            try {
                saglayici = gelecek.get();
                if (sayfa != 2) return;
                Preview p = new Preview.Builder().build();
                p.setSurfaceProvider(onizleme.getSurfaceProvider());
                saglayici.unbindAll();
                kamera = saglayici.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p);
            } catch (Throwable t) {
                kameraNot.setText("Kamera açılamadı. Uygulamayı kapatıp yeniden dene.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void kamerayiKapat() {
        if (donmus) coz();
        try {
            if (saglayici != null) saglayici.unbindAll();
        } catch (Throwable ignored) {
        }
        kamera = null;
        fenerAcik = false;
    }

    private void kameraNotuGuncelle() {
        if (ben == null || karsi == null || donmus) return;
        if (karsi.yazi == Diller.OKUNMAZ) {
            kameraNot.setText("Kamera " + karsi.ad + " yazısını okuyamıyor (Arap, Kiril, Yunan gibi alfabeler "
                    + "desteklenmiyor). Bu dilde Konuşma ve Yazı sekmelerini kullan.");
        } else {
            kameraNot.setText(karsi.ad + " yazıyı kadraja al, ÇEVİR'e bas. Çeviri " + ben.ad
                    + " olarak resmin üstüne yazılır.");
        }
    }

    private void fenerTikla() {
        if (kamera == null) return;
        try {
            fenerAcik = !fenerAcik;
            kamera.getCameraControl().enableTorch(fenerAcik);
        } catch (Throwable ignored) {
        }
    }

    private void cekTikla() {
        if (donmus) {
            coz();
            return;
        }
        if (!hazir) {
            bildir("Dil paketi henüz hazır değil.");
            return;
        }
        if (karsi.yazi == Diller.OKUNMAZ) {
            bildir("Kamera " + karsi.ad + " yazısını okuyamıyor.");
            return;
        }
        final Bitmap b = onizleme.getBitmap();
        if (b == null) {
            bildir("Kamera görüntüsü henüz hazır değil.");
            return;
        }
        donmus = true;
        final int t = ++kameraTur;
        kameraMetin = "";
        donuk.setImageBitmap(b);
        donuk.setVisibility(View.VISIBLE);
        cek.setText(R.string.yeniden);
        kameraNot.setText("Yazı okunuyor…");
        // Kutuların yeri resmin piksellerine göre gelir; ekrandaki boyuta oranla.
        final float ox = b.getWidth() > 0 ? (float) onizleme.getWidth() / b.getWidth() : 1f;
        final float oy = b.getHeight() > 0 ? (float) onizleme.getHeight() / b.getHeight() : 1f;
        try {
            okuyucu(karsi.yazi).process(InputImage.fromBitmap(b, 0))
                    .addOnSuccessListener(okunan -> {
                        if (t == kameraTur) bloklariCevir(okunan, t, ox, oy);
                    })
                    .addOnFailureListener(e -> {
                        if (t == kameraTur) kameraNot.setText("Yazı okunamadı. YENİDEN'e basıp tekrar dene.");
                    });
        } catch (Throwable e) {
            kameraNot.setText("Yazı okunamadı. YENİDEN'e basıp tekrar dene.");
        }
    }

    private void bloklariCevir(Text okunan, final int t, final float ox, final float oy) {
        final List<Text.TextBlock> bloklar = new ArrayList<>();
        for (Text.TextBlock blok : okunan.getTextBlocks()) {
            if (blok.getBoundingBox() != null && !blok.getText().trim().isEmpty()) bloklar.add(blok);
        }
        if (bloklar.isEmpty()) {
            kameraNot.setText("Yazı bulunamadı. Yazıya yaklaş, ışığı artır, YENİDEN'e basıp tekrar dene.");
            return;
        }
        kameraNot.setText("Çevriliyor…");
        final String[] ceviriler = new String[bloklar.size()];
        final int[] kalan = {bloklar.size()};
        // Çince ve Japoncada satır sonu kelime arası değildir; boşluk koymadan birleştir.
        final boolean bitisik = karsi.yazi == Diller.CINCE || karsi.yazi == Diller.JAPONCA;
        for (int i = 0; i < bloklar.size(); i++) {
            final int sira = i;
            String ham = bloklar.get(i).getText().replace("\n", bitisik ? "" : " ");
            cevirmen.cevir(false, ham, new Cevirmen.Sonuc() {
                @Override
                public void tamam(String c) {
                    ceviriler[sira] = c;
                    biri();
                }

                @Override
                public void hata(String neden) {
                    biri();
                }

                private void biri() {
                    if (--kalan[0] > 0 || t != kameraTur) return;
                    List<KameraKatmani.Kutu> kutular = new ArrayList<>();
                    StringBuilder hepsi = new StringBuilder();
                    for (int j = 0; j < ceviriler.length; j++) {
                        if (ceviriler[j] == null || ceviriler[j].isEmpty()) continue;
                        Rect r = bloklar.get(j).getBoundingBox();
                        if (r == null) continue;
                        Rect yer = new Rect(Math.round(r.left * ox), Math.round(r.top * oy),
                                Math.round(r.right * ox), Math.round(r.bottom * oy));
                        kutular.add(new KameraKatmani.Kutu(yer, ceviriler[j]));
                        if (hepsi.length() > 0) hepsi.append("\n");
                        hepsi.append(ceviriler[j]);
                    }
                    kameraMetin = hepsi.toString();
                    katman.goster(kutular);
                    kameraNot.setText(kutular.isEmpty()
                            ? "Çevrilemedi. YENİDEN'e basıp tekrar dene."
                            : "Çeviri resmin üstünde. Yeni çekim için YENİDEN'e bas.");
                }
            });
        }
    }

    /** Dondurulmuş görüntüyü bırakıp canlı kameraya döner. */
    private void coz() {
        donmus = false;
        kameraTur++;
        kameraMetin = "";
        donuk.setVisibility(View.GONE);
        donuk.setImageDrawable(null);
        katman.temizle();
        cek.setText(R.string.cevir);
        kameraNotuGuncelle();
    }

    private TextRecognizer okuyucu(int yazi) {
        if (yazi < 0 || yazi >= okuyucular.length) yazi = Diller.LATIN;
        if (okuyucular[yazi] == null) {
            switch (yazi) {
                case Diller.CINCE:
                    okuyucular[yazi] = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                    break;
                case Diller.JAPONCA:
                    okuyucular[yazi] = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
                    break;
                case Diller.KORECE:
                    okuyucular[yazi] = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
                    break;
                case Diller.DEVANAGARI:
                    okuyucular[yazi] = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
                    break;
                default:
                    okuyucular[yazi] = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            }
        }
        return okuyucular[yazi];
    }

    // ---------------------------------------------------------------- Sekmeler ve izinler

    private void sayfaGoster(int yeni) {
        if (yeni == sayfa) return;
        if (sayfa == 0) {
            hepsiniDurdur();
            canli.setText("");
        }
        if (sayfa == 1) {
            try {
                InputMethodManager klavye = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                klavye.hideSoftInputFromWindow(giris.getWindowToken(), 0);
            } catch (Throwable ignored) {
            }
        }
        if (sayfa == 2) kamerayiKapat();
        ses.sus();
        sayfa = yeni;
        sayfaKonusma.setVisibility(yeni == 0 ? View.VISIBLE : View.GONE);
        sayfaYazi.setVisibility(yeni == 1 ? View.VISIBLE : View.GONE);
        sayfaKamera.setVisibility(yeni == 2 ? View.VISIBLE : View.GONE);
        sekmeKonusma.setBackgroundResource(yeni == 0 ? R.drawable.sekme_secili : R.drawable.sekme);
        sekmeYazi.setBackgroundResource(yeni == 1 ? R.drawable.sekme_secili : R.drawable.sekme);
        sekmeKamera.setBackgroundResource(yeni == 2 ? R.drawable.sekme_secili : R.drawable.sekme);
        if (yeni == 2) kamerayaGir();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRequestPermissionsResult(int istek, String[] izinler, int[] sonuc) {
        super.onRequestPermissionsResult(istek, izinler, sonuc);
        boolean verildi = sonuc.length > 0 && sonuc[0] == PackageManager.PERMISSION_GRANTED;
        if (istek == IST_MIK) {
            int m = bekleyenMod;
            bekleyenMod = BOS;
            if (verildi && m != BOS) {
                surekli = bekleyenSurekli;
                if (!basla(m)) hepsiniDurdur();
                dugmeleriGuncelle();
            } else {
                hepsiniDurdur();
                durum.setText("Mikrofon izni verilmedi. Konuşmayı çevirebilmem için bu izin şart.");
            }
        } else if (istek == IST_KAM) {
            if (verildi) kamerayiAc();
            else kameraNot.setText("Kamera izni verilmedi. Yazıyı okuyabilmem için bu izin şart.");
        }
    }

    private void bildir(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
