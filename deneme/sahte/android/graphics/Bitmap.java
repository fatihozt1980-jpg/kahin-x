package android.graphics;
public final class Bitmap {
    public enum Config { ARGB_8888 }
    final int w, h; public final int[] p;
    public Bitmap(int w, int h, int[] p) { this.w = w; this.h = h; this.p = p; }
    public static Bitmap createBitmap(int w, int h, Config c) { return new Bitmap(w, h, new int[w * h]); }
    public static Bitmap createScaledBitmap(Bitmap s, int w, int h, boolean f) {
        int[] o = new int[w * h];
        for (int y = 0; y < h; y++) { int sy = (int) ((y + 0.5) * s.h / h); for (int x = 0; x < w; x++) { int sx = (int) ((x + 0.5) * s.w / w); o[y * w + x] = s.p[sy * s.w + sx]; } }
        return new Bitmap(w, h, o);
    }
    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public int getByteCount() { return w * h * 4; }
    public int getPixel(int x, int y) { return p[y * w + x]; }
    public void getPixels(int[] out, int off, int stride, int x, int y, int ww, int hh) { for (int j = 0; j < hh; j++) for (int i = 0; i < ww; i++) out[off + j * stride + i] = p[(y + j) * w + x + i]; }
    public void recycle() {}
    public void copyPixelsFromBuffer(java.nio.Buffer b) {}
}
