package app.anindaceviri;

import java.util.Locale;

/** Okunan yazıyı temizleme ve "bunu çevirmeye değer mi" kararı. Android sınıfı kullanmaz. */
final class Metin {
    private Metin() {}

    /** Satır sonlarını ve fazla boşlukları tek boşluğa indirir. */
    static String duzelt(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        boolean bosluk = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                bosluk = b.length() > 0;
            } else {
                if (bosluk) b.append(' ');
                bosluk = false;
                b.append(c);
            }
        }
        return b.toString();
    }

    /** Sayı, simge ya da zaten Türkçe olan yazıları eler. */
    static boolean cevrilmeli(String s) {
        int harf = 0, toplam = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) continue;
            toplam++;
            if (Character.isLetter(c)) harf++;
            // Bu harfler yalnızca Türkçede var: yazı zaten Türkçe (büyük olasılıkla kendi etiketimiz).
            if (c == 'ğ' || c == 'Ğ' || c == 'ş' || c == 'Ş') return false;
        }
        if (harf < 2) return false;
        return harf * 2 >= toplam;
    }

    /** Karşılaştırma için: yalnızca harf ve rakamlar, küçük harfle. */
    static String yalin(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString().toLowerCase(Locale.ROOT);
    }
}
