package app.anindaceviri;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Çeviri motoru: "ekran değişti mi, hangi yazı yeni, hangi kutu eskidi" kararlarını verir.
 * Ekran yakalama, yazı okuma, çeviri ve çizim işlerini alt sınıf (CeviriServisi) sağlar;
 * böylece bu mantık telefonsuz da denenebilir.
 *
 * Zor kısım şu: çizdiğimiz kutular da ekran görüntüsüne girer. Bu yüzden
 *  - kutular %80 opak çizilir; hem dokunmalar oyuna geçer (Android 12+ kuralı) hem de altındaki
 *    yazı değişince kutunun kapladığı alanda hafif bir fark görülür ve kutu "eskidi" diye silinir,
 *  - okunan yazı mevcut bir kutunun içindeyse bizim çevirimizdir, yeniden çevrilmez,
 *  - ekran hâlâ değişiyorsa (harf harf yazılan diyalog gibi) yazı durulana kadar beklenir.
 *
 * Bütün yöntemler tek bir iş parçacığından (işçi) çağrılır.
 */
abstract class Motor {
    private static final String TAG = "AnindaCeviri";
    /** Ekranı yoklama aralığı (ms). */
    static final long TIK = 400;
    /** Kutuları değiştirdikten sonra, değişikliğin ekran görüntüsüne yansıması için beklenen süre (ms). */
    static final long KATMAN_BEKLE = 260;
    /** Değişim ölçümü için ekran görüntüsü bu oranda küçültülür. */
    static final int KUCULT = 6;
    static final int AZAMI_ETIKET = 80;

    // --- platformun doldurduğu değerler ---
    volatile int ekranW, ekranH;
    int yakW, yakH;
    float yogunluk = 1f;
    volatile boolean aktif, bitti;
    /** En son kopyalanan ekran görüntüsü. */
    Bitmap ham;
    /** Baloncuğun ekrandaki yeri; kendi üstünde kilitlenerek okunur/yazılır. */
    final Rect balonAlan = new Rect();

    // --- motorun kendi durumu ---
    final ArrayList<Etiket> etiketler = new ArrayList<>();
    private final ArrayList<Rect> kirli = new ArrayList<>();
    /** Listeden çıkarılmış ama ekrandan silindiği henüz görülmemiş kutular. */
    private final ArrayList<Etiket> kalkan = new ArrayList<>();
    private final HashMap<String, String> onbellek = new HashMap<>();
    private final HashSet<String> sonCiktilar = new HashSet<>();
    private final HashMap<String, Integer> tekrar = new HashMap<>();
    private final HashMap<String, Integer> red = new HashMap<>();
    private final HashSet<String> cokOynak = new HashSet<>();
    /** Bir önceki okumada görülen yazılar ve yerleri. */
    private HashMap<String, Rect> gorulen = new HashMap<>();
    private final int[] sayim = new int[2];
    private int[] sonKucuk;
    private int[] refKucuk;
    private int kucukW, kucukH;
    private float kx, ky;
    private long sonKatmanDegisim, sonOku;
    private boolean bekleyen = true, acil = true;
    private int bosGecis, sakinDegil;
    /** Kaç kez yazı okundu (deneme ve tanılama için). */
    int okumaSayisi;
    /** Şimdiye dek görülen yeni ekran görüntüsü sayısı. */
    private long kareSayaci;

    // --- platformun sağladığı işler ---

    /** Ekran boyutunu ölçer (ekranW/ekranH'yi günceller); değiştiyse true. */
    abstract boolean olc();

    /** Yakalama yüzeyini güncel ekran boyutuna göre kurar (yakW/yakH'yi günceller). */
    abstract void yakalamaKur();

    /** En güncel ekran görüntüsünü "ham"a koyar. 0: görüntü yok, 1: öncekiyle aynı, 2: yeni. */
    abstract int kareAl();

    /** Görüntüdeki yazı öbeklerini okur. */
    abstract List<Blok> oku(Bitmap b) throws Exception;

    /** Bir yazıyı Türkçeye çevirir. */
    abstract String cevir(String metin) throws Exception;

    /** Güncel kutu listesini ekrana çizer. */
    abstract void goster(ArrayList<Etiket> kopya);

    /** Çalışıyor göstergesi. */
    abstract void mesgul(boolean m);

    /** Telefon döndükten sonra platformun yapacağı işler. */
    abstract void ekranDondu();

    /** Kutunun yerleşimini hesaplar (denemelerde değiştirilebilir). */
    Etiket etiketKur(String kaynak, String ceviri, Rect alan, boolean tekSatir, float satirY,
                     int arka, int yazi) {
        return Etiket.uret(kaynak, ceviri, alan, tekSatir, satirY, arka, yazi, yogunluk, ekranW, ekranH);
    }

    long simdi() {
        return SystemClock.uptimeMillis();
    }

    private static final class Aday {
        final String metin;
        final Rect alan;
        final Rect kutu;
        final boolean tekSatir;
        final float satirY;
        final long buyukluk;

        Aday(String metin, Rect alan, Rect kutu, boolean tekSatir, float satirY) {
            this.metin = metin;
            this.alan = alan;
            this.kutu = kutu;
            this.tekSatir = tekSatir;
            this.satirY = satirY;
            this.buyukluk = (long) alan.width() * alan.height();
        }
    }

    /** Bir yoklama adımı. Dönen değer: bir sonraki adıma kadar beklenecek süre (ms). */
    long adim() {
        if (olc()) {
            // Telefon döndü: yakalamayı yeni boyuta göre kur, her şeyi sıfırla.
            yakalamaKur();
            sifirla();
            ekranDondu();
            return 350;
        }
        if (!aktif) return 300;
        long simdi = simdi();
        if (simdi - sonKatmanDegisim < KATMAN_BEKLE) return 90;

        int k = kareAl();
        if (k == 0) return 150;
        if (k == 2) kareSayaci++;
        if (k == 1 && !bekleyen) return TIK;

        Bitmap b = ham;
        int[] kucuk;
        boolean sakin;
        if (k == 1 && sonKucuk != null) {
            // Son bakıştan beri ekran hiç değişmedi.
            kucuk = sonKucuk;
            sakin = true;
        } else {
            kucuk = kucukUret(b);
            sakin = sakinMi(kucuk);
            sonKucuk = kucuk;
            kirli.clear();
        }

        // Kaldırdığımız kutular ekran görüntüsünden de gitti mi? (Çizim gecikebilir; gitmeden
        // yazı okursak kendi çevirimizi yabancı yazı sanarız.)
        boolean hala = false;
        for (int i = kalkan.size() - 1; i >= 0; i--) {
            Etiket e = kalkan.get(i);
            if (simdi - e.kalkti > 1500 || Fark.farkli(e.kalkis, yamaAl(kucuk, e.cizim), 20, 0.04f)) {
                kalkan.remove(i);
            } else {
                hala = true;
            }
        }

        // 1) Kutuların altı değişti mi? Değiştiyse o kutu eskimiştir.
        boolean silindi = false;
        for (int i = etiketler.size() - 1; i >= 0; i--) {
            Etiket e = etiketler.get(i);
            int[] y = yamaAl(kucuk, e.cizim);
            if (e.ref == null) {
                // Karşılaştırma örneği, kutu ekran görüntüsünde belirdikten sonra alınır
                // (alan öncekinden farklılaşmışsa kutu gelmiştir; 2 saniyede gelmediyse yine de al).
                if (kareSayaci > e.kare && (e.once == null || simdi - e.gosterildi > 2000
                        || Fark.farkli(e.once, y, 20, 0.04f))) {
                    e.ref = y;
                    e.once = null;
                }
                continue;
            }
            if (e.kat >= 2 && simdi - e.gosterildi < 5000) continue;
            // Kutu %80 opak olduğundan altındaki değişim beşte bir şiddetle görünür: yazı değişimi
            // belirgin, arkadaki sahne hareketi ise silik bir fark bırakır.
            boolean eskidi = e.kat == 0 ? Fark.farkli(e.ref, y, 8, 0.04f)
                    : Fark.farkli(e.ref, y, 22, e.kat == 1 ? 0.08f : 0.12f);
            if (eskidi) {
                // Kısa sürede yine eskidiyse yazı doğrudan hareketli görüntünün üstündedir.
                if (e.kat >= 1 && simdi - e.gosterildi < 3000) {
                    if (cokOynak.size() > 600) cokOynak.clear();
                    cokOynak.add(e.anahtar);
                }
                kaldir(e, kucuk, simdi);
                etiketler.remove(i);
                silindi = true;
            }
        }

        // 2) Kutuların dışında ekran değişti mi?
        int[] ref = refKucuk;
        boolean degisti = ref == null || ref.length != kucuk.length;
        if (!degisti) {
            Fark.say(ref, kucuk, maskeUret(null), 20, sayim);
            degisti = sayim[0] >= 4;
        }
        if (degisti) bekleyen = true;
        if (silindi) {
            katmanGonder();
            bekleyen = true;
            acil = true;
            bosGecis = 0;
            return 90;
        }
        if (!bekleyen) return TIK;
        if (hala) return 90;

        // Ekran hâlâ hareketliyse (yazı harf harf geliyor olabilir) biraz bekle.
        if (!sakin && sakinDegil < 2) {
            sakinDegil++;
            return 140;
        }
        long aralik = 650 + Math.min(bosGecis, 5) * 220L;
        if (!acil && simdi - sonOku < aralik) return 120;

        sakinDegil = 0;
        bekleyen = false;
        acil = false;
        sonOku = simdi;
        refKucuk = kucuk;
        mesgul(true);
        try {
            okuVeCevir(b, kucuk, sakin);
        } finally {
            mesgul(false);
        }
        return TIK;
    }

    /** Ekrandaki yazıyı okur, yeni olanları çevirir ve kutu olarak ekler. */
    private void okuVeCevir(Bitmap b, int[] okunan, boolean sakin) {
        List<Blok> bloklar;
        try {
            bloklar = oku(b);
        } catch (Exception e) {
            Log.w(TAG, "yazı okuma", e);
            return;
        }
        okumaSayisi++;
        float sx = (float) ekranW / yakW, sy = (float) ekranH / yakH;
        Rect bal = new Rect();
        synchronized (balonAlan) {
            bal.set(balonAlan);
        }
        ArrayList<Etiket> onceki = new ArrayList<>(etiketler);
        HashSet<Etiket> silinecek = new HashSet<>();
        ArrayList<Aday> adaylar = new ArrayList<>();

        for (Blok blok : bloklar) {
            Rect kutu = blok.kutu;
            if (kutu == null || kutu.width() < 6 || kutu.height() < 6) continue;
            if ("tr".equals(blok.dil)) continue;
            // Oyun grafiklerinden uydurulan "yazıları" ele (0 = güven bilgisi yok demek).
            if (blok.guven > 0f && blok.guven < 0.35f) continue;
            String metin = Metin.duzelt(blok.metin);
            if (!Metin.cevrilmeli(metin)) continue;
            // Kendi çevirimizi yeniden okumuşsak geç.
            if (sonCiktilar.contains(Metin.yalin(metin))) continue;

            Rect alan = new Rect(Math.round(kutu.left * sx), Math.round(kutu.top * sy),
                    Math.round(kutu.right * sx), Math.round(kutu.bottom * sy));
            long blokAlani = Math.max(1L, (long) alan.width() * alan.height());
            if (kesisim(alan, bal) * 2 >= blokAlani) continue;
            float satirY = blok.satirYuk > 0 ? blok.satirYuk * sy : alan.height();

            Etiket enIyi = null;
            long enCok = 0;
            for (Etiket e : onceki) {
                long ks = kesisim(alan, e.cizim);
                if (ks > enCok) {
                    enCok = ks;
                    enIyi = e;
                }
            }
            if (enIyi != null && enCok * 100 >= blokAlani * 15) {
                // Yazı mevcut bir kutunun üstünde: büyük olasılıkla kendi çevirimiz.
                // Ama kutunun dışına belirgin biçimde taşıyorsa altına yeni yazı gelmiştir:
                // kutuyu kaldır, bir sonraki turda temiz hâliyle yeniden oku.
                Rect z = enIyi.cizim;
                int disari = Math.max(Math.max(z.left - alan.left, alan.right - z.right),
                        Math.max(z.top - alan.top, alan.bottom - z.bottom));
                if (enIyi.eklenme < 3 && disari > 0.8f * satirY) silinecek.add(enIyi);
                continue;
            }
            adaylar.add(new Aday(metin, alan, kutu, blok.satir == 1, satirY));
        }

        boolean degisti = false;
        if (!silinecek.isEmpty()) {
            long an = simdi();
            for (Etiket e : silinecek) kaldir(e, okunan, an);
            etiketler.removeAll(silinecek);
            degisti = true;
            bekleyen = true;
            acil = true;
        }

        // Büyük yazılar (diyalog, açıklama) önce çevrilsin.
        Collections.sort(adaylar, (a1, a2) -> Long.compare(a2.buyukluk, a1.buyukluk));
        HashMap<String, Rect> buTur = new HashMap<>();
        ArrayList<Etiket> yeniler = new ArrayList<>();
        boolean onayBekleyen = false;
        int yeniCeviri = 0, sonGonderim = 0;
        for (Aday a : adaylar) {
            if (!aktif || bitti) break;
            buTur.put(a.metin, a.alan);
            String ceviri = onbellek.get(a.metin);
            if (ceviri == null) {
                // Ekran hareketliyken ilk kez görülen yazı: iki turda aynı okunursa çevir.
                if (!sakin && !gorulen.containsKey(a.metin)) {
                    onayBekleyen = true;
                    continue;
                }
                try {
                    ceviri = cevir(a.metin);
                } catch (Exception e) {
                    Log.w(TAG, "çeviri", e);
                    continue;
                }
                if (ceviri == null) continue;
                ceviri = Metin.duzelt(ceviri);
                if (onbellek.size() > 4000) onbellek.clear();
                onbellek.put(a.metin, ceviri);
                yeniCeviri++;
            }
            // Çeviri özgünüyle aynıysa (özel ad, kısaltma) kutu çizmeye gerek yok.
            if (ceviri.isEmpty() || Metin.yalin(ceviri).equals(Metin.yalin(a.metin))) continue;
            if (etiketler.size() + yeniler.size() >= AZAMI_ETIKET) break;
            Etiket yeni = etiketUret(a, ceviri, b);
            Rect eskiYer = gorulen.get(a.metin);
            yeni.yerinde = eskiYer != null && Math.abs(eskiYer.left - a.alan.left) <= 10
                    && Math.abs(eskiYer.top - a.alan.top) <= 10;
            yeniler.add(yeni);
            // İlk seferde çeviri uzun sürebilir: hazır olanları beklemeden göster.
            if (yeniCeviri - sonGonderim >= 3) {
                sonGonderim = yeniCeviri;
                if (dogrulaEkle(yeniler, okunan)) degisti = true;
                else if (degisti) katmanGonder();
            }
        }
        gorulen = buTur;
        if (onayBekleyen) bekleyen = true;
        if (dogrulaEkle(yeniler, okunan)) degisti = true;
        else if (degisti) katmanGonder();
        bosGecis = degisti ? 0 : bosGecis + 1;
    }

    /**
     * Yeni kutuları ekrana koymadan hemen önce son bir kontrol: yazı okunduğundan beri (okuma ve
     * çeviri zaman alır) kutunun geleceği yer değiştiyse yazı kaymış ya da gitmiştir; kutu eklenmez,
     * ekran durulunca yeniden okunur. Eklenen kutu olduysa çizime yollar ve true döner.
     */
    private boolean dogrulaEkle(ArrayList<Etiket> yeniler, int[] okunan) {
        if (yeniler.isEmpty()) return false;
        int[] guncel = null;
        if (kareAl() == 2) {
            kareSayaci++;
            int[] g = kucukUret(ham);
            if (g.length == okunan.length) {
                guncel = g;
                sonKucuk = g;
            }
        }
        boolean eklendi = false;
        for (Etiket e : yeniler) {
            boolean oynak = false;
            if (guncel != null && Fark.farkli(yamaAl(okunan, e.cizim), yamaAl(guncel, e.cizim), 20, 0.04f)) {
                // Yazı kaymış olabilir: ekleme. Ama aynı yazı aynı yerde ikinci kez okunuyorsa yazı
                // duruyor, oynayan arkasındaki sahnedir (oyun görüntüsünün üstündeki yazı): ekle.
                Integer r = red.get(e.anahtar);
                int kac = r == null ? 1 : r + 1;
                if (red.size() > 600) red.clear();
                red.put(e.anahtar, kac);
                if (kac < 2 && !e.yerinde) {
                    bekleyen = true;
                    continue;
                }
                oynak = true;
            }
            // Aynı yerde aynı yazı üçüncü kez ekleniyorsa da altındaki zemin oynaktır.
            Integer once = tekrar.get(e.anahtar);
            int sayi = once == null ? 1 : once + 1;
            if (tekrar.size() > 600) tekrar.clear();
            tekrar.put(e.anahtar, sayi);
            e.eklenme = sayi;
            e.kat = cokOynak.contains(e.anahtar) ? 2 : (oynak || sayi >= 3) ? 1 : 0;
            e.gosterildi = simdi();
            e.kare = kareSayaci;
            e.once = yamaAl(guncel != null ? guncel : okunan, e.cizim);
            etiketler.add(e);
            kirli.add(e.cizim);
            if (sonCiktilar.size() > 800) sonCiktilar.clear();
            sonCiktilar.add(Metin.yalin(e.ceviri));
            eklendi = true;
        }
        yeniler.clear();
        if (eklendi) katmanGonder();
        return eklendi;
    }

    private Etiket etiketUret(Aday a, String ceviri, Bitmap b) {
        int arka = arkaRenk(b, a.kutu);
        int parlak = (Color.red(arka) * 77 + Color.green(arka) * 150 + Color.blue(arka) * 29) >> 8;
        int yazi;
        if (parlak < 140) {
            yazi = 0xFFFFFFFF;
            arka = karistir(arka, 0xFF000000, 0.25f);
        } else {
            yazi = 0xFF101418;
            arka = karistir(arka, 0xFFFFFFFF, 0.25f);
        }
        Etiket e = etiketKur(a.metin, ceviri, a.alan, a.tekSatir, a.satirY, arka, yazi);
        e.anahtar = a.metin + "@" + (a.alan.left / 40) + "," + (a.alan.top / 40);
        return e;
    }

    /** Yazı kutusunun çevresindeki renklerin ortalaması: kutunun zemini oyunun zeminine benzesin. */
    private int arkaRenk(Bitmap b, Rect k) {
        int mw = Math.min(yakW, b.getWidth()) - 1, mh = Math.min(yakH, b.getHeight()) - 1;
        int l = sinirla(k.left - 3, mw), r = sinirla(k.right + 2, mw);
        int t = sinirla(k.top - 3, mh), a = sinirla(k.bottom + 2, mh);
        long rs = 0, gs = 0, bs = 0;
        int n = 0;
        final int adet = 14;
        for (int i = 0; i <= adet; i++) {
            int x = l + (r - l) * i / adet, y = t + (a - t) * i / adet;
            int[] ps = {b.getPixel(x, t), b.getPixel(x, a), b.getPixel(l, y), b.getPixel(r, y)};
            for (int p : ps) {
                rs += (p >> 16) & 0xFF;
                gs += (p >> 8) & 0xFF;
                bs += p & 0xFF;
                n++;
            }
        }
        return Color.rgb((int) (rs / n), (int) (gs / n), (int) (bs / n));
    }

    private static int sinirla(int v, int ust) {
        return v < 0 ? 0 : Math.min(v, Math.max(0, ust));
    }

    private static int karistir(int renk, int hedef, float oran) {
        int r = Math.round(Color.red(renk) + (Color.red(hedef) - Color.red(renk)) * oran);
        int g = Math.round(Color.green(renk) + (Color.green(hedef) - Color.green(renk)) * oran);
        int b = Math.round(Color.blue(renk) + (Color.blue(hedef) - Color.blue(renk)) * oran);
        return Color.rgb(r, g, b);
    }

    private static long kesisim(Rect a, Rect b) {
        int w = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        int h = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        return (w <= 0 || h <= 0) ? 0 : (long) w * h;
    }

    // ------------------------------------------------------------------ küçük görüntü yardımcıları

    /** Ekran görüntüsünün küçültülmüş, gri tonlu kopyası (değişim ölçümü için). */
    private int[] kucukUret(Bitmap b) {
        int w = Math.max(1, b.getWidth() / KUCULT), h = Math.max(1, b.getHeight() / KUCULT);
        Bitmap kb = Bitmap.createScaledBitmap(b, w, h, false);
        int[] p = new int[w * h];
        kb.getPixels(p, 0, w, 0, 0, w, h);
        if (kb != b) kb.recycle();
        Fark.griyeCevir(p);
        kucukW = w;
        kucukH = h;
        // Ekran pikseli -> küçük görüntü pikseli çarpanları.
        kx = ((float) yakW / ekranW) * ((float) w / b.getWidth());
        ky = ((float) yakH / ekranH) * ((float) h / b.getHeight());
        return p;
    }

    private int[] yamaAl(int[] kucuk, Rect a) {
        return Fark.yama(kucuk, kucukW, kucukH,
                (int) Math.floor(a.left * kx), (int) Math.floor(a.top * ky),
                (int) Math.ceil(a.right * kx), (int) Math.ceil(a.bottom * ky));
    }

    private void maskele(boolean[] m, Rect a) {
        Fark.maskele(m, kucukW, kucukH,
                (int) Math.floor(a.left * kx) - 1, (int) Math.floor(a.top * ky) - 1,
                (int) Math.ceil(a.right * kx) + 1, (int) Math.ceil(a.bottom * ky) + 1);
    }

    /** Kendi çizdiklerimizi (kutular, baloncuk, az önce değişen alanlar) ölçüm dışında tutan maske. */
    private boolean[] maskeUret(List<Rect> ek) {
        boolean[] m = new boolean[kucukW * kucukH];
        for (int i = 0; i < etiketler.size(); i++) maskele(m, etiketler.get(i).cizim);
        if (ek != null) {
            for (int i = 0; i < ek.size(); i++) maskele(m, ek.get(i));
        }
        Rect bal = new Rect();
        synchronized (balonAlan) {
            bal.set(balonAlan);
        }
        if (!bal.isEmpty()) maskele(m, bal);
        return m;
    }

    /** Son bakıştan beri ekran (bizim çizdiklerimiz dışında) aynı mı kaldı? */
    private boolean sakinMi(int[] kucuk) {
        int[] o = sonKucuk;
        // İlk bakış: karşılaştıracak önceki görüntü yok, beklemeden oku.
        if (o == null || o.length != kucuk.length) return true;
        Fark.say(o, kucuk, maskeUret(kirli), 20, sayim);
        return sayim[0] < 4;
    }

    /** Bir kutuyu "kaldırılıyor" diye işaretler; ekrandan gittiği sonraki görüntülerde doğrulanır. */
    private void kaldir(Etiket e, int[] kucuk, long an) {
        e.kalkis = yamaAl(kucuk, e.cizim);
        e.kalkti = an;
        kalkan.add(e);
        kirli.add(e.cizim);
    }

    /** Güncel kutu listesini çizilmek üzere platforma yollar. */
    private void katmanGonder() {
        sonKatmanDegisim = simdi();
        goster(new ArrayList<>(etiketler));
    }

    /** Tüm kutuları ve hafızayı sıfırlar (çeviri önbelleği kalır). Aç/kapat ve ekran dönmesinde çağrılır. */
    void sifirla() {
        long an = simdi();
        for (int i = 0; i < etiketler.size(); i++) {
            Etiket e = etiketler.get(i);
            if (sonKucuk != null) kaldir(e, sonKucuk, an);
            else kirli.add(e.cizim);
        }
        etiketler.clear();
        tekrar.clear();
        red.clear();
        cokOynak.clear();
        gorulen.clear();
        ham = null;
        sonKucuk = null;
        refKucuk = null;
        bekleyen = true;
        acil = true;
        bosGecis = 0;
        sakinDegil = 0;
        katmanGonder();
    }
}
