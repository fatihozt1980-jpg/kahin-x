package app.anindaceviri;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/** Ekrana bindirilen tek bir çeviri kutusu. */
final class Etiket {
    final String kaynak;
    final String ceviri;
    /** Ekrandaki kutu (piksel). Her zaman özgün yazının alanını kaplar. */
    final Rect cizim;
    final int arka;
    final int yazi;
    final int ic;
    final StaticLayout yerlesim;
    /**
     * Kutunun altındaki zemin ne kadar oynak:
     * 0 = durağan (en küçük değişimde kutu yenilenir),
     * 1 = arkada hareket var (yalnızca yazı değişimi kadar belirgin farkta yenilenir),
     * 2 = yazı doğrudan hareketli görüntünün üstünde (en fazla 5 saniyede bir yenilenir).
     */
    int kat;
    /** Bu yazı bu yerde kaçıncı kez kutulanıyor. */
    int eklenme;
    /** Önceki okumada da aynı yazı aynı yerdeydi (yani yazı duruyor). */
    boolean yerinde;
    /** Yazı + yaklaşık konum: aynı kutunun kaç kez eklendiğini saymak için. */
    String anahtar;

    long gosterildi;
    /** Kutu eklendiği andaki ekran görüntüsü sırası; "ref" ancak daha yeni bir görüntüden alınır. */
    long kare;
    /** Kutu çizilmeden önce o alanın küçük görüntüsü: kutunun ekrana gerçekten geldiğini anlamak için. */
    int[] once;
    /** Etiket ekrana çizildikten sonra altındaki küçük görüntü parçası; değişirse etiket eskimiştir. */
    int[] ref;
    /** Kutu kaldırılırken o alanın küçük görüntüsü ve zamanı: ekrandan gerçekten gittiğini anlamak için. */
    int[] kalkis;
    long kalkti;

    Etiket(String kaynak, String ceviri, Rect cizim, int arka, int yazi, int ic,
           StaticLayout yerlesim) {
        this.kaynak = kaynak;
        this.ceviri = ceviri;
        this.cizim = cizim;
        this.arka = arka;
        this.yazi = yazi;
        this.ic = ic;
        this.yerlesim = yerlesim;
    }

    /**
     * Çeviriyi özgün yazının kutusuna sığdırır: önce yazıyı küçültür, yetmezse tek satırlık
     * kısa yazılarda kutuyu yana, diğerlerinde aşağı doğru büyütür.
     */
    static Etiket uret(String kaynak, String ceviri, Rect alan, boolean tekSatir, float satirY,
                       int arka, int yazi, float yogunluk, int ekranW, int ekranH) {
        int ic = Math.max(2, Math.round(3 * yogunluk));
        float enBuyuk = Math.max(9 * yogunluk, Math.min(satirY * 0.95f, 34 * yogunluk));
        float enKucuk = Math.max(8 * yogunluk, enBuyuk * 0.58f);
        int gen = Math.max(alan.width(), Math.round(28 * yogunluk));
        int izin = Math.max(1, alan.height() + Math.round(0.18f * satirY));

        TextPaint boya = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        boya.setColor(yazi);
        boya.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Layout.Alignment hiza = tekSatir ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL;

        float boyut = enBuyuk;
        StaticLayout y = kur(ceviri, boya, boyut, gen, hiza);
        while (y.getHeight() > izin && boyut > enKucuk) {
            boyut = Math.max(enKucuk, boyut * 0.92f);
            y = kur(ceviri, boya, boyut, gen, hiza);
        }
        if (y.getHeight() > izin && tekSatir) {
            int azami = Math.max(gen, Math.min(ekranW - 2 * ic, Math.round(gen * 1.7f)));
            int istenen = (int) Math.ceil(Layout.getDesiredWidth(ceviri, boya)) + 2;
            int yeniGen = Math.max(gen, Math.min(azami, istenen));
            if (yeniGen > gen) {
                gen = yeniGen;
                y = kur(ceviri, boya, boyut, gen, hiza);
            }
        }

        int kutuGen = gen + 2 * ic;
        int kutuYuk = Math.max(alan.height(), y.getHeight()) + 2 * ic;
        int sol = alan.centerX() - kutuGen / 2;
        if (sol + kutuGen > ekranW) sol = ekranW - kutuGen;
        if (sol < 0) sol = 0;
        int ust = alan.top - ic;
        if (ust + kutuYuk > ekranH) ust = ekranH - kutuYuk;
        if (ust < 0) ust = 0;
        Rect cizim = new Rect(sol, ust, sol + kutuGen, ust + kutuYuk);
        return new Etiket(kaynak, ceviri, cizim, arka, yazi, ic, y);
    }

    private static StaticLayout kur(String m, TextPaint boya, float boyut, int gen, Layout.Alignment hiza) {
        boya.setTextSize(boyut);
        return StaticLayout.Builder.obtain(m, 0, m.length(), boya, gen)
                .setAlignment(hiza)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.0f)
                .build();
    }
}
