package app.tercuman;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Dondurulmuş kamera görüntüsünün üstüne, özgün yazının yerine çeviri kutularını çizer. */
public class KameraKatmani extends View {
    static final class Kutu {
        final Rect yer;
        final String metin;

        Kutu(Rect yer, String metin) {
            this.yer = yer;
            this.metin = metin;
        }
    }

    private final List<Kutu> kutular = new ArrayList<>();
    private final Paint zemin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint kalem = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF alan = new RectF();

    public KameraKatmani(Context c) {
        super(c);
        kur();
    }

    public KameraKatmani(Context c, AttributeSet a) {
        super(c, a);
        kur();
    }

    private void kur() {
        zemin.setColor(0xF2101820);
        kalem.setColor(0xFFFFFFFF);
    }

    void goster(List<Kutu> yeni) {
        kutular.clear();
        kutular.addAll(yeni);
        invalidate();
    }

    void temizle() {
        kutular.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float yog = getResources().getDisplayMetrics().density;
        float enKucuk = 11 * yog, enBuyuk = 22 * yog;
        int bosluk = Math.round(3 * yog);
        for (Kutu k : kutular) {
            int gen = Math.max(k.yer.width(), Math.round(70 * yog));
            if (k.yer.left + gen > getWidth()) gen = Math.max(getWidth() - k.yer.left, Math.round(40 * yog));
            int yuk = Math.max(k.yer.height(), 1);
            // Yazı kutuya sığana kadar küçült; en küçük boyda da sığmazsa kutu aşağı doğru uzar.
            float boyut = Math.min(enBuyuk, Math.max(enKucuk, yuk * 0.8f));
            StaticLayout d;
            while (true) {
                kalem.setTextSize(boyut);
                d = StaticLayout.Builder.obtain(k.metin, 0, k.metin.length(), kalem, gen).build();
                if (d.getHeight() <= yuk || boyut <= enKucuk) break;
                boyut = Math.max(enKucuk, boyut * 0.88f);
            }
            float alt = Math.max(k.yer.bottom, k.yer.top + d.getHeight());
            alan.set(k.yer.left - bosluk, k.yer.top - bosluk, k.yer.left + gen + bosluk, alt + bosluk);
            c.drawRoundRect(alan, 6 * yog, 6 * yog, zemin);
            c.save();
            c.translate(k.yer.left, k.yer.top);
            d.draw(c);
            c.restore();
        }
    }
}
