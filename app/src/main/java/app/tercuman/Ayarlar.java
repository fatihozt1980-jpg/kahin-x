package app.tercuman;

import android.content.Context;
import android.content.SharedPreferences;

/** Kalıcı ayarlar: seçili diller ve ses tercihleri. */
final class Ayarlar {
    private static SharedPreferences al(Context c) {
        return c.getSharedPreferences("ayar", Context.MODE_PRIVATE);
    }

    static String ben(Context c) {
        return al(c).getString("ben", "tr");
    }

    static String karsi(Context c) {
        return al(c).getString("karsi", "en");
    }

    static void dilYaz(Context c, String ben, String karsi) {
        al(c).edit().putString("ben", ben).putString("karsi", karsi).apply();
    }

    static boolean sesli(Context c) {
        return al(c).getBoolean("sesli", true);
    }

    static void sesliYaz(Context c, boolean acik) {
        al(c).edit().putBoolean("sesli", acik).apply();
    }

    static boolean ayrim(Context c) {
        return al(c).getBoolean("ayrim", true);
    }

    static void ayrimYaz(Context c, boolean acik) {
        al(c).edit().putBoolean("ayrim", acik).apply();
    }

    private Ayarlar() {
    }
}
