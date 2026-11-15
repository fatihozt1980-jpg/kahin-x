package app.anindaceviri;

import android.graphics.Rect;

/** Ekrandan okunan bir yazı öbeği (paragraf, düğme yazısı vb.). */
final class Blok {
    final String metin;
    /** Ekran görüntüsündeki yeri (yakalama pikseli). */
    final Rect kutu;
    final int satir;
    /** Satırların ortalama yüksekliği (yakalama pikseli); bilinmiyorsa 0. */
    final float satirYuk;
    /** Okuma güveni 0-1; bilinmiyorsa 0. */
    final float guven;
    final String dil;

    Blok(String metin, Rect kutu, int satir, float satirYuk, float guven, String dil) {
        this.metin = metin;
        this.kutu = kutu;
        this.satir = satir;
        this.satirYuk = satirYuk;
        this.guven = guven;
        this.dil = dil;
    }
}
