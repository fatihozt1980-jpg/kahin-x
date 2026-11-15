package android.graphics;
public final class Rect {
    public int left, top, right, bottom;
    public Rect() {}
    public Rect(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public Rect(Rect r) { left = r.left; top = r.top; right = r.right; bottom = r.bottom; }
    public void set(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public void set(Rect r) { left = r.left; top = r.top; right = r.right; bottom = r.bottom; }
    public int width() { return right - left; }
    public int height() { return bottom - top; }
    public int centerX() { return (left + right) >> 1; }
    public int centerY() { return (top + bottom) >> 1; }
    public boolean isEmpty() { return left >= right || top >= bottom; }
    public static boolean intersects(Rect a, Rect b) { return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom; }
    public String toString() { return "[" + left + "," + top + " " + right + "," + bottom + "]"; }
}
