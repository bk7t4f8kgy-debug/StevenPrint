package de.stevenprint.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

/**
 * Speicheroptimierte, deterministische sRGB-Bildbearbeitungspipeline für StevenPrint.
 *
 * Verhindert OutOfMemoryErrors durch zeilenweises (Scanline) Puffern statt
 * vollständiger int[w*h] Framebuffer (Reduktion von ~100-300 MB Heap auf wenige Kilobyte).
 */
public final class ImageProcessor {

    private static final String TAG = "ImageProcessor";

    private ImageProcessor() { }

    public static Bitmap process(Bitmap source, EditSettings s) {
        if (source == null || source.isRecycled()) {
            return null;
        }

        int w = source.getWidth();
        int h = source.getHeight();

        Bitmap result;
        try {
            result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM beim Allokieren des Ergebnis-Bitmaps", oom);
            System.gc();
            return source;
        }

        float contrast = Math.max(0f, 1f + s.contrast / 100f);
        float gamma = Math.max(0.5f, Math.min(2f, s.gamma / 100f));
        float black = Math.max(0, Math.min(100, s.blackPoint)) * 2.55f;
        float white = 255f - Math.max(0, Math.min(100, 100 - s.whitePoint)) * 2.55f;
        if (white <= black) white = black + 1f;
        float sat = Math.max(0f, 1f + s.saturation / 100f);
        float temperature = s.temperature * 1.2f;

        // Speicheroptimierung: Nur eine Scanline (int[w]) im RAM halten
        int[] row = new int[w];

        try {
            for (int y = 0; y < h; y++) {
                source.getPixels(row, 0, w, 0, y, w, 1);
                for (int x = 0; x < w; x++) {
                    int p = row[x];
                    float r = Color.red(p);
                    float g = Color.green(p);
                    float b = Color.blue(p);

                    // 1. Helligkeit
                    r += s.brightness * 2.55f;
                    g += s.brightness * 2.55f;
                    b += s.brightness * 2.55f;

                    // 2. Schwarz-/Weißpunkt (Levels)
                    r = levels(r, black, white);
                    g = levels(g, black, white);
                    b = levels(b, black, white);

                    // 3. Kontrast
                    r = (r - 128f) * contrast + 128f;
                    g = (g - 128f) * contrast + 128f;
                    b = (b - 128f) * contrast + 128f;

                    // 4. Gamma
                    r = 255f * (float) Math.pow(clamp(r) / 255f, 1f / gamma);
                    g = 255f * (float) Math.pow(clamp(g) / 255f, 1f / gamma);
                    b = 255f * (float) Math.pow(clamp(b) / 255f, 1f / gamma);

                    // 5. Sättigung
                    float l = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                    r = l + (r - l) * sat;
                    g = l + (g - l) * sat;
                    b = l + (b - l) * sat;

                    // 6. Farbtemperatur & RGB Kanäle
                    r += temperature + s.red * 2.55f;
                    g += s.green * 2.55f;
                    b += -temperature + s.blue * 2.55f;

                    row[x] = Color.argb(Color.alpha(p), clamp(r), clamp(g), clamp(b));
                }
                result.setPixels(row, 0, w, 0, y, w, 1);
            }

            if (s.sharpness > 0) {
                Bitmap sharpened = sharpenStream(result, s.sharpness);
                if (sharpened != null && sharpened != result) {
                    result.recycle();
                    result = sharpened;
                }
            }
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM während der Scanline-Verarbeitung", oom);
            System.gc();
            return source;
        }

        return result;
    }

    private static float levels(float v, float black, float white) {
        return clamp(v, black, white) * 255f / (white - black) - black * 255f / (white - black);
    }

    /**
     * Schärfen mit 3-Zeilen-Ringpuffer (oben, mitte, unten),
     * verhindert die Allokation zweier vollständiger int[w*h] Arrays.
     */
    private static Bitmap sharpenStream(Bitmap source, int amount) {
        int w = source.getWidth();
        int h = source.getHeight();

        Bitmap outBitmap;
        try {
            outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM bei Schärfen", oom);
            return source;
        }

        float a = Math.min(1f, amount / 100f);

        int[] rowPrev = new int[w];
        int[] rowCurr = new int[w];
        int[] rowNext = new int[w];
        int[] rowOut = new int[w];

        source.getPixels(rowCurr, 0, w, 0, 0, w, 1);

        for (int y = 0; y < h; y++) {
            // Zeilen für Faltungskern laden
            if (y == 0) {
                System.arraycopy(rowCurr, 0, rowPrev, 0, w);
            }
            if (y + 1 < h) {
                source.getPixels(rowNext, 0, w, 0, y + 1, w, 1);
            } else {
                System.arraycopy(rowCurr, 0, rowNext, 0, w);
            }

            for (int x = 0; x < w; x++) {
                int p = rowCurr[x];
                int left = rowCurr[Math.max(0, x - 1)];
                int right = rowCurr[Math.min(w - 1, x + 1)];
                int up = rowPrev[x];
                int down = rowNext[x];

                int r = clamp(Color.red(p) + a * (4 * Color.red(p) - Color.red(left) - Color.red(right) - Color.red(up) - Color.red(down)));
                int g = clamp(Color.green(p) + a * (4 * Color.green(p) - Color.green(left) - Color.green(right) - Color.green(up) - Color.green(down)));
                int b = clamp(Color.blue(p) + a * (4 * Color.blue(p) - Color.blue(left) - Color.blue(right) - Color.blue(up) - Color.blue(down)));

                rowOut[x] = Color.argb(Color.alpha(p), r, g, b);
            }

            outBitmap.setPixels(rowOut, 0, w, 0, y, w, 1);

            // Ringpuffer verschieben
            int[] temp = rowPrev;
            rowPrev = rowCurr;
            rowCurr = rowNext;
            rowNext = temp;
        }

        return outBitmap;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
