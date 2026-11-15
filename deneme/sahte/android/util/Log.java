package android.util;
public final class Log {
    public static int w(String t, String m, Throwable e) { System.out.println("LOG.w " + m + " " + e); return 0; }
    public static int e(String t, String m, Throwable e) { System.out.println("LOG.e " + m + " " + e); return 0; }
}
