package app.anindaceviri;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Motoru sahte bir ekranla çalıştıran deneme düzeneği (uygulamaya girmez). */
public class Sim {
    static final int W = 540, H = 960;

    static class Yazi {
        String ad, metin;
        Rect r;
        boolean var = true;
        boolean oynakZemin;      // altında panel yok, hareketli zemin görünüyor
        boolean yariSaydam;      // panel %70 opak, arkadaki sahne hafifçe görünüyor
        int gorunen = -1;        // harf harf yazılan yazıda görünen harf sayısı (-1: hepsi)
        boolean tekSatir = true;
        int kontrast = 200;
        Yazi(String ad, String metin, Rect r) { this.ad = ad; this.metin = metin; this.r = r; }
        String gorunenMetin() { return gorunen < 0 ? metin : metin.substring(0, Math.min(gorunen, metin.length())); }
        Rect gorunenAlan() {
            if (gorunen < 0) return new Rect(r);
            int w = (int) ((long) r.width() * Math.min(gorunen, metin.length()) / metin.length());
            return new Rect(r.left, r.top, r.left + w, r.bottom);
        }
    }

    interface Olay { void uygula(Dunya d); }

    static class Dunya extends Motor {
        long t;
        final List<Yazi> yazilar = new ArrayList<>();
        Rect hareketli;                 // içi her karede değişen bölge
        int etiketBuyume;               // kutuyu aşağı doğru bu kadar büyüt (komşuya taşma denemesi)
        long dunyaSurum, gosterimSurum;
        ArrayList<Etiket> ekrandaki = new ArrayList<>();
        ArrayList<Etiket> bekleyenListe;
        long bekleyenAn = -1;
        String sonKareAnahtar = "";
        List<Blok> sonBloklar = new ArrayList<>();
        int gosterSayisi, ceviriSayisi;
        final Map<String, Integer> eklemeler = new LinkedHashMap<>();
        final java.util.Set<Etiket> sayilan = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final List<String> gunluk = new ArrayList<>();
        final java.util.TreeMap<Long, Olay> olaylar = new java.util.TreeMap<>();
        boolean ayrinti;
        long gecikme = 30;             // kutuların ekrana çizilme gecikmesi (ms)

        Dunya() { ekranW = W; ekranH = H; yakW = W; yakH = H; yogunluk = 1f; }

        void zaman() {
            while (!olaylar.isEmpty() && olaylar.firstKey() <= t) { olaylar.pollFirstEntry().getValue().uygula(this); dunyaSurum++; }
            if (bekleyenAn >= 0 && t >= bekleyenAn) { ekrandaki = bekleyenListe; bekleyenAn = -1; gosterimSurum++; }
        }

        @Override long simdi() { return t; }
        @Override boolean olc() { return false; }
        @Override void yakalamaKur() {}
        @Override void mesgul(boolean m) {}
        @Override void ekranDondu() {}

        @Override int kareAl() {
            zaman();
            String anahtar = dunyaSurum + "/" + gosterimSurum + "/" + (hareketli != null ? t / 33 : 0);
            if (anahtar.equals(sonKareAnahtar) && ham != null) return 1;
            sonKareAnahtar = anahtar;
            ham = ciz();
            sonBloklar = okuSahte();
            return 2;
        }

        static int h(int a, int b, int c) { int x = a * 73856093 ^ b * 19349663 ^ c * 83492791; x ^= x >>> 13; x *= 0x5bd1e995; x ^= x >>> 15; return x & 0x7fffffff; }

        Bitmap ciz() {
            int[] p = new int[W * H];
            java.util.Arrays.fill(p, 0xFF202830);
            int faz = (int) (t / 33);
            if (hareketli != null)
                for (int y = hareketli.top; y < hareketli.bottom; y++) for (int x = hareketli.left; x < hareketli.right; x++) {
                    int v = h(x / 8, y / 8, faz) % 200; p[y * W + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
                }
            for (Yazi yz : yazilar) {
                if (!yz.var) continue;
                Rect g = yz.gorunenAlan();
                int tohum = yz.gorunenMetin().hashCode();
                for (int y = yz.r.top; y < yz.r.bottom; y++) for (int x = yz.r.left; x < yz.r.right; x++) {
                    if (yz.yariSaydam) { int u = p[y * W + x] & 0xFF; int v = (int) (0.7 * 0x38 + 0.3 * u); p[y * W + x] = 0xFF000000 | (v << 16) | (v << 8) | v; }
                    else if (!yz.oynakZemin) p[y * W + x] = 0xFF303A46;
                    if (x < g.right && h(x / 3, y / 3, tohum) % 5 == 0) {
                        int z = p[y * W + x] & 0xFF; int v = Math.min(255, z + yz.kontrast); if (z > 128) v = Math.max(0, z - yz.kontrast);
                        p[y * W + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
                    }
                }
            }
            for (Etiket e : ekrandaki) {
                int tohum = e.ceviri.hashCode();
                for (int y = Math.max(0, e.cizim.top); y < Math.min(H, e.cizim.bottom); y++) for (int x = Math.max(0, e.cizim.left); x < Math.min(W, e.cizim.right); x++) {
                    int c = h(x / 3, y / 3, tohum) % 5 == 0 ? e.yazi : e.arka;
                    int u = p[y * W + x];
                    int r = (int) (0.8 * ((c >> 16) & 0xFF) + 0.2 * ((u >> 16) & 0xFF));
                    int g = (int) (0.8 * ((c >> 8) & 0xFF) + 0.2 * ((u >> 8) & 0xFF));
                    int b = (int) (0.8 * (c & 0xFF) + 0.2 * (u & 0xFF));
                    p[y * W + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
            return new Bitmap(W, H, p);
        }

        /** Kendi kutumuzu geri okurken son harfi yanlış oku: "aynı çıktı" korumasına güvenilmesin. */
        static String bozuk(String s) { return s.length() > 3 ? s.substring(0, s.length() - 1) + "x" : s; }

        static long kes(Rect a, Rect b) { int w = Math.min(a.right, b.right) - Math.max(a.left, b.left), hh = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top); return w <= 0 || hh <= 0 ? 0 : (long) w * hh; }

        List<Blok> okuSahte() {
            List<Blok> l = new ArrayList<>();
            java.util.HashSet<Etiket> birlesen = new java.util.HashSet<>();
            for (Yazi yz : yazilar) {
                if (!yz.var) continue;
                Rect g = yz.gorunenAlan();
                if (g.width() < 8) continue;
                String m = yz.gorunenMetin();
                Etiket en = null; long enK = 0;
                for (Etiket e : ekrandaki) { long k = kes(g, e.cizim); if (k > enK) { enK = k; en = e; } }
                if (en == null) { l.add(new Blok(m, g, yz.tekSatir ? 1 : 3, yz.tekSatir ? g.height() : g.height() / 3f, 0.9f, "en")); continue; }
                double ort = (double) enK / ((long) g.width() * g.height());
                if (ort >= 0.9) continue; // kutunun altında, okunmuyor
                // Açıkta kalan kısım kutudaki yazıyla tek öbek olarak okunur (en kötü durum).
                Rect z = en.cizim;
                Rect birlesik = new Rect(Math.min(z.left, g.left), Math.min(z.top, g.top), Math.max(z.right, g.right), Math.max(z.bottom, g.bottom));
                int kalan = (int) Math.round(m.length() * (1 - ort));
                String ek = m.substring(Math.max(0, m.length() - kalan));
                l.add(new Blok(bozuk(en.ceviri) + " " + ek, birlesik, 1, g.height(), 0.9f, "und"));
                birlesen.add(en);
            }
            for (Etiket e : ekrandaki) {
                if (birlesen.contains(e)) continue;
                Rect z = new Rect(e.cizim.left + 3, e.cizim.top + 3, e.cizim.right - 3, e.cizim.bottom - 3);
                l.add(new Blok(bozuk(e.ceviri), z, 1, z.height(), 0.9f, "und"));
            }
            return l;
        }

        @Override List<Blok> oku(Bitmap b) {
            if (ayrinti) { StringBuilder s = new StringBuilder(); for (Blok k : sonBloklar) s.append(" {").append(k.metin).append(" ").append(k.kutu).append("}"); StringBuilder e2 = new StringBuilder(); for (Etiket e : etiketler) e2.append(" <").append(e.kaynak).append(" ").append(e.cizim).append(e.ref == null ? " ref-yok" : "").append(">"); gunluk.add(String.format("%6d ms  OKU:%s | kutular:%s", t, s, e2)); }
            t += 150; return sonBloklar; }

        @Override String cevir(String m) { t += 40; ceviriSayisi++; return m.startsWith("#") ? m : "tr " + m.toLowerCase(); }

        @Override Etiket etiketKur(String kaynak, String ceviri, Rect alan, boolean tekSatir, float satirY, int arka, int yazi) {
            Rect c = new Rect(alan.left - 3, alan.top - 3, alan.right + 3, alan.bottom + 3 + etiketBuyume);
            return new Etiket(kaynak, ceviri, c, arka, yazi, 3, null);
        }

        @Override void goster(ArrayList<Etiket> kopya) {
            gosterSayisi++;
            for (Etiket e : kopya) if (sayilan.add(e)) eklemeler.merge(e.kaynak, 1, Integer::sum);
            if (bekleyenAn >= 0 && gecikme > 100) { ekrandaki = bekleyenListe; gosterimSurum++; }
            bekleyenListe = kopya; bekleyenAn = t + gecikme;
            StringBuilder s = new StringBuilder();
            for (Etiket e : kopya) s.append(e.kat == 2 ? " **[" : e.kat == 1 ? " *[" : " [").append(e.kaynak).append("]");
            gunluk.add(String.format("%6d ms  ekranda:%s", t, s.length() == 0 ? " (boş)" : s.toString()));
        }

        void kos(long bitis) {
            while (t < bitis) { zaman(); long d = adim(); t += d; }
            zaman();
        }

        String ozet() {
            StringBuilder s = new StringBuilder();
            for (Etiket e : etiketler) s.append("[").append(e.kaynak).append("] ");
            return s.toString().trim();
        }
    }

    static int hata = 0;
    static void bekle(boolean kosul, String ne) { System.out.println((kosul ? "  OK   " : "  HATA ") + ne); if (!kosul) hata++; }
    static void baslik(String s) { System.out.println("\n== " + s); }
    static void dok(Dunya d) { for (String s : d.gunluk) System.out.println("     " + s); System.out.println("     okuma=" + d.okumaSayisi + " çeviri=" + d.ceviriSayisi + " çizim=" + d.gosterSayisi + " eklemeler=" + d.eklemeler); }

    static Yazi dugme() { return new Yazi("dugme", "Start Game", new Rect(170, 300, 370, 340)); }
    static Yazi paragraf(String m) { Yazi y = new Yazi("paragraf", m, new Rect(40, 600, 500, 720)); y.tekSatir = false; return y; }
    static Yazi can() { return new Yazi("can", "#HP 120", new Rect(20, 20, 140, 50)); }

    public static void main(String[] a) {
        List<Dunya> tum = new ArrayList<>();
        // 1) Durağan ekran
        baslik("1. Durağan ekran: iki çevrilecek yazı, bir çevrilmeyecek");
        Dunya d = new Dunya();
        d.yazilar.add(dugme()); d.yazilar.add(paragraf("Welcome traveler, the road ahead is long.")); d.yazilar.add(can());
        d.aktif = true; d.kos(20000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 2, "iki kutu var: " + d.ozet());
        bekle(d.okumaSayisi <= 3, "yazı okuma sayısı az (" + d.okumaSayisi + ")");
        bekle(d.gosterSayisi <= 2, "kutular bir kez çizildi, titreme yok (" + d.gosterSayisi + ")");
        long ilk = Long.parseLong(d.gunluk.get(0).trim().split(" ")[0]);
        bekle(ilk <= 1200, "ilk çeviri " + ilk + " ms içinde geldi");

        // 2) Diyalog ilerliyor
        baslik("2. Diyalog değişiyor (5. saniyede yeni cümle)");
        d = new Dunya();
        d.yazilar.add(dugme()); final Yazi p2 = paragraf("Welcome traveler, the road ahead is long."); d.yazilar.add(p2);
        d.olaylar.put(5000L, w -> p2.metin = "The king awaits you inside the old castle.");
        d.aktif = true; d.kos(4900);
        int once = d.gunluk.size();
        d.kos(15000); dok(d); tum.add(d);
        bekle(d.ozet().contains("The king awaits") && !d.ozet().contains("Welcome"), "yeni cümle çevrildi, eskisi kalktı: " + d.ozet());
        long yeniAn = -1; for (int i = once; i < d.gunluk.size(); i++) if (d.gunluk.get(i).contains("The king")) { yeniAn = Long.parseLong(d.gunluk.get(i).trim().split(" ")[0]); break; }
        bekle(yeniAn > 0 && yeniAn - 5000 <= 1500, "yeni çeviri " + (yeniAn - 5000) + " ms sonra geldi");
        bekle(d.etiketler.size() == 2, "düğmenin kutusu yerinde kaldı");

        // 3) Harf harf yazılan diyalog
        baslik("3. Harf harf yazılan diyalog (3. saniyede başlar, 2 saniye sürer)");
        d = new Dunya();
        final Yazi dz = new Yazi("daktilo", "You must find the three lost keys before the moon rises", new Rect(30, 600, 510, 640));
        dz.gorunen = 0; d.yazilar.add(dz); d.yazilar.add(dugme());
        for (int i = 1; i <= dz.metin.length(); i++) { final int n = i; d.olaylar.put(3000L + i * 36L, w -> dz.gorunen = n); }
        d.aktif = true; d.kos(15000); dok(d); tum.add(d);
        boolean tam = false; for (Etiket e : d.etiketler) if (e.kaynak.equals(dz.metin)) tam = true;
        bekle(tam, "sonunda cümlenin tamamı çevrildi: " + d.ozet());
        bekle(d.etiketler.size() == 2, "yarım cümle kutusu kalmadı (" + d.etiketler.size() + " kutu)");
        long bitis = 3000 + dz.metin.length() * 36L, tamAn = -1;
        for (String s : d.gunluk) if (s.contains("[" + dz.metin + "]")) { tamAn = Long.parseLong(s.trim().split(" ")[0]); break; }
        bekle(tamAn > 0 && tamAn - bitis <= 1800, "yazı bittikten " + (tamAn - bitis) + " ms sonra tam çeviri geldi");

        // 4) Hareketli sahne + sabit panellerdeki yazı
        baslik("4. Hareketli sahne, yazılar sabit panelde");
        d = new Dunya();
        d.hareketli = new Rect(0, 100, 540, 500);
        d.yazilar.add(dugme()); d.yazilar.add(paragraf("Welcome traveler, the road ahead is long."));
        d.aktif = true; d.kos(20000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 2, "iki kutu var: " + d.ozet());
        bekle(d.gosterSayisi <= 3, "kutular titremiyor (çizim " + d.gosterSayisi + ")");
        bekle(d.okumaSayisi <= 22, "okuma sıklığı kendini kısıyor (20 sn'de " + d.okumaSayisi + ")");

        // 5) Hareketli sahnenin üstündeki panelsiz yazı
        baslik("5. Hareketli sahnenin üstünde panelsiz yazı");
        d = new Dunya();
        d.hareketli = new Rect(0, 100, 540, 500);
        Yazi hud = new Yazi("hud", "Quest updated", new Rect(150, 200, 400, 240)); hud.oynakZemin = true; d.yazilar.add(hud);
        d.aktif = true; d.kos(30000); dok(d); tum.add(d);
        int ek = d.eklemeler.getOrDefault("Quest updated", 0);
        bekle(ek >= 1, "yazı çevrildi");
        bekle(ek <= 9, "30 saniyede en fazla birkaç kez yenilendi (" + ek + ")");

        // 6) Yazı kayboluyor
        baslik("6. Diyalog kapanıyor (5. saniyede)");
        d = new Dunya();
        d.yazilar.add(dugme()); final Yazi p6 = paragraf("Welcome traveler, the road ahead is long."); d.yazilar.add(p6);
        d.olaylar.put(5000L, w -> p6.var = false);
        d.aktif = true; d.kos(15000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 1 && d.ozet().contains("Start Game"), "kapanan diyaloğun kutusu kalktı: " + d.ozet());
        long kalkAn = -1; for (String s : d.gunluk) { long z = Long.parseLong(s.trim().split(" ")[0]); if (z >= 5000 && !s.contains("Welcome")) { kalkAn = z; break; } }
        bekle(kalkAn > 0 && kalkAn - 5000 <= 800, "kutu " + (kalkAn - 5000) + " ms içinde kalktı");

        // 7) Kapat / aç
        baslik("7. Baloncukla kapat (4. sn) ve yeniden aç (6. sn)");
        d = new Dunya();
        d.yazilar.add(dugme()); d.yazilar.add(paragraf("Welcome traveler, the road ahead is long."));
        d.aktif = true; d.kos(4000);
        d.aktif = false; d.sifirla(); d.kos(6000);
        bekle(d.etiketler.isEmpty() && d.bekleyenListe.isEmpty(), "kapalıyken ekranda kutu yok");
        int oncekiCeviri = d.ceviriSayisi;
        d.aktif = true; d.sifirla(); long acAn = d.t; d.kos(12000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 2, "açınca kutular geri geldi: " + d.ozet());
        bekle(d.ceviriSayisi == oncekiCeviri, "aynı yazılar yeniden çevrilmedi (önbellek)");
        long geriAn = -1; for (String s : d.gunluk) { long z = Long.parseLong(s.trim().split(" ")[0]); if (z > acAn && s.contains("Start Game")) { geriAn = z; break; } }
        bekle(geriAn > 0 && geriAn - acAn <= 900, "açtıktan " + (geriAn - acAn) + " ms sonra geldi");

        // 8) Kutu büyüyüp çevrilmeyen komşu yazının üstüne taşıyor
        baslik("8. Kutu, çevrilmeyen komşu yazının üstüne taşıyor");
        d = new Dunya();
        d.etiketBuyume = 30;
        d.yazilar.add(new Yazi("ust", "Inventory", new Rect(100, 300, 300, 340)));
        d.yazilar.add(new Yazi("alt", "#XP 4500 / 9000", new Rect(100, 350, 420, 390)));
        d.aktif = true; d.kos(30000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 1, "kutu duruyor: " + d.ozet());
        bekle(d.eklemeler.getOrDefault("Inventory", 0) <= 3, "sonsuz sil-ekle döngüsü yok (" + d.eklemeler.get("Inventory") + " ekleme)");
        bekle(d.okumaSayisi <= 12, "boşuna okuma yok (" + d.okumaSayisi + ")");

        // 8b) Aynısı, ekranın başka yerinde hareket varken (sürekli yeniden okuma olur)
        baslik("8b. Taşan kutu + ekranda hareket");
        d = new Dunya();
        d.etiketBuyume = 30; d.hareketli = new Rect(0, 600, 540, 900);
        d.yazilar.add(new Yazi("ust", "Inventory", new Rect(100, 300, 300, 340)));
        d.yazilar.add(new Yazi("alt", "#XP 4500 / 9000", new Rect(100, 350, 420, 390)));
        d.aktif = true; d.kos(30000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 1, "kutu duruyor: " + d.ozet());
        bekle(d.eklemeler.getOrDefault("Inventory", 0) <= 4, "sil-ekle döngüsü sınırlı (" + d.eklemeler.get("Inventory") + " ekleme)");

        // 10) Hareketli sahnede harf harf yazılan diyalog
        baslik("10. Hareketli sahnede harf harf yazılan diyalog");
        d = new Dunya();
        d.hareketli = new Rect(0, 100, 540, 500);
        final Yazi dz2 = new Yazi("daktilo", "You must find the three lost keys before the moon rises", new Rect(30, 600, 510, 640));
        dz2.gorunen = 0; d.yazilar.add(dz2);
        for (int i = 1; i <= dz2.metin.length(); i++) { final int n = i; d.olaylar.put(3000L + i * 36L, w -> dz2.gorunen = n); }
        d.aktif = true; d.kos(15000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 1 && d.etiketler.get(0).kaynak.equals(dz2.metin), "yalnızca tam cümlenin kutusu var: " + d.ozet());
        bitis = 3000 + dz2.metin.length() * 36L; tamAn = -1;
        for (String s : d.gunluk) if (s.contains("[" + dz2.metin + "]")) { tamAn = Long.parseLong(s.trim().split(" ")[0]); break; }
        bekle(tamAn > 0 && tamAn - bitis <= 2500, "yazı bittikten " + (tamAn - bitis) + " ms sonra tam çeviri geldi");

        // 11) Harf harf yazı ortada duraklıyor, sonra devam ediyor
        baslik("11. Harf harf yazı ortada 1,6 sn duraklıyor");
        d = new Dunya();
        final Yazi dz3 = new Yazi("daktilo", "You must find the three lost keys before the moon rises", new Rect(30, 600, 510, 640));
        dz3.gorunen = 0; d.yazilar.add(dz3);
        for (int i = 1; i <= dz3.metin.length(); i++) { final int n = i; d.olaylar.put(3000L + i * 36L + (i > 25 ? 1600 : 0), w -> dz3.gorunen = n); }
        d.aktif = true; d.kos(16000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 1 && d.etiketler.get(0).kaynak.equals(dz3.metin), "sonunda yalnızca tam cümlenin kutusu var: " + d.ozet());

        // 12) Liste kayıyor (1 saniye boyunca), sonra duruyor
        baslik("12. Kayan liste: yazılar 1 sn boyunca aşağı kayıyor");
        d = new Dunya();
        final Yazi k1 = new Yazi("k1", "Iron Sword", new Rect(60, 200, 300, 240)), k2 = new Yazi("k2", "Health Potion", new Rect(60, 300, 330, 340));
        d.yazilar.add(k1); d.yazilar.add(k2);
        for (int i = 1; i <= 10; i++) d.olaylar.put(4000L + i * 100L, w -> { k1.r = new Rect(k1.r.left, k1.r.top + 22, k1.r.right, k1.r.bottom + 22); k2.r = new Rect(k2.r.left, k2.r.top + 22, k2.r.right, k2.r.bottom + 22); });
        d.ayrinti = true; d.aktif = true; d.kos(15000); dok(d); tum.add(d);
        boolean yerinde = d.etiketler.size() == 2;
        for (Etiket e : d.etiketler) if (Math.abs(e.cizim.top - (e.kaynak.startsWith("Iron") ? k1.r.top : k2.r.top)) > 6) yerinde = false;
        bekle(yerinde, "kutular yazıların yeni yerinde: " + d.ozet());

        // 13) Diyalog hızlı hızlı ilerliyor
        baslik("13. Diyalog 1,3 saniyede bir değişiyor (6 cümle)");
        d = new Dunya();
        final Yazi p13 = paragraf("Sentence number zero is here."); d.yazilar.add(p13);
        final String[] cumle = {"The gate is closed tonight.", "Bring me the silver ring.", "Beware of the northern woods.", "Your reward waits in the chest.", "Speak to the old miller.", "Farewell and good luck."};
        for (int i = 0; i < cumle.length; i++) { final String c = cumle[i]; d.olaylar.put(3000L + i * 1300L, w -> p13.metin = c); }
        d.aktif = true; d.kos(16000); dok(d); tum.add(d);
        int gorulenCumle = 0; for (String c : cumle) if (d.eklemeler.containsKey(c)) gorulenCumle++;
        bekle(gorulenCumle == cumle.length, "altı cümlenin altısı da çevrildi (" + gorulenCumle + ")");
        bekle(d.etiketler.size() == 1 && d.etiketler.get(0).kaynak.equals(cumle[5]), "ekranda yalnızca son cümle var");

        // 14) Telefon yoğun: kutuların çizilmesi/silinmesi yarım saniye gecikiyor
        baslik("14. Çizim yarım saniye gecikmeli (yoğun oyun), diyalog 5. sn'de değişiyor, 9. sn'de kapanıyor");
        d = new Dunya();
        d.gecikme = 500;
        d.yazilar.add(dugme()); final Yazi p14 = paragraf("Welcome traveler, the road ahead is long."); d.yazilar.add(p14);
        d.olaylar.put(5000L, w -> p14.metin = "The king awaits you inside the old castle.");
        d.olaylar.put(9000L, w -> p14.var = false);
        d.aktif = true; d.kos(8900);
        bekle(d.ozet().contains("The king awaits") && d.etiketler.size() == 2, "yeni cümle çevrildi: " + d.ozet());
        d.kos(16000); dok(d); tum.add(d);
        bekle(d.etiketler.size() == 1 && d.ozet().contains("Start Game"), "kapanınca kutu kalktı: " + d.ozet());
        bekle(d.gosterSayisi <= 5, "gecikme titremeye yol açmadı (çizim " + d.gosterSayisi + ")");

        // 15) Aynısı hareketli sahnede
        baslik("15. Çizim gecikmeli + hareketli sahne");
        d = new Dunya();
        d.gecikme = 500; d.hareketli = new Rect(0, 100, 540, 280);
        d.yazilar.add(dugme()); final Yazi p15 = paragraf("Welcome traveler, the road ahead is long."); d.yazilar.add(p15);
        d.olaylar.put(5000L, w -> p15.metin = "The king awaits you inside the old castle.");
        d.aktif = true; d.kos(16000); dok(d); tum.add(d);
        bekle(d.ozet().contains("The king awaits") && d.etiketler.size() == 2, "yeni cümle çevrildi: " + d.ozet());
        bekle(d.gosterSayisi <= 5, "titreme yok (çizim " + d.gosterSayisi + ")");

        // 16) Bütün ekran hareketli (3B oyun), diyalog opak panelde
        baslik("16. Bütün ekran hareketli, diyalog opak panelde; 6. sn'de cümle değişiyor");
        d = new Dunya();
        d.hareketli = new Rect(0, 0, W, H);
        final Yazi p16 = paragraf("Welcome traveler, the road ahead is long."); d.yazilar.add(p16);
        d.olaylar.put(6000L, w -> p16.metin = "The king awaits you inside the old castle.");
        d.aktif = true; d.kos(5900);
        bekle(d.etiketler.size() == 1 && d.gosterSayisi == 1, "kutu geldi ve titremedi (çizim " + d.gosterSayisi + ")");
        d.kos(20000); dok(d); tum.add(d);
        yeniAn = -1; for (String s : d.gunluk) if (s.contains("The king")) { yeniAn = Long.parseLong(s.trim().split(" ")[0]); break; }
        bekle(yeniAn > 0 && yeniAn - 6000 <= 2600, "yeni cümle " + (yeniAn - 6000) + " ms sonra çevrildi");
        bekle(d.etiketler.size() == 1 && d.gosterSayisi <= 3, "sonrasında da titreme yok (çizim " + d.gosterSayisi + ")");

        // 17) Bütün ekran hareketli, diyalog yarı saydam panelde
        baslik("17. Bütün ekran hareketli, diyalog yarı saydam panelde; 8. sn'de cümle değişiyor");
        d = new Dunya();
        d.hareketli = new Rect(0, 0, W, H);
        final Yazi p17 = paragraf("Welcome traveler, the road ahead is long."); p17.yariSaydam = true; d.yazilar.add(p17);
        d.olaylar.put(8000L, w -> p17.metin = "The king awaits you inside the old castle.");
        d.aktif = true; d.kos(7900);
        int cizimOnce = d.gosterSayisi;
        bekle(d.etiketler.size() == 1 && cizimOnce <= 3, "kutu geldi, en fazla bir kez yenilendi (çizim " + cizimOnce + ")");
        d.kos(24000); dok(d); tum.add(d);
        yeniAn = -1; for (String s : d.gunluk) if (s.contains("The king")) { yeniAn = Long.parseLong(s.trim().split(" ")[0]); break; }
        bekle(yeniAn > 0 && yeniAn - 8000 <= 3000, "yeni cümle " + (yeniAn - 8000) + " ms sonra çevrildi");
        bekle(d.etiketler.size() == 1 && d.gosterSayisi - cizimOnce <= 4, "sonrasında titreme yok (çizim " + d.gosterSayisi + ")");

        // 9) Kendi çevirisini yeniden çevirmiyor
        baslik("9. Hiçbir senaryoda kendi çevirimizi çevirmedik mi?");
        boolean temiz = true;
        for (Dunya w : tum) for (String k : w.eklemeler.keySet()) if (k.startsWith("tr ") || k.contains(" tr ")) { temiz = false; System.out.println("     kendi çevirisi çevrilmiş: " + k); }
        bekle(temiz, "kendi kutularımız hiç yeniden çevrilmedi");

        System.out.println(hata == 0 ? "\nTÜM DENEMELER GEÇTİ" : "\n" + hata + " DENEME BAŞARISIZ");
        System.exit(hata == 0 ? 0 : 1);
    }
}
