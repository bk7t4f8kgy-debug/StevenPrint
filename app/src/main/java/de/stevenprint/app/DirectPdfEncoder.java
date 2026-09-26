package de.stevenprint.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * Erzeugt mehrseitige PDF-Dokumente für den IPP-Druck, inklusive optionaler
 * Übertragung der Bearbeitungsparameter auf alle PDF-Seiten.
 */
public final class DirectPdfEncoder {

    private static final String TAG = "DirectPdfEncoder";

    private DirectPdfEncoder() {}

    public static byte[] createMultiPagePdf(Context context, Uri pdfUri, EditSettings edits, PrintLayout layout, boolean applyEditsToAll)
            throws Exception {
        ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(pdfUri, "r");
        if (fd == null) throw new IllegalArgumentException("PDF-Datei konnte nicht geöffnet werden.");

        PdfRenderer renderer = new PdfRenderer(fd);
        int pageCount = renderer.getPageCount();

        PdfDocument pdf = new PdfDocument();
        try {
            float paperW = layout.paperWidthMm();
            float paperH = layout.paperHeightMm();
            if (layout.orientation == 2) {
                float t = paperW; paperW = paperH; paperH = t;
            }

            int pdfPageW = Math.max(72, Math.round(paperW * 72f / 25.4f));
            int pdfPageH = Math.max(72, Math.round(paperH * 72f / 25.4f));

            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page rendererPage = renderer.openPage(i);
                int renderW = Math.min(1600, rendererPage.getWidth() * 2);
                int renderH = Math.min(2200, rendererPage.getHeight() * 2);
                Bitmap pageBitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(pageBitmap);
                c.drawColor(Color.WHITE);
                rendererPage.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                rendererPage.close();

                Bitmap processed;
                if (applyEditsToAll || i == 0) {
                    processed = ImageProcessor.process(pageBitmap, edits);
                    if (processed != pageBitmap) pageBitmap.recycle();
                } else {
                    processed = pageBitmap;
                }

                PdfDocument.Page pdfPage = pdf.startPage(new PdfDocument.PageInfo.Builder(pdfPageW, pdfPageH, i + 1).create());
                Canvas canvas = pdfPage.getCanvas();

                PrintLayoutCalculator.Result placement = PrintLayoutCalculator.calculate(pdfPageW, pdfPageH, 72f / 25.4f, processed, layout);
                RectF r = placement.image;
                Rect dst = new Rect(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));

                canvas.save();
                canvas.clipRect(placement.content);
                canvas.rotate(layout.rotation, dst.exactCenterX(), dst.exactCenterY());
                canvas.drawBitmap(processed, null, dst, null);
                canvas.restore();

                pdf.finishPage(pdfPage);
                processed.recycle();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.writeTo(out);
            return out.toByteArray();
        } finally {
            pdf.close();
            renderer.close();
            fd.close();
        }
    }
}
