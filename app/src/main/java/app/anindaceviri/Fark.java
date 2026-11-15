package app.anindaceviri;

/** Küçültülmüş ekran görüntüleri üzerinde değişim ölçümü. Android sınıfı kullanmaz. */
final class Fark {
    private Fark() {}

    /** ARGB renk dizisini yerinde parlaklığa (0-255) çevirir. */
    static void griyeCevir(int[] p) {
        for (int i = 0; i < p.length; i++) {
            int c = p[i];
            p[i] = (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 150 + (c & 0xFF) * 29) >> 8;
        }
    }

    /** Maskelenmemiş noktaları karşılaştırır. cikti[0] = eşiği aşan nokta, cikti[1] = bakılan nokta. */
    static void say(int[] a, int[] b, boolean[] maske, int esik, int[] cikti) {
        int degisen = 0, toplam = 0;
        for (int i = 0; i < a.length; i++) {
            if (maske != null && maske[i]) continue;
            toplam++;
            int d = a[i] - b[i];
            if (d < 0) d = -d;
            if (d > esik) degisen++;
        }
        cikti[0] = degisen;
        cikti[1] = toplam;
    }

    /** w x h boyutlu maskede verilen dikdörtgeni işaretler (taşan kısım kırpılır). */
    static void maskele(boolean[] m, int w, int h, int l, int t, int r, int b) {
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > w) r = w;
        if (b > h) b = h;
        for (int y = t; y < b; y++) {
            int o = y * w;
            for (int x = l; x < r; x++) m[o + x] = true;
        }
    }

    /** Dikdörtgenin içindeki parlaklık değerlerini kopyalar (kenardan bir nokta içeriden). */
    static int[] yama(int[] gri, int w, int h, int l, int t, int r, int b) {
        if (r - l > 4) { l++; r--; }
        if (b - t > 4) { t++; b--; }
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > w) r = w;
        if (b > h) b = h;
        int gw = r - l, gh = b - t;
        if (gw <= 0 || gh <= 0) return new int[0];
        int adim = (gw * gh > 3000) ? 2 : 1;
        int nx = (gw + adim - 1) / adim, ny = (gh + adim - 1) / adim;
        int[] c = new int[nx * ny];
        int k = 0;
        for (int y = t; y < b; y += adim) {
            int o = y * w;
            for (int x = l; x < r; x += adim) c[k++] = gri[o + x];
        }
        return c;
    }

    /** İki yama belirgin biçimde farklı mı: eşiği aşan noktaların oranı "oran"dan büyükse evet. */
    static boolean farkli(int[] a, int[] b, int esik, float oran) {
        if (a.length != b.length) return true;
        if (a.length == 0) return false;
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            int d = a[i] - b[i];
            if (d < 0) d = -d;
            if (d > esik) n++;
        }
        return n > Math.max(2, Math.round(a.length * oran));
    }
}
