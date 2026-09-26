package de.stevenprint.app;

import android.print.PrintAttributes;

/** App-side layout settings. Printer capabilities remain owned by Android's print UI. */
public final class PrintLayout {
    public enum ScaleMode { FIT, FILL, ACTUAL, CUSTOM }
    public String paper = "A4";
    public int orientation = 0; // 0 auto, 1 portrait, 2 landscape
    public ScaleMode scaleMode = ScaleMode.FIT;
    public int customScale = 100;
    public int marginTop = 8, marginBottom = 8, marginLeft = 8, marginRight = 8;
    public int offsetX, offsetY;
    public float rotation;
    public boolean borderless;
    public int customPaperWidthMm = 210, customPaperHeightMm = 297;

    public PrintAttributes.MediaSize requestedMediaSize() {
        if ("A5".equals(paper)) return PrintAttributes.MediaSize.ISO_A5;
        if ("10 × 15 cm".equals(paper)) return PrintAttributes.MediaSize.NA_INDEX_4X6;
        // Android's generic custom size constructor is intentionally not used: not every
        // print service accepts arbitrary media. The app still previews this size faithfully.
        return PrintAttributes.MediaSize.ISO_A4;
    }

    public float paperWidthMm() { return "A5".equals(paper) ? 148 : "10 × 15 cm".equals(paper) ? 101.6f : "Benutzerdefiniert".equals(paper) ? customPaperWidthMm : 210; }
    public float paperHeightMm() { return "A5".equals(paper) ? 210 : "10 × 15 cm".equals(paper) ? 152.4f : "Benutzerdefiniert".equals(paper) ? customPaperHeightMm : 297; }
}
