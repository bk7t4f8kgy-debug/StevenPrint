package de.stevenprint.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * Orchestriert den Druckvorgang von der Benutzeroberfläche:
 * - Wählt das passende Übertragungsformat (Standard: "image/jpeg" oder "image/pwg-raster")
 * - Mappt Papierformate auf exakte IPP-Medienbezeichner (z. B. "iso_a4_210x297mm")
 * - Leitet Status- und Fortschrittsmeldungen sauber an den Android-Hauptthread weiter
 */
public class DirectPrintJob {

    private static final String TAG = "DirectPrintJob";

    public interface JobProgressListener {
        void onJobProgress(int percent, String message);
        void onJobSuccess(int jobId);
        void onJobFailed(int statusCode, String errorDescription);
    }

    /**
     * Startet einen Druckauftrag für eine gegebene Bitmap.
     *
     * @param context       Android Context für Toasts/UI
     * @param printerIp     IP-Adresse des Druckers (z. B. "192.168.178.34")
     * @param port          IPP-Port (in der Regel 631)
     * @param bitmap        Die zu druckende Bitmap
     * @param paperFormat   Format-Schlüssel (z. B. "A4", "A5", "10x15")
     * @param useJpegDirect Wenn true, wird direkt "image/jpeg" gesendet, ansonsten PWG-Raster
     * @param listener      Optionaler Callback für UI-Updates
     */
    public static void startPrint(
            final Context context,
            final String printerIp,
            final int port,
            final Bitmap bitmap,
            final String paperFormat,
            final boolean useJpegDirect,
            final JobProgressListener listener) {
        startPrint(context, printerIp, port, bitmap, paperFormat, "stationery", 300, false, useJpegDirect, listener);
    }

    public static void startPrint(
            final Context context,
            final String printerIp,
            final int port,
            final Bitmap bitmap,
            final String paperFormat,
            final String mediaType,
            final int dpi,
            final boolean duplex,
            final boolean useJpegDirect,
            final JobProgressListener listener) {

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        if (bitmap == null || bitmap.isRecycled()) {
            notifyError(context, mainHandler, listener, -1, "Ungültiges Bild zum Drucken ausgewählt.");
            return;
        }

        if (printerIp == null || printerIp.trim().isEmpty()) {
            notifyError(context, mainHandler, listener, -1, "Keine Drucker-IP-Adresse angegeben.");
            return;
        }

        final String ippMediaName = resolveIppMediaName(paperFormat);

        new Thread(() -> {
            try {
                byte[] documentBytes;
                String mimeType;

                if (useJpegDirect) {
                    notifyProgress(mainHandler, listener, 15, "Erzeuge hochauflösendes JPEG...");
                    mimeType = "image/jpeg";
                    documentBytes = DirectJpegEncoder.encode(bitmap, 95);
                } else {
                    notifyProgress(mainHandler, listener, 15, "Erzeuge PWG-Rasterdaten (" + dpi + " DPI)...");
                    mimeType = "image/pwg-raster";
                    documentBytes = PwgRasterEncoder.encode(bitmap, dpi);
                }

                notifyProgress(mainHandler, listener, 30, "Sende Daten an " + printerIp + " (" + (documentBytes.length / 1024) + " KB)...");

                DirectPrintEngine.printDocument(
                        printerIp,
                        port,
                        mimeType,
                        documentBytes,
                        ippMediaName,
                        new DirectPrintEngine.PrintCallback() {
                            @Override
                            public void onProgress(int percent, String message) {
                                notifyProgress(mainHandler, listener, percent, message);
                            }

                            @Override
                            public void onSuccess(int jobId, int statusCode) {
                                mainHandler.post(() -> {
                                    String msg = (jobId > 0)
                                            ? "Druckauftrag erfolgreich gestartet! (Job-ID: #" + jobId + ")"
                                            : "Druckdaten erfolgreich übertragen!";
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                                    if (listener != null) listener.onJobSuccess(jobId);
                                });
                            }

                            @Override
                            public void onError(int statusCode, String errorMsg) {
                                notifyError(context, mainHandler, listener, statusCode, errorMsg);
                            }
                        }
                );

            } catch (Exception e) {
                Log.e(TAG, "Fehler bei Druckaufbereitung: ", e);
                notifyError(context, mainHandler, listener, -1, "Fehler: " + e.getLocalizedMessage());
            }
        }).start();
    }

    /**
     * Wandelt die internen Papierformate in standardisierte IPP-Keywords nach PWG 5101.1 um.
     * Diese Bezeichner stimmen exakt mit der Liste aus dem Capabilities-Dump deines HP 8120e überein.
     */
    public static String resolveIppMediaName(String format) {
        if (format == null) return "iso_a4_210x297mm";

        switch (format.toUpperCase().trim()) {
            case "A4":
            case "ISO_A4":
                return "iso_a4_210x297mm";
            case "A5":
            case "ISO_A5":
                return "iso_a5_148x210mm";
            case "A6":
            case "ISO_A6":
                return "iso_a6_105x148mm";
            case "10X15":
            case "PHOTO_10X15":
            case "4X6":
                return "om_small-photo_100x150mm";
            case "13X18":
            case "PHOTO_13X18":
            case "5X7":
                return "na_5x7_5x7in";
            case "LETTER":
                return "na_letter_8.5x11in";
            case "LEGAL":
                return "na_legal_8.5x14in";
            default:
                return "iso_a4_210x297mm";
        }
    }

    private static void notifyProgress(Handler handler, JobProgressListener listener, int percent, String msg) {
        if (listener != null) {
            handler.post(() -> listener.onJobProgress(percent, msg));
        }
    }

    private static void notifyError(Context context, Handler handler, JobProgressListener listener, int statusCode, String message) {
        handler.post(() -> {
            String fullMsg = (statusCode > 0)
                    ? "Druckfehler: " + message
                    : message;
            Toast.makeText(context, fullMsg, Toast.LENGTH_LONG).show();
            if (listener != null) {
                listener.onJobFailed(statusCode, message);
            }
        });
    }
}