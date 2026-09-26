package de.stevenprint.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * DirectJpegEncoder für den IPP-JPEG-Direktdruck.
 * Wandelt Bitmap-Grafiken in saubere, hochqualitative JPEG-Datenströme um.
 * Stellt sicher, dass transparente Bereiche auf einem rein weißen Hintergrund
 * gerendert werden (verhindert schwarze Flächen auf dem Ausdruck).
 */
public class DirectJpegEncoder {

    private static final String TAG = "DirectJpegEncoder";

    /**
     * Konvertiert eine Android-Bitmap in ein standardkonformes JPEG-Byte-Array.
     *
     * @param srcBitmap Die bearbeitete Bitmap
     * @param quality   JPEG-Qualität (1-100, empfohlen: 92-95 für exzellente Schärfe ohne übermäßige Dateigröße)
     * @return JPEG-Binärdaten
     */
    public static byte[] encode(Bitmap srcBitmap, int quality) throws IOException {
        if (srcBitmap == null || srcBitmap.isRecycled()) {
            throw new IllegalArgumentException("Quell-Bitmap ist null oder bereits recycelt.");
        }

        // Falls die Bitmap Transparenz besitzt (z. B. PNG mit Alphakanal),
        // muss sie zwingend auf weißem Grund gerendert werden,
        // da JPEG keinen Alphakanal unterstützt und Drucker Transparenz sonst als Schwarz ausgeben.
        Bitmap outputBitmap;
        if (srcBitmap.hasAlpha()) {
            outputBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(outputBitmap);
            canvas.drawColor(Color.WHITE); // Weißes Papier simulieren
            canvas.drawBitmap(srcBitmap, 0f, 0f, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            outputBitmap = srcBitmap;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            boolean success = outputBitmap.compress(Bitmap.CompressFormat.JPEG, Math.max(1, Math.min(100, quality)), baos);
            if (!success) {
                throw new IOException("Bitmap.compress(JPEG) schlug fehl.");
            }
            baos.flush();
            byte[] jpegBytes = baos.toByteArray();
            Log.d(TAG, String.format("JPEG erfolgreich kodiert: %d x %d px, %d Bytes (Qualität: %d)",
                    outputBitmap.getWidth(), outputBitmap.getHeight(), jpegBytes.length, quality));
            return jpegBytes;
        } finally {
            // Temporäre Bitmap freigeben, falls eine Zwischenkopie angelegt wurde
            if (outputBitmap != srcBitmap && !outputBitmap.isRecycled()) {
                outputBitmap.recycle();
            }
        }
    }
}