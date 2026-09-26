package de.stevenprint.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Interaktive, dunkle Papier- & Bild-Vorschau mit Touch-Gesten (Verschieben, Zoomen, Drehen).
 */
public final class PaperPreviewView extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF page = new RectF();
    private final RectF content = new RectF();
    private final RectF image = new RectF();

    private Bitmap bitmap;
    private PrintLayout layout = new PrintLayout();
    private Runnable listener;

    private float lastX, lastY, lastDistance;

    public PaperPreviewView(Context c) {
        super(c);
        init();
    }

    public PaperPreviewView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    public PaperPreviewView(Context c, AttributeSet a, int d) {
        super(c, a, d);
        init();
    }

    private void init() {
        fillPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(80, 0, 0, 0));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setColor(Color.parseColor("#23354C"));
    }

    public void setContent(Bitmap b, PrintLayout l) {
        this.bitmap = b;
        if (l != null) this.layout = l;
        invalidate();
    }

    public void setBitmap(Bitmap b) {
        this.bitmap = b;
        invalidate();
    }

    public void setOnLayoutChanged(Runnable r) {
        this.listener = r;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        if (layout == null) layout = new PrintLayout();

        float pw = layout.paperWidthMm();
        float ph = layout.paperHeightMm();

        if (layout.orientation == 2) {
            float temp = pw;
            pw = ph;
            ph = temp;
        }

        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float padding = 24f;
        float scale = Math.min((viewW - padding * 2) / pw, (viewH - padding * 2) / ph);

        float pageW = pw * scale;
        float pageH = ph * scale;
        float left = (viewW - pageW) / 2f;
        float top = (viewH - pageH) / 2f;

        page.set(left, top, left + pageW, top + pageH);

        // Schatten unter dem Papier
        RectF shadow = new RectF(page.left + 4, page.top + 6, page.right + 4, page.bottom + 6);
        c.drawRoundRect(shadow, 6f, 6f, shadowPaint);

        // Weißes Papierblatt
        fillPaint.setColor(Color.WHITE);
        c.drawRoundRect(page, 6f, 6f, fillPaint);

        // Rand des Papierblatts
        c.drawRoundRect(page, 6f, 6f, strokePaint);

        if (bitmap == null || bitmap.isRecycled()) {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.parseColor("#8E9BAE"));
            textPaint.setTextSize(32f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            c.drawText("Fotovorschau", page.centerX(), page.centerY(), textPaint);
            return;
        }

        PrintLayoutCalculator.Result placement = PrintLayoutCalculator.calculate(pageW, pageH, scale, bitmap, layout);

        content.set(placement.content);
        content.offset(left, top);

        image.set(placement.image);
        image.offset(left, top);

        c.save();
        c.clipRect(content);
        c.rotate(layout.rotation, image.centerX(), image.centerY());
        c.drawBitmap(bitmap, null, image, bitmapPaint);
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (layout == null || bitmap == null) return false;

        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lastX = e.getX();
            lastY = e.getY();
            return true;
        } else if (action == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2) {
            lastDistance = distance(e);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (e.getPointerCount() == 1) {
                float mm = Math.min(page.width() / Math.max(1, layout.paperWidthMm()), page.height() / Math.max(1, layout.paperHeightMm()));
                if (mm > 0) {
                    layout.offsetX += Math.round((e.getX() - lastX) / mm);
                    layout.offsetY += Math.round((e.getY() - lastY) / mm);
                }
                lastX = e.getX();
                lastY = e.getY();
                changed();
            } else if (e.getPointerCount() == 2) {
                float d = distance(e);
                if (lastDistance > 0) {
                    layout.scaleMode = PrintLayout.ScaleMode.CUSTOM;
                    layout.customScale = Math.max(10, Math.min(400, Math.round(layout.customScale * d / lastDistance)));
                }
                lastDistance = d;
                changed();
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            lastDistance = 0;
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float distance(MotionEvent e) {
        float x = e.getX(0) - e.getX(1);
        float y = e.getY(0) - e.getY(1);
        return (float) Math.hypot(x, y);
    }

    private void changed() {
        invalidate();
        if (listener != null) listener.run();
    }
}
