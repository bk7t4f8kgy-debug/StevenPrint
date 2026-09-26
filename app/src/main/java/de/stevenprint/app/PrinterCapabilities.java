package de.stevenprint.app;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Exakte Drucker-Fähigkeiten des HP OfficeJet Pro 8120e series (IPP 1.1).
 * Ausgelesen via IPP Get-Printer-Attributes an port 631.
 */
public final class PrinterCapabilities {
    public String model = "HP OfficeJet Pro 8120e series";
    public String ipAddress = "192.168.178.34";
    public String printerUri = "ipp://192.168.178.34/ipp/print";
    public String protocol = "IPP (1.1)";
    public String printerState = "Bereit (3)";
    public String printerStateReasons = "none";

    public final Set<String> documentFormats = new LinkedHashSet<>();
    public final Set<String> paperSizes = new LinkedHashSet<>();
    public final Set<String> mediaTypes = new LinkedHashSet<>();
    public final Set<String> resolutions = new LinkedHashSet<>();
    public final Set<String> printQualities = new LinkedHashSet<>();
    public final Set<String> operations = new LinkedHashSet<>();

    public boolean colorSupported = true;
    public boolean duplexSupported = true;
    public boolean borderlessSupported = true;
    public boolean wifiDirectSupported = true;
    public boolean online = true;

    public PrinterCapabilities() {
        populateCapabilities();
    }

    public void populateCapabilities() {
        // Exakte Dokumentformate aus Druckerrückmeldung
        documentFormats.addAll(Arrays.asList(
                "application/octet-stream",
                "image/pwg-raster",
                "image/urf",
                "image/jpeg",
                "image/tiff",
                "application/vnd.hp-PCL",
                "application/PCLm",
                "application/pdf"
        ));

        // Exakte Papierformate
        paperSizes.addAll(Arrays.asList(
                "iso_a4_210x297mm",
                "iso_a5_148x210mm",
                "iso_a6_105x148mm",
                "om_small-photo_100x150mm (10x15 cm)",
                "na_5x7_5x7in (13x18 cm)",
                "na_letter_8.5x11in",
                "na_legal_8.5x14in",
                "na_index-4x6_4x6in",
                "oe_photo_5x5in",
                "oe_photo_4x12in",
                "iso_dl_110x220mm",
                "iso_c5_162x229mm",
                "iso_c6_114x162mm",
                "jpn_hagaki_100x148mm",
                "custom_min_88.9x127mm"
        ));

        // Exakte HP Medientypen
        mediaTypes.addAll(Arrays.asList(
                "com.hp-photographic-glossy (Fotopapier Glänzend)",
                "com.hp-photographic-inkjet (Fotopapier Inkjet)",
                "com.hp-specialty-glossy (Spezialpapier Glanz)",
                "com.hp-matte-brochure (Broschürenpapier Matt)",
                "com.hp-matte-inkjet (Inkjet Matt)",
                "com.hp-matte-presentation (Präsentationspapier Matt)",
                "com.hp-trifold-brochure-glossy-180gsm",
                "stationery (Normalpapier)",
                "stationery-heavyweight (Schweres Normalpapier)",
                "stationery-lightweight (Leichtes Normalpapier)"
        ));

        // Auflösungen
        resolutions.addAll(Arrays.asList(
                "300x300 dpi (Standard)",
                "600x600 dpi (Hochwertig)",
                "1200x1200 dpi (Maximal)"
        ));

        // Druckqualitäten
        printQualities.addAll(Arrays.asList(
                "3 (Entwurf / Draft)",
                "4 (Normal)",
                "5 (Foto Hoch / High Photo)"
        ));

        // IPP Operationen
        operations.addAll(Arrays.asList(
                "Print-Job (0x02)",
                "Validate-Job (0x04)",
                "Cancel-Job (0x08)",
                "Get-Printer-Attributes (0x0B)",
                "Get-Job-Attributes (0x09)",
                "Send-Document (0x06)",
                "Send-URI (0x07)"
        ));
    }

    public boolean supportsPdf() { return documentFormats.contains("application/pdf"); }
    public boolean supportsJpeg() { return documentFormats.contains("image/jpeg"); }
    public boolean supportsPwgRaster() { return documentFormats.contains("image/pwg-raster"); }
    public boolean supportsAppleRaster() { return documentFormats.contains("image/urf"); }
}
