package de.stevenprint.app;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

/** Small, intentionally focused start screen for the photo-print workflow. */
public final class HomeScreen {
    public interface Actions { void choose(); void photo10x15(); void photoA4(); void documentA4(); void profiles(); }
    private HomeScreen() { }
    public static LinearLayout create(Context c, Actions a) {
        float d=c.getResources().getDisplayMetrics().density; LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setPadding((int)(20*d),(int)(24*d),(int)(20*d),(int)(20*d));
        TextView title=t(c,"StevenPrint",30);title.setTextColor(Color.rgb(20,70,130));root.addView(title);TextView sub=t(c,"HP OfficeJet Pro 8124e\nPrivater Fotodruck",15);root.addView(sub);
        Space space=new Space(c);root.addView(space,new LinearLayout.LayoutParams(1,(int)(36*d)));
        Button choose=b(c,"FOTO AUSWÄHLEN");choose.setOnClickListener(v->a.choose());root.addView(choose);
        TextView quick=t(c,"Schnellaktionen",14);quick.setPadding(0,(int)(22*d),0,(int)(6*d));root.addView(quick);LinearLayout row=new LinearLayout(c);add(row,b(c,"10×15 FOTO"),v->a.photo10x15());add(row,b(c,"A4 FOTO"),v->a.photoA4());add(row,b(c,"A4 DOKUMENT"),v->a.documentA4());root.addView(row);
        TextView profile=t(c,"Letztes Druckprofil\nBenutzerdefiniert",14);profile.setPadding(0,(int)(26*d),0,(int)(8*d));root.addView(profile);Button profiles=b(c,"PROFIL ÄNDERN");profiles.setOnClickListener(v->a.profiles());root.addView(profiles);return root;
    }
    private static void add(LinearLayout row,Button b,android.view.View.OnClickListener l){b.setTextSize(10);b.setOnClickListener(l);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
    private static TextView t(Context c,String s,int z){TextView v=new TextView(c);v.setText(s);v.setTextSize(z);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private static Button b(Context c,String s){Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setMinHeight((int)(48*c.getResources().getDisplayMetrics().density));return b;}
}
