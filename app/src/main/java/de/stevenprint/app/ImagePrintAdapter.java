package de.stevenprint.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Writes one page whose media size and final layout follow the system print attributes. */
public final class ImagePrintAdapter extends PrintDocumentAdapter {
    private final Context context; private final Uri uri; private final EditSettings edits; private final PrintLayout layout; private final String jobName; private PrintAttributes attributes;
    public ImagePrintAdapter(Context context, Uri uri, EditSettings edits, PrintLayout layout, String jobName) { this.context=context; this.uri=uri; this.edits=copy(edits); this.layout=copy(layout); this.jobName=jobName; }
    @Override public void onLayout(PrintAttributes oldAttrs, PrintAttributes newAttrs, CancellationSignal cancel, LayoutResultCallback callback, Bundle extras) {
        attributes=newAttrs; if(cancel.isCanceled()){callback.onLayoutCancelled();return;} PrintDocumentInfo info=new PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).setPageCount(1).build(); callback.onLayoutFinished(info,!newAttrs.equals(oldAttrs));
    }
    @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor dest, CancellationSignal cancel, WriteResultCallback callback) {
        try { if(cancel.isCanceled()){callback.onWriteCancelled();return;} Bitmap source=decodeForPrint(); if(source==null) throw new IllegalArgumentException("Bild konnte nicht gelesen werden"); Bitmap image=ImageProcessor.process(source,edits); if(source!=image) source.recycle(); if(cancel.isCanceled()){image.recycle();callback.onWriteCancelled();return;}
            int[] size=pageSize(); PdfDocument pdf=new PdfDocument(); PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(size[0],size[1],1).create()); drawImage(page.getCanvas(),image,size[0],size[1]); pdf.finishPage(page); try(FileOutputStream out=new FileOutputStream(dest.getFileDescriptor())){pdf.writeTo(out);} pdf.close(); image.recycle(); callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
        } catch(Exception e) { callback.onWriteFailed("Druckdatei konnte nicht erzeugt werden."); }
    }
    private Bitmap decodeForPrint() throws Exception { BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true; try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);} // Keep PDF rendering robust on ordinary devices while retaining much more detail than preview.
        o.inJustDecodeBounds=false; o.inSampleSize=sample(o.outWidth,o.outHeight,4200); o.inPreferredConfig=Bitmap.Config.ARGB_8888; try(InputStream in=context.getContentResolver().openInputStream(uri)){return BitmapFactory.decodeStream(in,null,o);} }
    private int sample(int w,int h,int max){int s=1;while(w/s>max||h/s>max)s*=2;return s;}
    private int[] pageSize(){ PrintAttributes.MediaSize m=attributes!=null?attributes.getMediaSize():layout.requestedMediaSize(); int w=Math.max(72,Math.round(m.getWidthMils()*72f/1000f)), h=Math.max(72,Math.round(m.getHeightMils()*72f/1000f)); return new int[]{w,h}; }
    private void drawImage(Canvas canvas, Bitmap image, int w, int h) { PrintLayoutCalculator.Result placement=PrintLayoutCalculator.calculate(w,h,72f/25.4f,image,layout);RectF r=placement.image;Rect rect=new Rect(Math.round(r.left),Math.round(r.top),Math.round(r.right),Math.round(r.bottom));canvas.save();canvas.clipRect(placement.content);canvas.rotate(layout.rotation,rect.exactCenterX(),rect.exactCenterY());canvas.drawBitmap(image,null,rect,null);canvas.restore(); }
    private static EditSettings copy(EditSettings a){EditSettings b=new EditSettings();b.brightness=a.brightness;b.contrast=a.contrast;b.saturation=a.saturation;b.temperature=a.temperature;b.red=a.red;b.green=a.green;b.blue=a.blue;b.sharpness=a.sharpness;b.gamma=a.gamma;b.blackPoint=a.blackPoint;b.whitePoint=a.whitePoint;return b;}
    private static PrintLayout copy(PrintLayout a){PrintLayout b=new PrintLayout();b.paper=a.paper;b.orientation=a.orientation;b.scaleMode=a.scaleMode;b.customScale=a.customScale;b.marginTop=a.marginTop;b.marginBottom=a.marginBottom;b.marginLeft=a.marginLeft;b.marginRight=a.marginRight;b.offsetX=a.offsetX;b.offsetY=a.offsetY;b.rotation=a.rotation;b.borderless=a.borderless;b.customPaperWidthMm=a.customPaperWidthMm;b.customPaperHeightMm=a.customPaperHeightMm;return b;}
}
