package de.stevenprint.app;

import android.graphics.Bitmap;
import android.graphics.RectF;

/** Single source of truth for both paper preview and generated PDF placement. */
public final class PrintLayoutCalculator {
    public static final class Result { public final RectF content,image; Result(RectF c,RectF i){content=c;image=i;} }
    private PrintLayoutCalculator() { }
    public static Result calculate(float pageW,float pageH,float unitsPerMm,Bitmap bitmap,PrintLayout l) {
        float left=l.borderless?0:l.marginLeft*unitsPerMm,right=l.borderless?pageW:pageW-l.marginRight*unitsPerMm,top=l.borderless?0:l.marginTop*unitsPerMm,bottom=l.borderless?pageH:pageH-l.marginBottom*unitsPerMm;
        RectF content=new RectF(left,top,Math.max(left+1,right),Math.max(top+1,bottom)); float sx=content.width()/bitmap.getWidth(),sy=content.height()/bitmap.getHeight(),scale;
        switch(l.scaleMode){case FILL:scale=Math.max(sx,sy);break;case ACTUAL:scale=72f/300f*(unitsPerMm/(72f/25.4f));break;case CUSTOM:scale=Math.min(sx,sy)*l.customScale/100f;break;default:scale=Math.min(sx,sy);}
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale,cx=content.centerX()+l.offsetX*unitsPerMm,cy=content.centerY()+l.offsetY*unitsPerMm;
        return new Result(content,new RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2));
    }
}
