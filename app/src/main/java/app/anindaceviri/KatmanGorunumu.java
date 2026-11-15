package app.anindaceviri;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Tüm ekranı kaplayan, dokunmaları alttaki oyuna geçiren saydam katman; çeviri kutularını çizer. */
final class KatmanGorunumu extends View {
    private List<Etiket> liste = new ArrayList<>();
    private final Paint zemin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cizgi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int[] konum = new int[2];
    private final float kose;

    KatmanGorunumu(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        kose = 5 * d;
        cizgi.setStyle(Paint.Style.STROKE);
        cizgi.setStrokeWidth(Math.max(1f, d));
    }

    void ayarla(List<Etiket> yeni) {
        liste = yeni;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (liste.isEmpty()) return;
        // Etiket konumları ekran pikseli; pencere tam ekrandan kaymışsa farkı düş.
        getLocationOnScreen(konum);
        for (int i = 0; i < liste.size(); i++) {
            Etiket e = liste.get(i);
            r.set(e.cizim.left - konum[0], e.cizim.top - konum[1],
                    e.cizim.right - konum[0], e.cizim.bottom - konum[1]);
            zemin.setColor(e.arka);
            c.drawRoundRect(r, kose, kose, zemin);
            cizgi.setColor((e.yazi & 0x00FFFFFF) | 0x38000000);
            c.drawRoundRect(r, kose, kose, cizgi);
            float bos = r.height() - 2 * e.ic - e.yerlesim.getHeight();
            c.save();
            c.translate(r.left + e.ic, r.top + e.ic + Math.max(0f, bos / 2f));
            e.yerlesim.draw(c);
            c.restore();
        }
    }
}
