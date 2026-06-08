package com.dbuild.net;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectShareActivity extends AppCompatActivity {

    private static final int REQUEST_SAVE_FILE = 6001;
    private static final int REQUEST_SAVE_SCREENSHOT = 6002;

    private String projectId;
    private ProjectInfo projectInfo;
    private ProjectManager projectManager;
    private ProjectSerializer projectSerializer;
    private ExecutorService shareExecutor;
    private Handler mainHandler;

    private ImageView imagePreview;
    private TextView textProjectName;
    private TextView textProjectDetails;
    private ProgressBar progressBar;
    private TextView textProgress;
    private LinearLayout layoutShareOptions;

    private LinearLayout cardShareFile;
    private LinearLayout cardShareScreenshot;
    private LinearLayout cardShareBlockList;

    private File shareableM3xFile;
    private File screenshotFile;
    private File blockListFile;

    private boolean isPreparing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_project_share);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.share_project_title);
        }

        projectId = getIntent().getStringExtra("project_id");
        if (projectId == null) {
            Toast.makeText(this, R.string.no_project_selected, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        projectManager = ProjectManager.getInstance(this);
        projectSerializer = new ProjectSerializer(this);
        shareExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        projectInfo = projectManager.getProjectById(projectId);
        if (projectInfo == null) {
            Toast.makeText(this, R.string.project_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupShareCards();
        displayProjectInfo();
        prepareShareFiles();
    }

    private void initViews() {
        imagePreview = findViewById(R.id.image_project_preview);
        textProjectName = findViewById(R.id.text_project_name);
        textProjectDetails = findViewById(R.id.text_project_details);
        progressBar = findViewById(R.id.progress_bar);
        textProgress = findViewById(R.id.text_progress);
        layoutShareOptions = findViewById(R.id.layout_share_options);
        cardShareFile = findViewById(R.id.card_share_file);
        cardShareScreenshot = findViewById(R.id.card_share_screenshot);
        cardShareBlockList = findViewById(R.id.card_share_block_list);
    }

    private void setupShareCards() {
        cardShareFile.setOnClickListener(v -> shareAsFile());
        cardShareScreenshot.setOnClickListener(v -> shareAsScreenshot());
        cardShareBlockList.setOnClickListener(v -> shareAsBlockList());
    }

    private void displayProjectInfo() {
        textProjectName.setText(projectInfo.getName());

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String lastModified = dateFormat.format(new Date(projectInfo.getLastModified()));
        String details = getString(R.string.project_share_details,
                projectInfo.getBlockCount(), lastModified);
        textProjectDetails.setText(details);

        Bitmap thumbnail = projectInfo.getThumbnail();
        if (thumbnail != null) {
            imagePreview.setImageBitmap(thumbnail);
        } else {
            imagePreview.setImageResource(R.drawable.ic_project_placeholder);
        }

        layoutShareOptions.setVisibility(View.GONE);
    }

    private void prepareShareFiles() {
        isPreparing = true;
        progressBar.setVisibility(View.VISIBLE);
        textProgress.setVisibility(View.VISIBLE);
        textProgress.setText(R.string.preparing_share);
        layoutShareOptions.setVisibility(View.GONE);

        shareExecutor.execute(() -> {
            try {
                prepareM3xFile();
                mainHandler.post(() -> progressBar.setProgress(33));

                prepareScreenshotFile();
                mainHandler.post(() -> progressBar.setProgress(66));

                prepareBlockListFile();
                mainHandler.post(() -> progressBar.setProgress(100));

                mainHandler.post(() -> {
                    isPreparing = false;
                    progressBar.setVisibility(View.GONE);
                    textProgress.setVisibility(View.GONE);
                    layoutShareOptions.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isPreparing = false;
                    progressBar.setVisibility(View.GONE);
                    textProgress.setText(getString(R.string.prepare_error, e.getMessage()));
                    layoutShareOptions.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.prepare_share_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void prepareM3xFile() throws Exception {
        File shareDir = new File(getCacheDir(), "share");
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        String safeName = projectInfo.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        shareableM3xFile = new File(shareDir, safeName + ".m3x");
        projectSerializer.serialize(projectId, shareableM3xFile);
    }

    private void prepareScreenshotFile() throws Exception {
        File shareDir = new File(getCacheDir(), "share");
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        String safeName = projectInfo.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        screenshotFile = new File(shareDir, safeName + "_screenshot.png");

        Bitmap thumbnail = projectInfo.getThumbnail();
        if (thumbnail != null) {
            Bitmap screenshot = generateProjectScreenshot(thumbnail);
            FileOutputStream fos = new FileOutputStream(screenshotFile);
            screenshot.compress(Bitmap.CompressFormat.PNG, 95, fos);
            fos.flush();
            fos.close();
        } else {
            Bitmap placeholder = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            placeholder.eraseColor(android.graphics.Color.DKGRAY);
            FileOutputStream fos = new FileOutputStream(screenshotFile);
            placeholder.compress(Bitmap.CompressFormat.PNG, 95, fos);
            fos.flush();
            fos.close();
        }
    }

    private Bitmap generateProjectScreenshot(Bitmap source) {
        int targetSize = 1024;
        float scale = Math.max((float) targetSize / source.getWidth(),
                (float) targetSize / source.getHeight());
        int scaledWidth = Math.round(source.getWidth() * scale);
        int scaledHeight = Math.round(source.getHeight() * scale);
        Bitmap scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);

        Bitmap screenshot = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(screenshot);
        canvas.drawColor(android.graphics.Color.DKGRAY);

        int left = (targetSize - scaledWidth) / 2;
        int top = (targetSize - scaledHeight) / 2;
        canvas.drawBitmap(scaled, left, top, null);

        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setColor(android.graphics.Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setAntiAlias(true);
        canvas.drawText(projectInfo.getName(), 32, targetSize - 32, textPaint);

        return screenshot;
    }

    private void prepareBlockListFile() throws Exception {
        File shareDir = new File(getCacheDir(), "share");
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        String safeName = projectInfo.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        blockListFile = new File(shareDir, safeName + "_blocks.txt");

        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(projectInfo.getName()).append("\n");
        sb.append("Blocks: ").append(projectInfo.getBlockCount()).append("\n");
        sb.append("Date: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm",
                Locale.getDefault()).format(new Date())).append("\n");
        sb.append("\n--- Block List ---\n");

        java.util.List<BlockInfo> blocks = projectManager.getProjectBlocks(projectId);
        if (blocks != null) {
            int index = 1;
            for (BlockInfo block : blocks) {
                sb.append(index++).append(". ").append(block.getName())
                        .append(" (").append(block.getType()).append(")")
                        .append(" at [").append(block.getX()).append(", ")
                        .append(block.getY()).append(", ").append(block.getZ()).append("]\n");
            }
        } else {
            sb.append("No blocks found.\n");
        }

        FileOutputStream fos = new FileOutputStream(blockListFile);
        fos.write(sb.toString().getBytes("UTF-8"));
        fos.flush();
        fos.close();
    }

    private void shareAsFile() {
        if (shareableM3xFile == null || !shareableM3xFile.exists()) {
            Toast.makeText(this, R.string.share_file_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/octet-stream");
        Uri fileUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", shareableM3xFile);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.share_subject_file, projectInfo.getName()));
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                getString(R.string.share_text_file, projectInfo.getName()));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(shareIntent, getString(R.string.share_via));
        startActivity(chooser);
    }

    private void shareAsScreenshot() {
        if (screenshotFile == null || !screenshotFile.exists()) {
            Toast.makeText(this, R.string.screenshot_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        Uri fileUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", screenshotFile);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.share_subject_screenshot, projectInfo.getName()));
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                getString(R.string.share_text_screenshot, projectInfo.getName()));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(shareIntent, getString(R.string.share_via));
        startActivity(chooser);
    }

    private void shareAsBlockList() {
        if (blockListFile == null || !blockListFile.exists()) {
            Toast.makeText(this, R.string.block_list_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        Uri fileUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", blockListFile);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.share_subject_blocks, projectInfo.getName()));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(shareIntent, getString(R.string.share_via));
        startActivity(chooser);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shareExecutor != null && !shareExecutor.isShutdown()) {
            shareExecutor.shutdownNow();
        }
        cleanupShareFiles();
    }

    private void cleanupShareFiles() {
        File shareDir = new File(getCacheDir(), "share");
        if (shareDir.exists()) {
            File[] files = shareDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            shareDir.delete();
        }
    }
}
