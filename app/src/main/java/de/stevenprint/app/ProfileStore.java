package de.stevenprint.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Local-only persistence for the current profile; no photo data leaves the device. */
public final class ProfileStore {
    private static final String FILE = "print_profiles";
    private ProfileStore() { }
    public static void save(Context c, String name, EditSettings e, PrintLayout l) {
        SharedPreferences.Editor p=c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit(); String k=name+".";
        p.putInt(k+"brightness",e.brightness).putInt(k+"contrast",e.contrast).putInt(k+"saturation",e.saturation).putInt(k+"sharpness",e.sharpness).putInt(k+"temperature",e.temperature).putInt(k+"red",e.red).putInt(k+"green",e.green).putInt(k+"blue",e.blue).putInt(k+"gamma",e.gamma).putInt(k+"black",e.blackPoint).putInt(k+"white",e.whitePoint).putString(k+"paper",l.paper).putString(k+"scale",l.scaleMode.name()).putInt(k+"orientation",l.orientation).putInt(k+"margin",l.marginLeft).putInt(k+"x",l.offsetX).putInt(k+"y",l.offsetY).putFloat(k+"rotation",l.rotation).putInt(k+"customScale",l.customScale).apply();
    }
    public static boolean load(Context c, String name, EditSettings e, PrintLayout l) {
        SharedPreferences p=c.getSharedPreferences(FILE,Context.MODE_PRIVATE); String k=name+"."; if(!p.contains(k+"brightness"))return false;
        e.brightness=p.getInt(k+"brightness",0);e.contrast=p.getInt(k+"contrast",0);e.saturation=p.getInt(k+"saturation",0);e.sharpness=p.getInt(k+"sharpness",0);e.temperature=p.getInt(k+"temperature",0);e.red=p.getInt(k+"red",0);e.green=p.getInt(k+"green",0);e.blue=p.getInt(k+"blue",0);e.gamma=p.getInt(k+"gamma",100);e.blackPoint=p.getInt(k+"black",0);e.whitePoint=Math.min(100,p.getInt(k+"white",100));l.paper=p.getString(k+"paper","A4");l.orientation=p.getInt(k+"orientation",0);l.marginTop=l.marginBottom=l.marginLeft=l.marginRight=p.getInt(k+"margin",8);l.offsetX=p.getInt(k+"x",0);l.offsetY=p.getInt(k+"y",0);l.rotation=p.getFloat(k+"rotation",0);l.customScale=p.getInt(k+"customScale",100);try{l.scaleMode=PrintLayout.ScaleMode.valueOf(p.getString(k+"scale",PrintLayout.ScaleMode.FIT.name()));}catch(Exception ignored){l.scaleMode=PrintLayout.ScaleMode.FIT;}return true;
    }
}
