package app.anindaceviri;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Ekranda yüzen aç/kapat düğmesi. Gri: kapalı, yeşil: çeviri açık, sarı halka: çalışıyor. */
final class BalonGorunumu extends View {
    private final Paint zemin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kenar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint harf = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF yay = new RectF();
    private final float d;
    private boolean acik;
    private boolean mesgul;

    BalonGorunumu(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        kenar.setStyle(Paint.Style.STROKE);
        harf.setColor(0xFFFFFFFF);
        harf.setTextAlign(Paint.Align.CENTER);
        harf.setTypeface(Typeface.DEFAULT_BOLD);
    }

    void acik(boolean a) {
        if (acik == a) return;
        acik = a;
        invalidate();
    }

    void mesgul(boolean m) {
        if (mesgul == m) return;
        mesgul = m;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float yr = Math.min(cx, cy) - 3 * d;
        zemin.setColor(acik ? 0xF00E9F8E : 0xD9333A44);
        c.drawCircle(cx, cy, yr, zemin);
        kenar.setStrokeWidth(1.5f * d);
        kenar.setColor(0xCCFFFFFF);
        c.drawCircle(cx, cy, yr, kenar);
        if (mesgul && acik) {
            kenar.setStrokeWidth(3f * d);
            kenar.setColor(0xFFFFD34D);
            yay.set(cx - yr, cy - yr, cx + yr, cy + yr);
            c.drawArc(yay, -90, 250, false, kenar);
        }
        harf.setTextSize(yr * 1.05f);
        c.drawText("Ç", cx, cy - (harf.descent() + harf.ascent()) / 2f, harf);
    }
}
