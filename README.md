# StevenPrint 3.1

Private Android photo-printing app intended for an HP OfficeJet Pro 8124e. StevenPrint never implements an HP network driver and never uploads photos. It uses Android's system print framework; a compatible print service (HP Print Service Plugin, Mopria, or another installed Android print service) owns printer discovery and communication.

## Included

- Professional, workflow-first interface: Start → Foto → Bearbeiten → Farbe → Layout → Drucken
- Focused home screen with photo picker, 10×15/A4 shortcuts and local profile access
- Editor workspace with four large bottom-style tabs and a persistent interactive preview
- Responsive editor: phones keep the preview above controls; displays from 700 dp use a two-column workspace with controls on the left and the live paper preview on the right.
- `PrintLayoutCalculator` is shared by `PaperPreviewView` and `ImagePrintAdapter`, so margins, cropping, placement, scaling and rotation use one layout calculation for preview and PDF output.

- Gallery/document photo selection with a persisted read permission
- Non-destructive brightness, contrast, saturation, sharpness, colour temperature, RGB, gamma and black/white-point adjustments
- Preview decoding capped around 1600 px so typical 12–48 MP photos do not overload the editor
- Higher-resolution source decoding again for printing, with the current adjustments applied at print time
- A4, A5 and 10 × 15 cm initial page choices; portrait, landscape or automatic orientation; fit, fill, original-size and custom-scale layout modes
- Built-in presets and a locally saved `Benutzerdefiniert` profile
- Android print dialog hand-off, where service-dependent options such as printer choice, paper type, colour/greyscale, quality, copies and duplex are selected
- Live paper preview with page ratio, orientation, margins, position, scaling, rotation and borderless-layout preview
- Drag to position and pinch to scale directly on the paper preview; Undo/Redo retains the last 20 editor states
- Custom page dimensions are previewed locally in millimetres. The Android print service remains authoritative for the media sizes it can physically print.
- Defined sRGB processing order: brightness, black/white point, contrast, gamma, saturation, temperature, RGB and sharpening.
- Auto correction, original/edited comparison, RGB histogram and a pre-print effective-DPI quality report.
- 10×15 photo quick action with automatic orientation and 2:3 fill crop preview.

## Colour management

StevenPrint works in Android's decoded bitmap colour space and uses sRGB as the safe fallback when no usable embedded colour-space data is available. `ColorProfile` is deliberately metadata-only preparation for a future real ICC workflow: it never claims that a generic or invented profile is an HP ICC profile. The screen preview is useful for editing but cannot replace a calibrated display/printer ICC workflow.

The HP Print Service or Mopria controls the actual printer colour mode, media type, quality, copies, duplex and any supported borderless setting in the Android print dialog. StevenPrint does not send an invented DPI or printer-quality setting.

## Requirements and build

- Android Studio Ladybug or newer
- JDK 17
- Android SDK Platform 35

Open the project root in Android Studio and select **Build > Build APK(s)**. From a terminal with Gradle available, run:

```text
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Printing

1. Install and enable HP Print Service Plugin or Mopria in Android settings.
2. Choose a photo, adjust it and tap **Druckeinstellungen** for app-side page/layout choices.
3. Tap **Drucken**, select the HP OfficeJet Pro 8124e in the Android system dialog, and configure the print-service options there.

Borderless printing, custom media sizes, print quality, copies, paper type and duplex are intentionally not simulated by the app. They appear only when the selected Android print service and printer support them. A borderless layout request in StevenPrint therefore means zero app-side margins, not a claim that the printer supports edge-to-edge printing.

## Direct-print preparation

StevenPrint 3.1 adds a local, standards-based IPP capability probe. Entering a printer IP sends only an IPP `Get-Printer-Attributes` request to port 631; it reads the printer-reported model, URI, document formats, media names, colour, duplex and resolution support. No image is uploaded while probing.

HP lists the OfficeJet Pro 8124e print language as HP PCL3 GUI and documents IPP over TLS, AirPrint, Mopria and Wi-Fi Direct. StevenPrint deliberately does **not** emit guessed PCL3 GUI or raw data. A direct IPP print-job sender is not enabled until a real target printer has confirmed its URI and accepted document format. Android System Print remains the production print path.

## Notes

`gradle-wrapper` was not present in the supplied archive. Android Studio can generate it through **Gradle > wrapper** if a command-line wrapper is required. No signing key is included; release signing must be configured by the owner.
