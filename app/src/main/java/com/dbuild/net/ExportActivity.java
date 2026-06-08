package com.dbuild.net;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExportActivity extends AppCompatActivity {

    private static final int REQUEST_CHOOSE_DIRECTORY = 4001;

    private String projectId;
    private String exportType = "block";

    private LinearLayout cardGlb;
    private LinearLayout cardGltf;
    private LinearLayout cardObj;
    private LinearLayout cardM3x;
    private CheckBox checkboxIncludeTextures;
    private CheckBox checkboxIncludeMaterials;
    private EditText editScale;
    private LinearLayout layoutExportSettings;
    private ProgressBar progressBar;
    private TextView textProgress;
    private TextView textExportDestination;
    private LinearLayout layoutStartExport;

    private String selectedFormat = "glb";
    private Uri exportDirectoryUri;
    private String exportFileName;

    private ExecutorService exportExecutor;
    private Handler mainHandler;
    private boolean isExporting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_export);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.export_title);
        }

        projectId = getIntent().getStringExtra("project_id");
        exportType = getIntent().getStringExtra("export_type");
        if (exportType == null) exportType = "project";

        exportExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupFormatCards();
        setupExportButton();
        setupDestinationButton();

        if (projectId == null) {
            Toast.makeText(this, R.string.no_project_selected, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        cardGlb = findViewById(R.id.card_glb);
        cardGltf = findViewById(R.id.card_gltf);
        cardObj = findViewById(R.id.card_obj);
        cardM3x = findViewById(R.id.card_m3x);
        checkboxIncludeTextures = findViewById(R.id.checkbox_include_textures);
        checkboxIncludeMaterials = findViewById(R.id.checkbox_include_materials);
        editScale = findViewById(R.id.edit_scale);
        layoutExportSettings = findViewById(R.id.layout_export_settings);
        progressBar = findViewById(R.id.progress_bar);
        textProgress = findViewById(R.id.text_progress);
        textExportDestination = findViewById(R.id.text_export_destination);
        layoutStartExport = findViewById(R.id.layout_start_export);

        editScale.setText("1.0");
        updateFormatSelection();
    }

    private void setupFormatCards() {
        cardGlb.setOnClickListener(v -> {
            selectedFormat = "glb";
            updateFormatSelection();
        });

        cardGltf.setOnClickListener(v -> {
            selectedFormat = "gltf";
            updateFormatSelection();
        });

        cardObj.setOnClickListener(v -> {
            selectedFormat = "obj";
            updateFormatSelection();
        });

        cardM3x.setOnClickListener(v -> {
            selectedFormat = "m3x";
            updateFormatSelection();
        });
    }

    private void updateFormatSelection() {
        float alphaSelected = 1.0f;
        float alphaNotSelected = 0.4f;

        cardGlb.setAlpha("glb".equals(selectedFormat) ? alphaSelected : alphaNotSelected);
        cardGltf.setAlpha("gltf".equals(selectedFormat) ? alphaSelected : alphaNotSelected);
        cardObj.setAlpha("obj".equals(selectedFormat) ? alphaSelected : alphaNotSelected);
        cardM3x.setAlpha("m3x".equals(selectedFormat) ? alphaSelected : alphaNotSelected);

        cardGlb.setSelected("glb".equals(selectedFormat));
        cardGltf.setSelected("gltf".equals(selectedFormat));
        cardObj.setSelected("obj".equals(selectedFormat));
        cardM3x.setSelected("m3x".equals(selectedFormat));
    }

    private void setupDestinationButton() {
        textExportDestination.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CHOOSE_DIRECTORY);
        });
    }

    private void setupExportButton() {
        layoutStartExport.setOnClickListener(v -> {
            if (isExporting) {
                Toast.makeText(this, R.string.export_already_running, Toast.LENGTH_SHORT).show();
                return;
            }
            if (exportDirectoryUri == null) {
                Toast.makeText(this, R.string.select_export_directory, Toast.LENGTH_SHORT).show();
                return;
            }
            startExport();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHOOSE_DIRECTORY && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                exportDirectoryUri = treeUri;
                getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                textExportDestination.setText(R.string.directory_selected);
            }
        }
    }

    private void startExport() {
        isExporting = true;
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        textProgress.setVisibility(View.VISIBLE);
        textProgress.setText(R.string.preparing_export);
        layoutStartExport.setEnabled(false);

        boolean includeTextures = checkboxIncludeTextures.isChecked();
        boolean includeMaterials = checkboxIncludeMaterials.isChecked();
        float scale;
        try {
            scale = Float.parseFloat(editScale.getText().toString().trim());
        } catch (NumberFormatException e) {
            scale = 1.0f;
        }
        if (scale <= 0) scale = 1.0f;

        ProjectManager projectManager = ProjectManager.getInstance(this);
        ProjectInfo projectInfo = projectManager.getProjectById(projectId);
        if (projectInfo == null) {
            onExportError(getString(R.string.project_not_found));
            return;
        }

        String baseName = projectInfo.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        String extension = getExtensionForFormat(selectedFormat);
        exportFileName = baseName + "." + extension;

        final float finalScale = scale;
        exportExecutor.execute(() -> {
            try {
                performExport(projectId, selectedFormat, includeTextures,
                        includeMaterials, finalScale, exportFileName);
            } catch (Exception e) {
                mainHandler.post(() -> onExportError(e.getMessage()));
            }
        });
    }

    private void performExport(String projId, String format, boolean includeTextures,
                              boolean includeMaterials, float scale, String fileName) throws Exception {
        File tempOutputDir = new File(getCacheDir(), "export_temp");
        if (!tempOutputDir.exists()) {
            tempOutputDir.mkdirs();
        }
        File tempOutputFile = new File(tempOutputDir, fileName);

        mainHandler.post(() -> {
            progressBar.setProgress(10);
            textProgress.setText(getString(R.string.exporting_format, format.toUpperCase(Locale.getDefault())));
        });

        Thread.sleep(300);

        boolean success;
        switch (format) {
            case "glb":
                success = exportGlb(projId, tempOutputFile, includeTextures, includeMaterials, scale);
                break;
            case "gltf":
                success = exportGltf(projId, tempOutputFile, includeTextures, includeMaterials, scale);
                break;
            case "obj":
                success = exportObj(projId, tempOutputFile, includeTextures, includeMaterials, scale);
                break;
            case "m3x":
                success = exportM3x(projId, tempOutputFile, includeTextures, includeMaterials, scale);
                break;
            default:
                success = false;
                break;
        }

        if (!success) {
            mainHandler.post(() -> onExportError(getString(R.string.export_format_failed, format)));
            return;
        }

        mainHandler.post(() -> {
            progressBar.setProgress(70);
            textProgress.setText(R.string.copying_to_destination);
        });

        Thread.sleep(200);

        copyFileToDestination(tempOutputFile, fileName);

        mainHandler.post(() -> {
            progressBar.setProgress(100);
            textProgress.setText(R.string.export_complete);
            onExportComplete(tempOutputFile, fileName);
        });
    }

    private boolean exportGlb(String projId, File outputFile, boolean includeTextures,
                              boolean includeMaterials, float scale) {
        RustBridge bridge = RustBridge.getInstance(this);
        NativeExporter exporter = bridge.getNativeExporter();
        int result = exporter.exportGLB(projId, outputFile.getAbsolutePath(),
                includeTextures, includeMaterials, scale);
        mainHandler.post(() -> progressBar.setProgress(40));
        return result == 0;
    }

    private boolean exportGltf(String projId, File outputFile, boolean includeTextures,
                               boolean includeMaterials, float scale) {
        RustBridge bridge = RustBridge.getInstance(this);
        NativeExporter exporter = bridge.getNativeExporter();
        int result = exporter.exportGLTF(projId, outputFile.getAbsolutePath(),
                includeTextures, includeMaterials, scale);
        mainHandler.post(() -> progressBar.setProgress(40));
        return result == 0;
    }

    private boolean exportObj(String projId, File outputFile, boolean includeTextures,
                              boolean includeMaterials, float scale) {
        OBJExporter exporter = new OBJExporter(this);
        boolean result = exporter.export(projId, outputFile, includeTextures,
                includeMaterials, scale);
        mainHandler.post(() -> progressBar.setProgress(40));
        return result;
    }

    private boolean exportM3x(String projId, File outputFile, boolean includeTextures,
                              boolean includeMaterials, float scale) {
        M3XExporter exporter = new M3XExporter(this);
        boolean result = exporter.export(projId, outputFile, includeTextures,
                includeMaterials, scale);
        mainHandler.post(() -> progressBar.setProgress(40));
        return result;
    }

    private void copyFileToDestination(File sourceFile, String fileName) throws Exception {
        if (exportDirectoryUri == null) {
            throw new Exception("No export directory selected");
        }

        Uri destUri = Uri.parse(exportDirectoryUri.toString() + "/" + fileName);
        OutputStream outputStream = getContentResolver().openOutputStream(destUri);
        if (outputStream == null) {
            throw new Exception("Cannot open destination for writing");
        }

        FileInputStream inputStream = new FileInputStream(sourceFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();

        sourceFile.delete();
    }

    private void onExportComplete(File outputFile, String fileName) {
        isExporting = false;
        layoutStartExport.setEnabled(true);

        Toast.makeText(this, getString(R.string.export_success, fileName), Toast.LENGTH_LONG).show();

        showShareOption(outputFile, fileName);
    }

    private void onExportError(String errorMessage) {
        isExporting = false;
        layoutStartExport.setEnabled(true);
        progressBar.setVisibility(View.GONE);
        textProgress.setText(getString(R.string.export_error, errorMessage));
        Toast.makeText(this, getString(R.string.export_error, errorMessage), Toast.LENGTH_LONG).show();
    }

    private void showShareOption(File file, String fileName) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.export_complete)
                .setMessage(R.string.share_exported_file)
                .setPositiveButton(R.string.share, (dialog, which) -> {
                    Uri fileUri = FileProvider.getUriForFile(this,
                            getPackageName() + ".fileprovider", file);
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType(getMimeTypeForFormat(selectedFormat));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent,
                            getString(R.string.share_via)));
                })
                .setNegativeButton(R.string.done, (dialog, which) -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private String getExtensionForFormat(String format) {
        switch (format) {
            case "gltf": return "gltf";
            case "obj": return "obj";
            case "m3x": return "m3x";
            default: return "glb";
        }
    }

    private String getMimeTypeForFormat(String format) {
        switch (format) {
            case "gltf": return "model/gltf+json";
            case "obj": return "model/obj";
            case "m3x": return "application/octet-stream";
            default: return "model/gltf-binary";
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (isExporting) {
                Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show();
                return true;
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exportExecutor != null && !exportExecutor.isShutdown()) {
            exportExecutor.shutdownNow();
        }
    }

    @Override
    public void onBackPressed() {
        if (isExporting) {
            Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }
}
