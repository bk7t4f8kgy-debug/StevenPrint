package de.stevenprint.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "StevenPrintPrefs";
    private static final String KEY_PRINTER_IP = "printer_ip";
    private static final String DEFAULT_PRINTER_IP = "192.168.178.34";

    private FrameLayout fragmentContainer;
    private BottomNavigationView bottomNavigationView;

    private View viewHome, viewPrint, viewPrinterInfo;
    private String currentPrinterIp;
    private boolean useJpegDirect = true;
    private int selectedDpi = 300;
    private String selectedPaperFormat = "A4";
    private String selectedMediaType = "stationery";
    private boolean isDuplex = false;

    private boolean isPdfFile = false;
    private int pdfPageCount = 1;
    private boolean applySettingsToAllPdfPages = true;

    private Uri selectedImageUri = null;
    private Bitmap originalBitmap = null;
    private Bitmap processedBitmap = null;
    private final EditSettings currentEditSettings = new EditSettings();
    private final PrintLayout currentPrintLayout = new PrintLayout();
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    private PaperPreviewView printTabPreview;
    private TextView tvPrintImageName, tvPrintImageDetails, tvPrintPageBadge;
    private View cardPdfOptions;
    private TextView tvPdfPageCount;
    private SwitchCompat switchApplyToAllPdfPages;
    private MaterialButtonToggleGroup toggleGroupPaperFormat;
    private MaterialButtonToggleGroup toggleGroupDpi;
    private MaterialButtonToggleGroup toggleGroupDuplex;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selectedImageUri = uri;
                        currentEditSettings.reset();
                        currentPrintLayout.rotation = 0f;
                        loadImageFromUri(uri);
                        bottomNavigationView.setSelectedItemId(R.id.nav_print);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> editLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getData() != null) {
                        selectedImageUri = data.getData();
                    }
                    currentEditSettings.brightness = data.getIntExtra("brightness", 0);
                    currentEditSettings.contrast = data.getIntExtra("contrast", 0);
                    currentEditSettings.saturation = data.getIntExtra("saturation", 0);
                    currentEditSettings.sharpness = data.getIntExtra("sharpness", 0);
                    currentEditSettings.temperature = data.getIntExtra("temperature", 0);
                    currentEditSettings.red = data.getIntExtra("red", 0);
                    currentEditSettings.green = data.getIntExtra("green", 0);
                    currentEditSettings.blue = data.getIntExtra("blue", 0);
                    currentPrintLayout.rotation = data.getFloatExtra("rotation", 0f);
                    if (data.hasExtra("paper")) {
                        currentPrintLayout.paper = data.getStringExtra("paper");
                        selectedPaperFormat = currentPrintLayout.paper;
                        syncPaperFormatToggle();
                    }
                    if (data.hasExtra("scaleMode")) {
                        try {
                            currentPrintLayout.scaleMode = PrintLayout.ScaleMode.valueOf(data.getStringExtra("scaleMode"));
                        } catch (Exception ignored) {}
                    }

                    if (originalBitmap != null) {
                        processedBitmap = ImageProcessor.process(originalBitmap, currentEditSettings);
                        updatePreview();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentPrinterIp = prefs.getString(KEY_PRINTER_IP, DEFAULT_PRINTER_IP);

        fragmentContainer = findViewById(R.id.fragmentContainer);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        initTabViews();
        setupNavigation();

        showTab(0);
    }

    private void initTabViews() {
        LayoutInflater inflater = LayoutInflater.from(this);

        viewHome = inflater.inflate(R.layout.tab_home, fragmentContainer, false);
        setupHomeTabListeners(viewHome);

        viewPrint = inflater.inflate(R.layout.tab_print, fragmentContainer, false);
        setupPrintTabListeners(viewPrint);

        viewPrinterInfo = inflater.inflate(R.layout.tab_printer_info, fragmentContainer, false);
        setupPrinterInfoTabListeners(viewPrinterInfo);
    }

    private void setupNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_start) {
                showTab(0);
                return true;
            } else if (id == R.id.nav_print) {
                showTab(1);
                return true;
            } else if (id == R.id.nav_scan) {
                showTab(2);
                return true;
            } else if (id == R.id.nav_settings) {
                showPrinterConfigDialog();
                return false;
            }
            return false;
        });
    }

    private void showTab(int tabIndex) {
        fragmentContainer.removeAllViews();
        if (tabIndex == 0) {
            fragmentContainer.addView(viewHome);
        } else if (tabIndex == 1) {
            fragmentContainer.addView(viewPrint);
        } else if (tabIndex == 2) {
            fragmentContainer.addView(viewPrinterInfo);
        }
    }

    private void setupHomeTabListeners(View home) {
        View cardPhotos = home.findViewById(R.id.cardPrintPhotos);
        if (cardPhotos != null) {
            cardPhotos.setOnClickListener(v -> pickImage());
        }

        View cardDocs = home.findViewById(R.id.cardPrintDocs);
        if (cardDocs != null) {
            cardDocs.setOnClickListener(v -> pickDocument());
        }

        View cardInfo = home.findViewById(R.id.cardPrinterInfo);
        if (cardInfo != null) {
            cardInfo.setOnClickListener(v -> bottomNavigationView.setSelectedItemId(R.id.nav_scan));
        }

        View cardDirectIp = home.findViewById(R.id.cardDirectIpCheck);
        if (cardDirectIp != null) {
            cardDirectIp.setOnClickListener(v -> showPrinterConfigDialog());
        }

        View btnSettings = home.findViewById(R.id.btnHeaderSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showPrinterConfigDialog());
        }
    }

    private void syncPaperFormatToggle() {
        if (toggleGroupPaperFormat == null) return;
        if ("A5".equalsIgnoreCase(selectedPaperFormat)) {
            toggleGroupPaperFormat.check(R.id.btnPaperA5);
        } else if ("10x15".equalsIgnoreCase(selectedPaperFormat)) {
            toggleGroupPaperFormat.check(R.id.btnPaper10x15);
        } else {
            toggleGroupPaperFormat.check(R.id.btnPaperA4);
        }
    }

    private void setupPrintTabListeners(View print) {
        View btnBack = print.findViewById(R.id.btnBackFromPrint);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> bottomNavigationView.setSelectedItemId(R.id.nav_start));
        }

        printTabPreview = print.findViewById(R.id.paperPreviewViewPrintTab);
        tvPrintImageName = print.findViewById(R.id.tvPrintImageName);
        tvPrintImageDetails = print.findViewById(R.id.tvPrintImageDetails);
        tvPrintPageBadge = print.findViewById(R.id.tvPrintPageBadge);

        cardPdfOptions = print.findViewById(R.id.cardPdfOptions);
        tvPdfPageCount = print.findViewById(R.id.tvPdfPageCount);
        switchApplyToAllPdfPages = print.findViewById(R.id.switchApplyToAllPdfPages);

        if (switchApplyToAllPdfPages != null) {
            switchApplyToAllPdfPages.setOnCheckedChangeListener((buttonView, isChecked) -> {
                applySettingsToAllPdfPages = isChecked;
            });
        }

        View btnEdit = print.findViewById(R.id.btnEditPhoto);
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> {
                if (selectedImageUri != null) {
                    Intent intent = new Intent(this, EditActivity.class);
                    intent.setData(selectedImageUri);
                    editLauncher.launch(intent);
                } else {
                    Toast.makeText(this, "Bitte zuerst ein Bild oder Dokument auswählen.", Toast.LENGTH_SHORT).show();
                    pickImage();
                }
            });
        }

        // Paper formats (MaterialButtonToggleGroup)
        toggleGroupPaperFormat = print.findViewById(R.id.toggleGroupPaperFormat);
        if (toggleGroupPaperFormat != null) {
            syncPaperFormatToggle();
            toggleGroupPaperFormat.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btnPaperA4) {
                    selectedPaperFormat = "A4";
                } else if (checkedId == R.id.btnPaperA5) {
                    selectedPaperFormat = "A5";
                } else if (checkedId == R.id.btnPaper10x15) {
                    selectedPaperFormat = "10x15";
                }
                currentPrintLayout.paper = selectedPaperFormat;
                updatePreview();
            });
        }

        // Media Type Spinner
        Spinner spinnerMedia = print.findViewById(R.id.spinnerMediaType);
        if (spinnerMedia != null) {
            String[] mediaTypes = new String[]{
                    "Normalpapier (stationery)",
                    "Fotopapier Glänzend (com.hp-photographic-glossy)",
                    "Fotopapier Inkjet (com.hp-photographic-inkjet)",
                    "Inkjet Matt (com.hp-matte-inkjet)",
                    "Präsentationspapier Matt (com.hp-matte-presentation)",
                    "Broschürenpapier Matt (com.hp-matte-brochure)"
            };
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mediaTypes);
            spinnerMedia.setAdapter(adapter);
            spinnerMedia.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == 1) selectedMediaType = "com.hp-photographic-glossy";
                    else if (position == 2) selectedMediaType = "com.hp-photographic-inkjet";
                    else if (position == 3) selectedMediaType = "com.hp-matte-inkjet";
                    else if (position == 4) selectedMediaType = "com.hp-matte-presentation";
                    else if (position == 5) selectedMediaType = "com.hp-matte-brochure";
                    else selectedMediaType = "stationery";
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        // DPI (MaterialButtonToggleGroup)
        toggleGroupDpi = print.findViewById(R.id.toggleGroupDpi);
        if (toggleGroupDpi != null) {
            if (selectedDpi == 600) {
                toggleGroupDpi.check(R.id.btnDpi600);
            } else if (selectedDpi == 1200) {
                toggleGroupDpi.check(R.id.btnDpi1200);
            } else {
                toggleGroupDpi.check(R.id.btnDpi300);
            }
            toggleGroupDpi.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btnDpi300) {
                    selectedDpi = 300;
                } else if (checkedId == R.id.btnDpi600) {
                    selectedDpi = 600;
                } else if (checkedId == R.id.btnDpi1200) {
                    selectedDpi = 1200;
                }
            });
        }

        // Duplex (MaterialButtonToggleGroup)
        toggleGroupDuplex = print.findViewById(R.id.toggleGroupDuplex);
        if (toggleGroupDuplex != null) {
            toggleGroupDuplex.check(isDuplex ? R.id.btnDuplexOn : R.id.btnDuplexOff);
            toggleGroupDuplex.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                isDuplex = (checkedId == R.id.btnDuplexOn);
            });
        }

        Button btnPrint = print.findViewById(R.id.btnExecutePrintTab);
        if (btnPrint != null) {
            btnPrint.setOnClickListener(v -> executePrintJob());
        }
    }

    private void setupPrinterInfoTabListeners(View info) {
        View btnBack = info.findViewById(R.id.btnBackFromPrinterInfo);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> bottomNavigationView.setSelectedItemId(R.id.nav_start));
        }

        TextView tvIp = info.findViewById(R.id.tvPrinterInfoIp);
        if (tvIp != null) {
            tvIp.setText(currentPrinterIp);
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void pickDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "image/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        imagePickerLauncher.launch(intent);
    }

    private void loadImageFromUri(Uri uri) {
        bgExecutor.execute(() -> {
            try {
                String type = getContentResolver().getType(uri);
                final Bitmap loadedBitmap;
                if (type != null && type.equalsIgnoreCase("application/pdf")) {
                    isPdfFile = true;
                    loadedBitmap = renderPdfToBitmap(uri);
                } else {
                    isPdfFile = false;
                    pdfPageCount = 1;
                    
                    // 1. Nur Dimensionen auslesen (verhindert OOM bei 24-48 MP Kameras)
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream is = getContentResolver().openInputStream(uri)) {
                        BitmapFactory.decodeStream(is, null, bounds);
                    }

                    // 2. Speicherfreundlich auf maximal 1800 px für UI und Vorschau skalieren
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    int maxDim = 1800;
                    int sample = 1;
                    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
                        sample *= 2;
                    }
                    opts.inSampleSize = sample;
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

                    try (InputStream is = getContentResolver().openInputStream(uri)) {
                        loadedBitmap = BitmapFactory.decodeStream(is, null, opts);
                    }
                }

                if (loadedBitmap == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Datei konnte nicht dekodiert werden.", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 3. Vorverarbeitung asynchron im Hintergrund durchführen
                final Bitmap initialProcessed = ImageProcessor.process(loadedBitmap, currentEditSettings);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (originalBitmap != null && !originalBitmap.isRecycled() && originalBitmap != loadedBitmap) {
                        originalBitmap.recycle();
                    }
                    if (processedBitmap != null && !processedBitmap.isRecycled() && processedBitmap != initialProcessed) {
                        processedBitmap.recycle();
                    }

                    originalBitmap = loadedBitmap;
                    processedBitmap = initialProcessed;

                    if (cardPdfOptions != null) {
                        cardPdfOptions.setVisibility(isPdfFile && pdfPageCount > 1 ? View.VISIBLE : View.GONE);
                    }
                    if (tvPdfPageCount != null) {
                        tvPdfPageCount.setText(pdfPageCount + (pdfPageCount == 1 ? " Seite" : " Seiten"));
                    }
                    if (tvPrintPageBadge != null) {
                        tvPrintPageBadge.setText("1/" + pdfPageCount);
                    }
                    if (tvPrintImageName != null) {
                        tvPrintImageName.setText(isPdfFile ? "PDF-Dokument geladen" : "Foto ausgewählt");
                    }
                    if (tvPrintImageDetails != null) {
                        tvPrintImageDetails.setText(originalBitmap.getWidth() + " × " + originalBitmap.getHeight() + " px");
                    }
                    updatePreview();
                });
            } catch (Exception e) {
                Log.e(TAG, "Fehler beim Laden des Bildes: ", e);
                runOnUiThread(() -> Toast.makeText(this, "Fehler beim Laden der Datei.", Toast.LENGTH_SHORT).show());
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OOM beim Laden des Bildes: ", oom);
                System.gc();
                runOnUiThread(() -> Toast.makeText(this, "Bild zu groß für den Speicher.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private Bitmap renderPdfToBitmap(Uri uri) {
        try {
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r");
            if (fd != null) {
                PdfRenderer renderer = new PdfRenderer(fd);
                pdfPageCount = renderer.getPageCount();
                if (pdfPageCount > 0) {
                    PdfRenderer.Page page = renderer.openPage(0);
                    int width = Math.min(1600, page.getWidth() * 2);
                    int height = Math.min(2200, page.getHeight() * 2);
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

    private void updatePreview() {
        if (processedBitmap != null && printTabPreview != null) {
            currentPrintLayout.paper = selectedPaperFormat;
            printTabPreview.setContent(processedBitmap, currentPrintLayout);
        }
    }

    private void executePrintJob() {
        if (selectedImageUri == null && originalBitmap == null) {
            Toast.makeText(this, "Bitte wähle zuerst eine Datei aus.", Toast.LENGTH_SHORT).show();
            pickImage();
            return;
        }

        PrintProgressDialog progressDialog = new PrintProgressDialog(this);
        progressDialog.show(currentPrinterIp, isPdfFile ? "application/pdf" : (useJpegDirect ? "image/jpeg" : "image/pwg-raster"), selectedDpi);

        if (isPdfFile && pdfPageCount > 1) {
            // Multi-page PDF print job
            new Thread(() -> {
                try {
                    byte[] multiPagePdfBytes = DirectPdfEncoder.createMultiPagePdf(this, selectedImageUri, currentEditSettings, currentPrintLayout, applySettingsToAllPdfPages);
                    if (multiPagePdfBytes != null) {
                        DirectPrintEngine.printDocument(
                                currentPrinterIp,
                                631,
                                "application/pdf",
                                multiPagePdfBytes,
                                DirectPrintJob.resolveIppMediaName(selectedPaperFormat),
                                new DirectPrintEngine.PrintCallback() {
                                    @Override
                                    public void onProgress(int percent, String message) {
                                        runOnUiThread(() -> progressDialog.updateProgress(percent, "PDF-Druck wird gesendet ...", message));
                                    }

                                    @Override
                                    public void onSuccess(int jobId, int statusCode) {
                                        runOnUiThread(() -> {
                                            progressDialog.dismiss();
                                            new AlertDialog.Builder(MainActivity.this)
                                                    .setTitle("Druckauftrag erfolgreich")
                                                    .setMessage("PDF (" + pdfPageCount + " Seiten) wurde an den HP OfficeJet übermittelt.\nJob-ID: #" + jobId)
                                                    .setPositiveButton("OK", null)
                                                    .show();
                                        });
                                    }

                                    @Override
                                    public void onError(int statusCode, String errorMsg) {
                                        runOnUiThread(() -> {
                                            progressDialog.dismiss();
                                            new AlertDialog.Builder(MainActivity.this)
                                                    .setTitle("Druckerfehler")
                                                    .setMessage("Fehler 0x" + Integer.toHexString(statusCode).toUpperCase() + ": " + errorMsg)
                                                    .setPositiveButton("Schließen", null)
                                                    .show();
                                        });
                                    }
                                }
                        );
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Multi-page PDF Fehler: ", e);
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "PDF-Druckfehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();

        } else {
            // Single image or 1-page document
            Bitmap printBitmap = ImageProcessor.process(originalBitmap, currentEditSettings);

            DirectPrintJob.startPrint(
                    this,
                    currentPrinterIp,
                    631,
                    printBitmap,
                    selectedPaperFormat,
                    selectedMediaType,
                    selectedDpi,
                    isDuplex,
                    useJpegDirect,
                    new DirectPrintJob.JobProgressListener() {
                        @Override
                        public void onJobProgress(int percent, String message) {
                            runOnUiThread(() -> progressDialog.updateProgress(percent, "Druck wird gesendet ...", message));
                        }

                        @Override
                        public void onJobSuccess(int jobId) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Druckauftrag erfolgreich")
                                        .setMessage("Der HP OfficeJet Pro 8120e hat den Auftrag verarbeitet.\nJob-ID: #" + jobId)
                                        .setPositiveButton("OK", null)
                                        .show();
                            });
                        }

                        @Override
                        public void onJobFailed(int statusCode, String errorDescription) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Druckerfehler")
                                        .setMessage("Fehler 0x" + Integer.toHexString(statusCode).toUpperCase() + ": " + errorDescription)
                                        .setPositiveButton("Schließen", null)
                                        .show();
                            });
                        }
                    }
            );
        }
    }

    private void showPrinterConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Drucker-Konfiguration");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_printer_config, null);
        EditText etIp = dialogView.findViewById(R.id.etPrinterIp);
        RadioGroup rgFormat = dialogView.findViewById(R.id.rgFormat);

        if (etIp != null) etIp.setText(currentPrinterIp);
        if (rgFormat != null) {
            rgFormat.check(useJpegDirect ? R.id.rbFormatJpeg : R.id.rbFormatPwg);
        }

        builder.setView(dialogView);
        builder.setPositiveButton("Speichern", (dialog, which) -> {
            if (etIp != null) {
                String newIp = etIp.getText().toString().trim();
                if (!newIp.isEmpty()) {
                    currentPrinterIp = newIp;
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_PRINTER_IP, currentPrinterIp).apply();
                }
            }
            if (rgFormat != null) {
                useJpegDirect = (rgFormat.getCheckedRadioButtonId() == R.id.rbFormatJpeg);
            }
            Toast.makeText(this, "Einstellungen gespeichert!", Toast.LENGTH_SHORT).show();
            setupPrinterInfoTabListeners(viewPrinterInfo);
        });
        builder.setNegativeButton("Abbrechen", null);
        builder.show();
    }
}
