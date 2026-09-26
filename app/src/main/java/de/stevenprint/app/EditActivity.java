package de.stevenprint.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Performantes, voll funktionsfähiges Bild-Bearbeitungs-Activity für StevenPrint.
 * Bietet Anpassen (Regler), Filter-Presets, Drehen, Formate und gibt die Parameter
 * direkt an den Druckauftrag weiter.
 */
public class EditActivity extends AppCompatActivity {

    private static final String TAG = "EditActivity";

    private PaperPreviewView editorPaperPreview;
    private TextView tvBrightnessValue, tvContrastValue, tvSaturationValue, tvEditImageDetails;
    private SeekBar seekBarBrightness, seekBarContrast, seekBarSaturation;
    private LinearLayout layoutSliders;
    private View layoutFilters, layoutCrop;
    private LinearLayout toolCrop, toolRotate, toolAdjust, toolFilter;
    private ImageView btnBackFromEdit;
    private Button btnApplyAndPrint;

    private Uri imageUri;
    private Bitmap previewSourceBitmap;
    private Bitmap processedBitmap;
    private final EditSettings editSettings = new EditSettings();
    private final PrintLayout printLayout = new PrintLayout();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        initViews();
        setupListeners();

        if (getIntent() != null && getIntent().getData() != null) {
            imageUri = getIntent().getData();
            loadPreviewImage(imageUri);
        }
    }

    private void initViews() {
        editorPaperPreview = findViewById(R.id.editorPaperPreview);
        tvEditImageDetails = findViewById(R.id.tvEditImageDetails);
        tvBrightnessValue = findViewById(R.id.tvBrightnessValue);
        tvContrastValue = findViewById(R.id.tvContrastValue);
        tvSaturationValue = findViewById(R.id.tvSaturationValue);

        seekBarBrightness = findViewById(R.id.seekBarBrightness);
        seekBarContrast = findViewById(R.id.seekBarContrast);
        seekBarSaturation = findViewById(R.id.seekBarSaturation);

        layoutSliders = findViewById(R.id.layoutSliders);
        layoutFilters = findViewById(R.id.layoutFilters);
        layoutCrop = findViewById(R.id.layoutCrop);

        toolCrop = findViewById(R.id.toolCrop);
        toolRotate = findViewById(R.id.toolRotate);
        toolAdjust = findViewById(R.id.toolAdjust);
        toolFilter = findViewById(R.id.toolFilter);

        btnBackFromEdit = findViewById(R.id.btnBackFromEdit);
        btnApplyAndPrint = findViewById(R.id.btnApplyAndPrint);
    }

    private void setupListeners() {
        if (btnBackFromEdit != null) {
            btnBackFromEdit.setOnClickListener(v -> finish());
        }

        // Tool Tabs Switching
        if (toolAdjust != null) {
            toolAdjust.setOnClickListener(v -> showToolTab(0));
        }
        if (toolFilter != null) {
            toolFilter.setOnClickListener(v -> showToolTab(1));
        }
        if (toolCrop != null) {
            toolCrop.setOnClickListener(v -> showToolTab(2));
        }

        // Drehen
        if (toolRotate != null) {
            toolRotate.setOnClickListener(v -> {
                printLayout.rotation = (printLayout.rotation + 90f) % 360f;
                updatePreviewAsync();
            });
        }

        // Sliders
        if (seekBarBrightness != null) {
            seekBarBrightness.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = progress - 100;
                    editSettings.brightness = val;
                    if (tvBrightnessValue != null) tvBrightnessValue.setText(String.valueOf(val));
                    updatePreviewAsync();
                }
            });
        }

        if (seekBarContrast != null) {
            seekBarContrast.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = progress - 100;
                    editSettings.contrast = val;
                    if (tvContrastValue != null) tvContrastValue.setText(String.valueOf(val));
                    updatePreviewAsync();
                }
            });
        }

        if (seekBarSaturation != null) {
            seekBarSaturation.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = progress - 100;
                    editSettings.saturation = val;
                    if (tvSaturationValue != null) tvSaturationValue.setText(String.valueOf(val));
                    updatePreviewAsync();
                }
            });
        }

        // Filter Presets
        setupFilterPresets();

        // Crop / Scale Mode
        View cropFit = findViewById(R.id.cropFit);
        View cropFill = findViewById(R.id.cropFill);
        View cropA4 = findViewById(R.id.cropA4);
        View cropA5 = findViewById(R.id.cropA5);
        View crop10x15 = findViewById(R.id.crop10x15);

        if (cropFit != null && cropFill != null) {
            cropFit.setOnClickListener(v -> {
                printLayout.scaleMode = PrintLayout.ScaleMode.FIT;
                cropFit.setBackgroundResource(R.drawable.bg_tile_selected);
                cropFill.setBackgroundResource(R.drawable.bg_tile_unselected);
                updatePreviewAsync();
            });
            cropFill.setOnClickListener(v -> {
                printLayout.scaleMode = PrintLayout.ScaleMode.FILL;
                cropFit.setBackgroundResource(R.drawable.bg_tile_unselected);
                cropFill.setBackgroundResource(R.drawable.bg_tile_selected);
                updatePreviewAsync();
            });
        }

        if (cropA4 != null) cropA4.setOnClickListener(v -> { printLayout.paper = "A4"; updatePreviewAsync(); });
        if (cropA5 != null) cropA5.setOnClickListener(v -> { printLayout.paper = "A5"; updatePreviewAsync(); });
        if (crop10x15 != null) crop10x15.setOnClickListener(v -> { printLayout.paper = "10x15"; updatePreviewAsync(); });

        // Apply Button
        if (btnApplyAndPrint != null) {
            btnApplyAndPrint.setOnClickListener(v -> {
                Intent result = new Intent();
                if (imageUri != null) {
                    result.setData(imageUri);
                }
                result.putExtra("brightness", editSettings.brightness);
                result.putExtra("contrast", editSettings.contrast);
                result.putExtra("saturation", editSettings.saturation);
                result.putExtra("sharpness", editSettings.sharpness);
                result.putExtra("temperature", editSettings.temperature);
                result.putExtra("red", editSettings.red);
                result.putExtra("green", editSettings.green);
                result.putExtra("blue", editSettings.blue);
                result.putExtra("rotation", printLayout.rotation);
                result.putExtra("paper", printLayout.paper);
                result.putExtra("scaleMode", printLayout.scaleMode.name());
                setResult(RESULT_OK, result);
                finish();
            });
        }
    }

    private void showToolTab(int index) {
        if (layoutSliders != null) layoutSliders.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (layoutFilters != null) layoutFilters.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (layoutCrop != null) layoutCrop.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        if (toolAdjust != null) toolAdjust.setBackgroundResource(index == 0 ? R.drawable.bg_tile_selected : R.drawable.bg_tile_unselected);
        if (toolFilter != null) toolFilter.setBackgroundResource(index == 1 ? R.drawable.bg_tile_selected : R.drawable.bg_tile_unselected);
        if (toolCrop != null) toolCrop.setBackgroundResource(index == 2 ? R.drawable.bg_tile_selected : R.drawable.bg_tile_unselected);
    }

    private void setupFilterPresets() {
        View fOriginal = findViewById(R.id.filterOriginal);
        View fLebendig = findViewById(R.id.filterLebendig);
        View fNatuerlich = findViewById(R.id.filterNatuerlich);
        View fWarm = findViewById(R.id.filterWarm);
        View fKuehl = findViewById(R.id.filterKuehl);
        View fBW = findViewById(R.id.filterBW);

        if (fOriginal != null) {
            fOriginal.setOnClickListener(v -> {
                editSettings.reset();
                resetSeekBarUi();
                updatePreviewAsync();
            });
        }
        if (fLebendig != null) {
            fLebendig.setOnClickListener(v -> {
                editSettings.reset();
                editSettings.brightness = 10;
                editSettings.contrast = 20;
                editSettings.saturation = 35;
                editSettings.sharpness = 20;
                updateSeekBarUi();
                updatePreviewAsync();
            });
        }
        if (fNatuerlich != null) {
            fNatuerlich.setOnClickListener(v -> {
                editSettings.reset();
                editSettings.brightness = 5;
                editSettings.contrast = 10;
                editSettings.saturation = 10;
                updateSeekBarUi();
                updatePreviewAsync();
            });
        }
        if (fWarm != null) {
            fWarm.setOnClickListener(v -> {
                editSettings.reset();
                editSettings.temperature = 25;
                editSettings.saturation = 15;
                updateSeekBarUi();
                updatePreviewAsync();
            });
        }
        if (fKuehl != null) {
            fKuehl.setOnClickListener(v -> {
                editSettings.reset();
                editSettings.temperature = -25;
                editSettings.saturation = 10;
                updateSeekBarUi();
                updatePreviewAsync();
            });
        }
        if (fBW != null) {
            fBW.setOnClickListener(v -> {
                editSettings.reset();
                editSettings.saturation = -100;
                editSettings.contrast = 15;
                updateSeekBarUi();
                updatePreviewAsync();
            });
        }
    }

    private void resetSeekBarUi() {
        if (seekBarBrightness != null) seekBarBrightness.setProgress(100);
        if (seekBarContrast != null) seekBarContrast.setProgress(100);
        if (seekBarSaturation != null) seekBarSaturation.setProgress(100);
        if (tvBrightnessValue != null) tvBrightnessValue.setText("0");
        if (tvContrastValue != null) tvContrastValue.setText("0");
        if (tvSaturationValue != null) tvSaturationValue.setText("0");
    }

    private void updateSeekBarUi() {
        if (seekBarBrightness != null) seekBarBrightness.setProgress(editSettings.brightness + 100);
        if (seekBarContrast != null) seekBarContrast.setProgress(editSettings.contrast + 100);
        if (seekBarSaturation != null) seekBarSaturation.setProgress(editSettings.saturation + 100);
        if (tvBrightnessValue != null) tvBrightnessValue.setText(String.valueOf(editSettings.brightness));
        if (tvContrastValue != null) tvContrastValue.setText(String.valueOf(editSettings.contrast));
        if (tvSaturationValue != null) tvSaturationValue.setText(String.valueOf(editSettings.saturation));
    }

    private void loadPreviewImage(Uri uri) {
        executor.execute(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                if (mime != null && mime.equalsIgnoreCase("application/pdf")) {
                    previewSourceBitmap = renderPdfToBitmap(uri);
                } else {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        BitmapFactory.decodeStream(in, null, bounds);
                    }

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1280);
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        previewSourceBitmap = BitmapFactory.decodeStream(in, null, options);
                    }
                }

                if (previewSourceBitmap != null) {
                    String details = previewSourceBitmap.getWidth() + " × " + previewSourceBitmap.getHeight() + " px";
                    mainHandler.post(() -> {
                        if (tvEditImageDetails != null) tvEditImageDetails.setText(details);
                        updatePreviewAsync();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Fehler beim Laden in EditActivity: ", e);
                mainHandler.post(() -> Toast.makeText(this, "Bild konnte nicht geladen werden.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private Bitmap renderPdfToBitmap(Uri uri) {
        try {
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r");
            if (fd != null) {
                PdfRenderer renderer = new PdfRenderer(fd);
                if (renderer.getPageCount() > 0) {
                    PdfRenderer.Page page = renderer.openPage(0);
                    int width = Math.min(1280, page.getWidth() * 2);
                    int height = Math.min(1800, page.getHeight() * 2);
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    renderer.close();
                    fd.close();
                    return bitmap;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "PDF Render Fehler: ", e);
        }
        return null;
    }

    private void updatePreviewAsync() {
        if (previewSourceBitmap == null || isProcessing) return;
        isProcessing = true;

        executor.execute(() -> {
            Bitmap result = ImageProcessor.process(previewSourceBitmap, editSettings);
            mainHandler.post(() -> {
                processedBitmap = result;
                if (editorPaperPreview != null) {
                    editorPaperPreview.setContent(processedBitmap, printLayout);
                }
                isProcessing = false;
            });
        });
    }

    private static int sampleSize(int w, int h, int max) {
        int s = 1;
        while (w / s > max || h / s > max) s *= 2;
        return s;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (previewSourceBitmap != null && !previewSourceBitmap.isRecycled()) {
            previewSourceBitmap.recycle();
        }
    }

    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
