package de.stevenprint.app;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/** Compact RGB histogram for the processed preview bitmap. */
public final class HistogramView extends View {
    private final int[] red = new int[256], green = new int[256], blue = new int[256];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int max = 1;

    public HistogramView(Context c) {
        super(c);
        init(c);
    }

    public HistogramView(Context c, AttributeSet a) {
        super(c, a);
        init(c);
    }

    public HistogramView(Context c, AttributeSet a, int d) {
        super(c, a, d);
        init(c);
    }

    private void init(Context c) {
        setMinimumHeight((int) (110 * c.getResources().getDisplayMetrics().density));
    }

    public void setBitmap(Bitmap bitmap) {
        for (int i = 0; i < 256; i++) red[i] = green[i] = blue[i] = 0;
        max = 1;
        if (bitmap != null) {
            int w = bitmap.getWidth(), h = bitmap.getHeight(), step = Math.max(1, Math.max(w, h) / 600);
            int[] p = new int[w * h];
            bitmap.getPixels(p, 0, w, 0, 0, w, h);
            for (int y = 0; y < h; y += step)
                for (int x = 0; x < w; x += step) {
                    int q = p[y * w + x];
                    red[Color.red(q)]++;
                    green[Color.green(q)]++;
                    blue[Color.blue(q)]++;
                }
            for (int i = 0; i < 256; i++)
                max = Math.max(max, Math.max(red[i], Math.max(green[i], blue[i])));
        }
        invalidate();
    }

    public void updateFromBitmap(Bitmap bitmap) {
        setBitmap(bitmap);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float sx = getWidth() / 255f, sy = (getHeight() - 4f) / max;
        draw(c, red, Color.argb(150, 230, 60, 60), sx, sy);
        draw(c, green, Color.argb(150, 60, 210, 110), sx, sy);
        draw(c, blue, Color.argb(150, 70, 130, 235), sx, sy);
    }

    private void draw(Canvas c, int[] values, int color, float sx, float sy) {
        paint.setColor(color);
        paint.setStrokeWidth(Math.max(1, sx));
        for (int i = 0; i < 256; i++) {
            float x = i * sx;
            c.drawLine(x, getHeight(), x, getHeight() - values[i] * sy, paint);
        }
    }
}
