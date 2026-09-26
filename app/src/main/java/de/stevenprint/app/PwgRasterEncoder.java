package de.stevenprint.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal standards-conformant PWG Raster producer.
 *
 * Produces one full-page sRGB 8-bit page at a printer-advertised resolution.
 * The page bitmap uses the PWG PackBits-like line compression defined by
 * PWG 5102.4. No vendor/PCL data is embedded.
 */
public final class PwgRasterEncoder {
    private static final int HEADER_SIZE = 1796;
    private static final int DEFAULT_DPI = 300;
    private static final int MAX_SOURCE = 4200;

    private PwgRasterEncoder() {}

    public static byte[] encode(Bitmap source, int dpi) throws Exception {
        if (dpi <= 0) dpi = DEFAULT_DPI;
        if (dpi > 600) {
            throw new IllegalArgumentException("PWG-Raster ist auf maximal 600 DPI begrenzt.");
        }
        if (source == null || source.isRecycled()) {
            throw new IllegalArgumentException("Ungültiges Quell-Bild.");
        }

        int pageW = source.getWidth();
        int pageH = source.getHeight();

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(4096, pageW * pageH / 2));
        DataOutputStream d = new DataOutputStream(out);

        d.writeInt(0x52615332); // "RaS2"
        PrintLayout layout = new PrintLayout();
        writeHeader(d, layout, false, dpi, pageW, pageH);

        int[] pixels = new int[pageW];
        byte[] rgbLine = new byte[pageW * 3];

        for (int y = 0; y < pageH; y++) {
            source.getPixels(pixels, 0, pageW, 0, y, pageW, 1);
            int p = 0;
            for (int x = 0; x < pageW; x++) {
                int argb = pixels[x];
                rgbLine[p++] = (byte) ((argb >> 16) & 0xff);
                rgbLine[p++] = (byte) ((argb >> 8) & 0xff);
                rgbLine[p++] = (byte) (argb & 0xff);
            }
            d.writeByte(0);
            encodeLine(d, rgbLine, pageW);
        }

        d.flush();
        return out.toByteArray();
    }

    public static byte[] create(Context context, Uri uri, EditSettings edits,
                                 PrintLayout layout, int dpi) throws Exception {
        if (dpi <= 0) dpi = DEFAULT_DPI;
        if (dpi > 600) {
            throw new IllegalArgumentException("PWG-Raster-Test ist auf maximal 600 DPI begrenzt.");
        }

        Bitmap source = decodeForPrint(context, uri, MAX_SOURCE);
        if (source == null) throw new IllegalArgumentException("Bild konnte nicht gelesen werden.");

        Bitmap image = ImageProcessor.process(source, edits);
        if (image != source) source.recycle();

        float paperWmm = layout.paperWidthMm();
        float paperHmm = layout.paperHeightMm();

        boolean landscape;
        if (layout.orientation == 2) {
            landscape = true;
        } else if (layout.orientation == 1) {
            landscape = false;
        } else {
            landscape = image.getWidth() > image.getHeight()
                    && paperWmm < paperHmm;
        }

        int pageW = mmToPixels(landscape ? paperHmm : paperWmm, dpi);
        int pageH = mmToPixels(landscape ? paperWmm : paperHmm, dpi);

        Bitmap page = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(page);
        canvas.drawColor(Color.WHITE);

        float unitsPerMm = dpi / 25.4f;
        float left = layout.borderless ? 0 : layout.marginLeft * unitsPerMm;
        float top = layout.borderless ? 0 : layout.marginTop * unitsPerMm;
        float right = layout.borderless ? pageW : pageW - layout.marginRight * unitsPerMm;
        float bottom = layout.borderless ? pageH : pageH - layout.marginBottom * unitsPerMm;

        float contentW = Math.max(1, right - left);
        float contentH = Math.max(1, bottom - top);
        float sx = contentW / image.getWidth();
        float sy = contentH / image.getHeight();
        float scale;
        switch (layout.scaleMode) {
            case FILL: scale = Math.max(sx, sy); break;
            case CUSTOM: scale = Math.min(sx, sy) * layout.customScale / 100f; break;
            case ACTUAL: scale = dpi / 300f; break;
            default: scale = Math.min(sx, sy); break;
        }

        float drawW = image.getWidth() * scale;
        float drawH = image.getHeight() * scale;
        float cx = (left + right) / 2f + layout.offsetX * unitsPerMm;
        float cy = (top + bottom) / 2f + layout.offsetY * unitsPerMm;

        RectF dst = new RectF(cx - drawW / 2f, cy - drawH / 2f,
                cx + drawW / 2f, cy + drawH / 2f);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(image, null, dst, paint);

        image.recycle();

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(4096, pageW * pageH / 2));
        DataOutputStream d = new DataOutputStream(out);

        d.writeInt(0x52615332); // "RaS2"
        writeHeader(d, layout, landscape, dpi, pageW, pageH);

        int[] pixels = new int[pageW];
        byte[] rgbLine = new byte[pageW * 3];

        for (int y = 0; y < pageH; y++) {
            page.getPixels(pixels, 0, pageW, 0, y, pageW, 1);
            int p = 0;
            for (int x = 0; x < pageW; x++) {
                int argb = pixels[x];
                rgbLine[p++] = (byte) ((argb >> 16) & 0xff);
                rgbLine[p++] = (byte) ((argb >> 8) & 0xff);
                rgbLine[p++] = (byte) (argb & 0xff);
            }
            // One raster line, no vertical repetition optimization yet.
            d.writeByte(0);
            encodeLine(d, rgbLine, pageW);
        }

        d.flush();
        page.recycle();
        return out.toByteArray();
    }

    private static void writeHeader(DataOutputStream d, PrintLayout layout,
                                    boolean landscape, int dpi, int width, int height)
            throws Exception {
        byte[] header = new byte[HEADER_SIZE];

        putCString(header, 0, "PwgRaster");
        putCString(header, 192, "Photo");

        putInt(header, 268, 0);  // CutMedia = Never
        putInt(header, 272, 0);  // Duplex = false
        putInt(header, 276, dpi);
        putInt(header, 280, dpi);
        putInt(header, 300, 0);  // InsertSheet = false
        putInt(header, 304, 0);  // Jog = Never
        putInt(header, 308, 1);  // LeadingEdge = LongEdgeFirst
        putInt(header, 324, 0);  // MediaPosition = Auto
        putInt(header, 340, 1);  // NumCopies
        putInt(header, 344, landscape ? 1 : 0); // Orientation
        putInt(header, 368, 0);  // Tumble
        putInt(header, 372, width);
        putInt(header, 376, height);
        putInt(header, 384, 8);  // BitsPerColor
        putInt(header, 388, 24); // BitsPerPixel
        putInt(header, 392, width * 3);
        putInt(header, 396, 0); // Chunky
        putInt(header, 400, 19); // sRGB
        putInt(header, 420, 3); // NumColors
        putInt(header, 452, 1); // TotalPageCount
        putInt(header, 456, 1); // CrossFeedTransform
        putInt(header, 460, 1); // FeedTransform
        putInt(header, 484, 5); // High/photo quality

        int pointsW = Math.round((landscape ? layout.paperHeightMm() : layout.paperWidthMm()) / 25.4f * 72f);
        int pointsH = Math.round((landscape ? layout.paperWidthMm() : layout.paperHeightMm()) / 25.4f * 72f);
        putInt(header, 352, pointsW);
        putInt(header, 356, pointsH);

        putCString(header, 1732, mediaName(layout));
        d.write(header);
    }

    private static String mediaName(PrintLayout layout) {
        if ("A4".equals(layout.paper)) return "iso_a4_210x297mm";
        if ("A5".equals(layout.paper)) return "iso_a5_148x210mm";
        if ("10 × 15 cm".equals(layout.paper)) return "na_index-4x6_4x6in";
        return "";
    }

    private static void encodeLine(DataOutputStream d, byte[] line, int pixels)
            throws Exception {
        int x = 0;
        while (x < pixels) {
            int run = 1;
            while (x + run < pixels && run < 128 && samePixel(line, x, x + run)) run++;

            if (run >= 2) {
                d.writeByte(run - 1);
                writePixel(d, line, x);
                x += run;
                continue;
            }

            int start = x++;
            while (x < pixels) {
                if (x - start >= 128) break;
                int nextRun = 1;
                while (x + nextRun < pixels && nextRun < 2 && samePixel(line, x, x + nextRun)) nextRun++;
                if (nextRun >= 2) break;
                x++;
            }

            int count = x - start;
            if (count < 2) {
                d.writeByte(0);
                writePixel(d, line, start);
            } else {
                d.writeByte(257 - count);
                for (int i = 0; i < count; i++) writePixel(d, line, start + i);
            }
        }
    }

    private static boolean samePixel(byte[] line, int a, int b) {
        int x = a * 3, y = b * 3;
        return line[x] == line[y] && line[x + 1] == line[y + 1] && line[x + 2] == line[y + 2];
    }

    private static void writePixel(DataOutputStream d, byte[] line, int pixel) throws Exception {
        int p = pixel * 3;
        d.writeByte(line[p] & 0xff);
        d.writeByte(line[p + 1] & 0xff);
        d.writeByte(line[p + 2] & 0xff);
    }

    private static Bitmap decodeForPrint(Context context, Uri uri, int max) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample(bounds.outWidth, bounds.outHeight, max);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    private static int sample(int width, int height, int max) {
        int sample = 1;
        while (width / sample > max || height / sample > max) sample *= 2;
        return sample;
    }

    private static int mmToPixels(float mm, int dpi) {
        return Math.max(1, Math.round(mm / 25.4f * dpi));
    }

    private static void putInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private static void putCString(byte[] data, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        int len = Math.min(63, bytes.length);
        System.arraycopy(bytes, 0, data, offset, len);
        data[offset + len] = 0;
    }
}
