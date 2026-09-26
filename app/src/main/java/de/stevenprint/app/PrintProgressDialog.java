package de.stevenprint.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * Modernes Fortschritts-Dialogfenster für den IPP-Druckauftrag.
 */
public class PrintProgressDialog {

    private final Context context;
    private AlertDialog dialog;
    private ProgressBar progressCircle;
    private TextView tvProgressPercent, tvProgressStatusTitle, tvProgressStatusSub;
    private TextView tvProgressPrinter, tvProgressIp, tvProgressFormat, tvProgressDpi;
    private Button btnProgressCancel;

    public PrintProgressDialog(Context context) {
        this.context = context;
        init();
    }

    private void init() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_print_progress, null);

        progressCircle = view.findViewById(R.id.progressCircle);
        tvProgressPercent = view.findViewById(R.id.tvProgressPercent);
        tvProgressStatusTitle = view.findViewById(R.id.tvProgressStatusTitle);
        tvProgressStatusSub = view.findViewById(R.id.tvProgressStatusSub);

        tvProgressPrinter = view.findViewById(R.id.tvProgressPrinter);
        tvProgressIp = view.findViewById(R.id.tvProgressIp);
        tvProgressFormat = view.findViewById(R.id.tvProgressFormat);
        tvProgressDpi = view.findViewById(R.id.tvProgressDpi);

        btnProgressCancel = view.findViewById(R.id.btnProgressCancel);
        btnProgressCancel.setOnClickListener(v -> dismiss());

        builder.setView(view);
        builder.setCancelable(false);
        dialog = builder.create();
    }

    public void show(String printerIp, String format, int dpi) {
        tvProgressPrinter.setText("HP OfficeJet Pro 8120e");
        tvProgressIp.setText(printerIp);
        tvProgressFormat.setText(format);
        tvProgressDpi.setText(dpi + " dpi");

        updateProgress(10, "Druck wird vorbereitet ...", "Verbindung wird hergestellt");
        if (dialog != null && !dialog.isShowing()) {
            dialog.show();
        }
    }

    public void updateProgress(int percent, String title, String sub) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (progressCircle != null) progressCircle.setProgress(percent);
            if (tvProgressPercent != null) tvProgressPercent.setText(percent + "%");
            if (tvProgressStatusTitle != null) tvProgressStatusTitle.setText(title);
            if (tvProgressStatusSub != null) tvProgressStatusSub.setText(sub);
        });
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
