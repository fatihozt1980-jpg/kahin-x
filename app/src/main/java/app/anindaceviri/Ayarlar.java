package app.anindaceviri;

import android.content.Context;
import android.content.SharedPreferences;

/** Kalıcı ayarlar: oyunun (kaynak) dili. Hedef dil her zaman Türkçe. */
final class Ayarlar {
    static final String[] KODLAR = {"en", "de", "fr", "es", "it", "pt"};
    static final String[] ADLAR = {"İngilizce", "Almanca", "Fransızca", "İspanyolca", "İtalyanca", "Portekizce"};

    private Ayarlar() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("ayar", Context.MODE_PRIVATE);
    }

    static int dilSirasi(Context c) {
        int i = p(c).getInt("dil", 0);
        return (i < 0 || i >= KODLAR.length) ? 0 : i;
    }

    static void dilSirasiYaz(Context c, int i) {
        p(c).edit().putInt("dil", i).apply();
    }

    static String kaynakDil(Context c) {
        return KODLAR[dilSirasi(c)];
    }
}
