package de.stevenprint.app;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * DirectPrintEngine für IPP (Internet Printing Protocol, RFC 8010 / 8011)
 * Speziell optimiert für strenge IPP-Parser wie den des HP OfficeJet Pro 8120e.
 */
public class DirectPrintEngine {

    private static final String TAG = "DirectPrintEngine";

    // IPP Delimiter Tags
    private static final byte TAG_OPERATION_ATTRIBUTES = 0x01;
    private static final byte TAG_JOB_ATTRIBUTES = 0x02;
    private static final byte TAG_END_OF_ATTRIBUTES = 0x03;

    // IPP Value Tags
    private static final byte TAG_INTEGER = 0x21;
    private static final byte TAG_ENUM = 0x23;
    private static final byte TAG_NAME_WITHOUT_LANG = 0x42;
    private static final byte TAG_KEYWORD = 0x44;
    private static final byte TAG_URI = 0x45;
    private static final byte TAG_CHARSET = 0x47;
    private static final byte TAG_NATURAL_LANG = 0x48;
    private static final byte TAG_MIME_TYPE = 0x49;

    public interface PrintCallback {
        void onProgress(int percent, String message);
        void onSuccess(int jobId, int statusCode);
        void onError(int statusCode, String errorMsg);
    }

    /**
     * Führt einen IPP-Druckauftrag (Print-Job, 0x0002) aus.
     *
     * @param printerIp   IP-Adresse des Druckers (z. B. "192.168.178.34")
     * @param port        Port (Standard: 631)
     * @param mimeType    z. B. "image/pwg-raster" oder "image/jpeg"
     * @param documentData Die Binärdaten (PWG-Rasterstream oder JPEG)
     * @param mediaName   IPP Medienname (z. B. "iso_a4_210x297mm")
     * @param callback    Status-Callback
     */
    public static void printDocument(
            final String printerIp,
            final int port,
            final String mimeType,
            final byte[] documentData,
            final String mediaName,
            final PrintCallback callback) {

        new Thread(() -> {
            try {
                if (callback != null) {
                    callback.onProgress(10, "Verbindung zu Drucker wird aufgebaut...");
                }

                String printerUri = "ipp://" + printerIp + ":" + port + "/ipp/print";
                String httpUrl = "http://" + printerIp + ":" + port + "/ipp/print";

                // 1. IPP-Header exakt nach RFC 8011 generieren
                byte[] ippHeader = buildIppPrintJobHeader(printerUri, mimeType, mediaName);

                if (callback != null) {
                    callback.onProgress(30, "Sende Druckdaten (" + (documentData.length / 1024) + " KB)...");
                }

                // 2. HTTP POST Request mit Chunked Transfer oder exakter Länge absetzen
                URL url = new URL(httpUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(20000);

                // Pflicht-Header für HP IPP Stack
                conn.setRequestProperty("Content-Type", "application/ipp");
                conn.setRequestProperty("User-Agent", "StevenPrint/1.0 (Android)");
                conn.setRequestProperty("Connection", "close");

                int totalLength = ippHeader.length + documentData.length;
                conn.setFixedLengthStreamingMode(totalLength);

                // 3. Daten schreiben
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(ippHeader);
                    os.flush();

                    // Dokumentendaten in Chunks schreiben für stabiles Streaming
                    int chunkSize = 16384;
                    int offset = 0;
                    while (offset < documentData.length) {
                        int len = Math.min(chunkSize, documentData.length - offset);
                        os.write(documentData, offset, len);
                        offset += len;

                        if (callback != null) {
                            int progress = 30 + (int) (((float) offset / documentData.length) * 60);
                            callback.onProgress(progress, "Übertrage Daten: " + (offset / 1024) + " KB");
                        }
                    }
                    os.flush();
                }

                // 4. Antwort des Druckers auswerten
                int httpCode = conn.getResponseCode();
                Log.d(TAG, "HTTP Response Code: " + httpCode);

                InputStream is = (httpCode >= 200 && httpCode < 300) 
                        ? conn.getInputStream() 
                        : conn.getErrorStream();

                if (is == null) {
                    if (callback != null) {
                        callback.onError(httpCode, "HTTP-Verbindung fehlgeschlagen: Code " + httpCode);
                    }
                    return;
                }

                // IPP Antwort dekodieren
                ByteArrayOutputStream respBuf = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int r;
                while ((r = is.read(b)) != -1) {
                    respBuf.write(b, 0, r);
                }
                byte[] responseBytes = respBuf.toByteArray();

                parseIppResponse(responseBytes, callback);

            } catch (Exception e) {
                Log.e(TAG, "Druckfehler: ", e);
                if (callback != null) {
                    callback.onError(-1, "Netzwerkfehler: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Erstellt den IPP 1.1 Print-Job Header unter strenger Einhaltung von RFC 8011:
     * - Version 1.1 (0x0101)
     * - Operation 0x0002 (Print-Job)
     * - Group 0x01 (Operation Attributes):
     *     1. attributes-charset (Zwingend Erstes Attribut!)
     *     2. attributes-natural-language (Zwingend Zweites Attribut!)
     *     3. printer-uri (Zwingend Drittes Attribut!)
     *     4. requesting-user-name
     *     5. job-name
     *     6. document-format
     * - Group 0x02 (Job Template Attributes):
     *     - copies
     *     - media
     * - Group 0x03 (End of Attributes)
     */
    private static byte[] buildIppPrintJobHeader(String printerUri, String mimeType, String mediaName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // 1. Version: IPP 1.1 (0x0101)
        dos.writeByte(0x01);
        dos.writeByte(0x01);

        // 2. Operation: Print-Job (0x0002)
        dos.writeShort(0x0002);

        // 3. Request-ID (z. B. 1)
        dos.writeInt(0x00000001);

        // ==========================================
        // GRUPPE 1: OPERATION ATTRIBUTES (0x01)
        // ==========================================
        dos.writeByte(TAG_OPERATION_ATTRIBUTES);

        // 1. attributes-charset (MUSS ZWINGEND AN ERSTER STELLE STEHEN)
        writeAttribute(dos, TAG_CHARSET, "attributes-charset", "utf-8");

        // 2. attributes-natural-language (MUSS ZWINGEND AN ZWEITER STELLE STEHEN)
        writeAttribute(dos, TAG_NATURAL_LANG, "attributes-natural-language", "de");

        // 3. printer-uri (MUSS VORHALTEN SEIN)
        writeAttribute(dos, TAG_URI, "printer-uri", printerUri);

        // 4. requesting-user-name
        writeAttribute(dos, TAG_NAME_WITHOUT_LANG, "requesting-user-name", "AndroidSteven");

        // 5. job-name
        writeAttribute(dos, TAG_NAME_WITHOUT_LANG, "job-name", "StevenPrint Job");

        // 6. document-format (z. B. "image/pwg-raster" oder "image/jpeg")
        writeAttribute(dos, TAG_MIME_TYPE, "document-format", mimeType);

        // ==========================================
        // GRUPPE 2: JOB TEMPLATE ATTRIBUTES (0x02)
        // ==========================================
        dos.writeByte(TAG_JOB_ATTRIBUTES);

        // copies = 1
        dos.writeByte(TAG_INTEGER);
        writeString(dos, "copies");
        dos.writeShort(4);
        dos.writeInt(1);

        // media (z. B. "iso_a4_210x297mm")
        if (mediaName != null && !mediaName.isEmpty()) {
            writeAttribute(dos, TAG_KEYWORD, "media", mediaName);
        }

        // print-quality (4 = normal)
        dos.writeByte(TAG_ENUM);
        writeString(dos, "print-quality");
        dos.writeShort(4);
        dos.writeInt(4);

        // ==========================================
        // GRUPPE 3: END OF ATTRIBUTES (0x03)
        // ==========================================
        dos.writeByte(TAG_END_OF_ATTRIBUTES);

        dos.flush();
        return baos.toByteArray();
    }

    private static void writeAttribute(DataOutputStream dos, byte tag, String name, String value) throws IOException {
        dos.writeByte(tag);
        writeString(dos, name);
        byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(valBytes.length);
        dos.write(valBytes);
    }

    private static void writeString(DataOutputStream dos, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }

    /**
     * Parst die binäre IPP-Antwort des Druckers, extrahiert den numerischen Statuscode
     * sowie die vom Drucker vergebene Job-ID.
     */
    private static void parseIppResponse(byte[] data, PrintCallback callback) {
        if (data == null || data.length < 8) {
            if (callback != null) callback.onError(-1, "Ungültige IPP-Antwort (zu kurz)");
            return;
        }

        int major = data[0] & 0xFF;
        int minor = data[1] & 0xFF;
        int statusCode = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int requestId = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);

        Log.d(TAG, String.format("IPP Response: Version %d.%d, Status 0x%04X, Request-ID %d", major, minor, statusCode, requestId));

        int jobId = -1;

        // Attribute durchsuchen, um job-id (Tag 0x21, Name "job-id") zu finden
        try {
            int idx = 8;
            while (idx < data.length) {
                byte tag = data[idx++];
                if (tag == TAG_END_OF_ATTRIBUTES) break;
                if (tag == TAG_OPERATION_ATTRIBUTES || tag == TAG_JOB_ATTRIBUTES) {
                    continue;
                }

                // Attributname lesen
                if (idx + 2 > data.length) break;
                int nameLen = ((data[idx] & 0xFF) << 8) | (data[idx + 1] & 0xFF);
                idx += 2;
                String attrName = "";
                if (nameLen > 0 && idx + nameLen <= data.length) {
                    attrName = new String(data, idx, nameLen, StandardCharsets.UTF_8);
                    idx += nameLen;
                }

                // Wert lesen
                if (idx + 2 > data.length) break;
                int valLen = ((data[idx] & 0xFF) << 8) | (data[idx + 1] & 0xFF);
                idx += 2;

                if ("job-id".equals(attrName) && tag == TAG_INTEGER && valLen == 4) {
                    jobId = ((data[idx] & 0xFF) << 24) | ((data[idx + 1] & 0xFF) << 16) | ((data[idx + 2] & 0xFF) << 8) | (data[idx + 3] & 0xFF);
                }
                idx += valLen;
            }
        } catch (Exception e) {
            Log.w(TAG, "Konnte Job-ID nicht auslesen", e);
        }

        // IPP-Erfolgcodes: 0x0000 (successful-ok) oder 0x0001 (successful-ok-ignored-or-substituted-attributes)
        if (statusCode == 0x0000 || statusCode == 0x0001) {
            if (callback != null) {
                callback.onSuccess(jobId, statusCode);
            }
        } else {
            String errorName = decodeIppStatus(statusCode);
            if (callback != null) {
                callback.onError(statusCode, errorName + String.format(" (0x%04X)", statusCode));
            }
        }
    }

    private static String decodeIppStatus(int code) {
        switch (code) {
            case 0x0400: return "client-error-bad-request (Syntax/Attributfehler)";
            case 0x0401: return "client-error-forbidden";
            case 0x0403: return "client-error-not-authenticated";
            case 0x0404: return "client-error-not-authorized";
            case 0x0409: return "client-error-document-format-not-supported";
            case 0x040B: return "client-error-attributes-or-values-not-supported";
            case 0x0500: return "server-error-internal-error";
            case 0x0501: return "server-error-operation-not-supported";
            case 0x0503: return "server-error-service-unavailable";
            default: return "IPP-Fehler";
        }
    }
}