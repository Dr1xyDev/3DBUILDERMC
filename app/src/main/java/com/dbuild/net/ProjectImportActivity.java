package com.dbuild.net;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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

public class ProjectImportActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_FILE = 5001;
    private static final String M3X_EXTENSION = ".m3x";
    private static final int M3X_MAGIC_NUMBER = 0x4D335830;

    private LinearLayout layoutSelectFile;
    private LinearLayout layoutPreview;
    private LinearLayout layoutImporting;
    private LinearLayout layoutComplete;

    private TextView textFileName;
    private TextView textProjectName;
    private TextView textProjectDescription;
    private TextView textProjectBlockCount;
    private EditText editProjectName;
    private ProgressBar progressBar;
    private TextView textProgress;
    private Button btnSelectFile;
    private Button btnImport;
    private Button btnCancel;
    private Button btnDone;

    private ProjectLoader projectLoader;
    private ProjectStorage projectStorage;
    private ProjectManager projectManager;
    private ExecutorService importExecutor;
    private Handler mainHandler;

    private Uri selectedFileUri;
    private String selectedFileName;
    private ProjectImportInfo importInfo;
    private String importedProjectId;
    private boolean isImporting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_project_import);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.import_project_title);
        }

        projectLoader = new ProjectLoader(this);
        projectStorage = new ProjectStorage(this);
        projectManager = ProjectManager.getInstance(this);
        importExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupButtons();
        showStateSelectFile();
    }

    private void initViews() {
        layoutSelectFile = findViewById(R.id.layout_select_file);
        layoutPreview = findViewById(R.id.layout_preview);
        layoutImporting = findViewById(R.id.layout_importing);
        layoutComplete = findViewById(R.id.layout_complete);

        textFileName = findViewById(R.id.text_file_name);
        textProjectName = findViewById(R.id.text_project_name);
        textProjectDescription = findViewById(R.id.text_project_description);
        textProjectBlockCount = findViewById(R.id.text_project_block_count);
        editProjectName = findViewById(R.id.edit_project_name);
        progressBar = findViewById(R.id.progress_bar);
        textProgress = findViewById(R.id.text_progress);
        btnSelectFile = findViewById(R.id.btn_select_file);
        btnImport = findViewById(R.id.btn_import);
        btnCancel = findViewById(R.id.btn_cancel);
        btnDone = findViewById(R.id.btn_done);
    }

    private void setupButtons() {
        btnSelectFile.setOnClickListener(v -> openFilePicker());
        btnImport.setOnClickListener(v -> startImport());
        btnCancel.setOnClickListener(v -> {
            if (!isImporting) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        btnDone.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra("project_id", importedProjectId);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void showStateSelectFile() {
        layoutSelectFile.setVisibility(View.VISIBLE);
        layoutPreview.setVisibility(View.GONE);
        layoutImporting.setVisibility(View.GONE);
        layoutComplete.setVisibility(View.GONE);
    }

    private void showStatePreview() {
        layoutSelectFile.setVisibility(View.GONE);
        layoutPreview.setVisibility(View.VISIBLE);
        layoutImporting.setVisibility(View.GONE);
        layoutComplete.setVisibility(View.GONE);
    }

    private void showStateImporting() {
        layoutSelectFile.setVisibility(View.GONE);
        layoutPreview.setVisibility(View.GONE);
        layoutImporting.setVisibility(View.VISIBLE);
        layoutComplete.setVisibility(View.GONE);
    }

    private void showStateComplete() {
        layoutSelectFile.setVisibility(View.GONE);
        layoutPreview.setVisibility(View.GONE);
        layoutImporting.setVisibility(View.GONE);
        layoutComplete.setVisibility(View.VISIBLE);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "*/*"});
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_m3x_file)),
                REQUEST_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedFileUri = uri;
                selectedFileName = getFileNameFromUri(uri);
                textFileName.setText(selectedFileName);
                validateAndPreview(uri);
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "unknown.m3x";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return fileName;
    }

    private void validateAndPreview(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                showError(getString(R.string.cannot_open_file));
                return;
            }

            byte[] headerBytes = new byte[4];
            int bytesRead = inputStream.read(headerBytes);
            inputStream.close();

            if (bytesRead < 4) {
                showError(getString(R.string.invalid_file_format));
                return;
            }

            int magic = ((headerBytes[0] & 0xFF) << 24) |
                    ((headerBytes[1] & 0xFF) << 16) |
                    ((headerBytes[2] & 0xFF) << 8) |
                    (headerBytes[3] & 0xFF);

            if (magic != M3X_MAGIC_NUMBER) {
                showError(getString(R.string.invalid_m3x_format));
                return;
            }

            importInfo = projectLoader.loadImportInfo(uri);
            if (importInfo == null) {
                showError(getString(R.string.cannot_read_project_info));
                return;
            }

            displayPreview();
        } catch (Exception e) {
            showError(getString(R.string.file_read_error, e.getMessage()));
        }
    }

    private void displayPreview() {
        String name = importInfo.getName();
        if (name == null || name.isEmpty()) {
            int dotIndex = selectedFileName.lastIndexOf('.');
            name = dotIndex > 0 ? selectedFileName.substring(0, dotIndex) : selectedFileName;
        }

        textProjectName.setText(name);
        editProjectName.setText(name);

        String description = importInfo.getDescription();
        textProjectDescription.setText(description != null && !description.isEmpty() ?
                description : getString(R.string.no_description));

        textProjectBlockCount.setText(getString(R.string.block_count_format,
                importInfo.getBlockCount()));

        showStatePreview();
    }

    private void startImport() {
        if (isImporting) {
            return;
        }

        String projectName = editProjectName.getText().toString().trim();
        if (projectName.isEmpty()) {
            Toast.makeText(this, R.string.project_name_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        projectName = resolveDuplicateName(projectName);

        isImporting = true;
        showStateImporting();
        progressBar.setProgress(0);
        textProgress.setText(R.string.importing_project);

        final String finalProjectName = projectName;
        importExecutor.execute(() -> {
            try {
                performImport(finalProjectName);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isImporting = false;
                    showError(getString(R.string.import_error, e.getMessage()));
                });
            }
        });
    }

    private String resolveDuplicateName(String baseName) {
        List<ProjectInfo> existingProjects = projectManager.getAllProjects();
        if (existingProjects == null) return baseName;

        boolean nameExists = false;
        for (ProjectInfo project : existingProjects) {
            if (project.getName().equals(baseName)) {
                nameExists = true;
                break;
            }
        }

        if (!nameExists) return baseName;

        int counter = 1;
        String newName;
        do {
            newName = baseName + " (" + counter + ")";
            boolean found = false;
            for (ProjectInfo project : existingProjects) {
                if (project.getName().equals(newName)) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
            counter++;
        } while (true);

        return newName;
    }

    private void performImport(String projectName) throws Exception {
        mainHandler.post(() -> {
            progressBar.setProgress(20);
            textProgress.setText(R.string.copying_file);
        });

        File tempFile = copyToTemp(selectedFileUri, selectedFileName);

        mainHandler.post(() -> {
            progressBar.setProgress(50);
            textProgress.setText(R.string.processing_project);
        });

        Thread.sleep(200);

        File projectDir = projectStorage.getProjectDirectory();
        if (!projectDir.exists()) {
            projectDir.mkdirs();
        }

        String destFileName = projectName.replaceAll("[^a-zA-Z0-9_-]", "_") + M3X_EXTENSION;
        File destFile = new File(projectDir, destFileName);

        copyFile(tempFile, destFile);
        tempFile.delete();

        mainHandler.post(() -> {
            progressBar.setProgress(75);
            textProgress.setText(R.string.loading_project);
        });

        Thread.sleep(100);

        ProjectInfo projectInfo = projectLoader.loadProject(destFile.getAbsolutePath(), projectName);
        if (projectInfo == null) {
            throw new Exception("Failed to load imported project");
        }

        importedProjectId = projectManager.addImportedProject(projectInfo);

        mainHandler.post(() -> {
            progressBar.setProgress(100);
            textProgress.setText(R.string.import_complete);
        });

        mainHandler.postDelayed(() -> {
            isImporting = false;
            showStateComplete();
            Toast.makeText(this, getString(R.string.project_imported, projectName),
                    Toast.LENGTH_LONG).show();
        }, 500);
    }

    private File copyToTemp(Uri uri, String fileName) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new Exception("Cannot open input stream");
        }

        File tempDir = new File(getCacheDir(), "import_temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File tempFile = new File(tempDir, fileName);
        FileOutputStream outputStream = new FileOutputStream(tempFile);

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();

        return tempFile;
    }

    private void copyFile(File source, File dest) throws Exception {
        FileInputStream fis = new FileInputStream(source);
        FileOutputStream fos = new FileOutputStream(dest);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        fos.flush();
        fos.close();
        fis.close();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_error_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    showStateSelectFile();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (isImporting) {
                Toast.makeText(this, R.string.import_in_progress, Toast.LENGTH_SHORT).show();
                return true;
            }
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (importExecutor != null && !importExecutor.isShutdown()) {
            importExecutor.shutdownNow();
        }
        cleanupTempFiles();
    }

    private void cleanupTempFiles() {
        File tempDir = new File(getCacheDir(), "import_temp");
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            tempDir.delete();
        }
    }

    @Override
    public void onBackPressed() {
        if (isImporting) {
            Toast.makeText(this, R.string.import_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }
}
