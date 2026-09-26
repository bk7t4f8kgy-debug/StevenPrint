package de.stevenprint.app;

/** Metadata only: no manufacturer ICC transform is claimed unless a real profile is supplied. */
public final class ColorProfile {
    public final String name, sourceColorSpace, printerProfile, paperProfile;
    public ColorProfile(String name,String source,String printer,String paper){this.name=name;sourceColorSpace=source;printerProfile=printer;paperProfile=paper;}
    public static ColorProfile sRgbDefault(){return new ColorProfile("sRGB (Standard)","sRGB","Nicht installiert","Nicht festgelegt");}
}
