package de.stevenprint.app;

/** Non-destructive editing state. Values are deliberately independent of a Bitmap. */
public final class EditSettings {
    public int brightness, contrast, saturation, temperature, red, green, blue, sharpness;
    /** Gamma is multiplied by 100; points are expressed as percent of the sRGB range. */
    public int gamma = 100, blackPoint, whitePoint = 100;

    public void reset() {
        brightness = contrast = saturation = temperature = red = green = blue = sharpness = blackPoint = 0;
        gamma = 100;
        whitePoint = 100;
    }
}
