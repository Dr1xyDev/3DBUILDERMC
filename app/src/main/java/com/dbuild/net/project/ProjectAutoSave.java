package com.dbuild.net.project;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dbuild.net.model.Scene;

/**
 * Auto-save system for 3D Builder MC projects.
 * Uses a Handler-based periodic save mechanism that saves project data
 * to a separate autosave file first, then replaces the main file after
 * successful save. Tracks dirty state and supports forced saves.
 */
public class ProjectAutoSave {

    private static final String TAG = "ProjectAutoSave";

    /** Default auto-save interval: 60 seconds. */
    public static final long DEFAULT_INTERVAL_MS = 60_000L;

    /** Minimum auto-save interval: 10 seconds. */
    public static final long MIN_INTERVAL_MS = 10_000L;

    /** Maximum auto-save interval: 10 minutes. */
    public static final long MAX_INTERVAL_MS = 600_000L;

    /** Notification channel ID for auto-save notifications. */
    private static final String NOTIFICATION_CHANNEL_ID = "autosave_channel";

    /** Notification ID for auto-save notifications. */
    private static final int NOTIFICATION_ID = 2001;

    private final Context context;
    private final ProjectStorage storage;
    private final ProjectSerializer serializer;
    private final Handler handler;

    private String currentProjectId;
    private Scene currentScene;
    private long intervalMs;
    private boolean dirty;
    private boolean autoSaveRunning;

    private final Runnable autoSaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (autoSaveRunning && currentProjectId != null) {
                performAutoSave();
                // Schedule next auto-save
                handler.postDelayed(this, intervalMs);
            }
        }
    };

    /**
     * Constructs a ProjectAutoSave with the given context.
     *
     * @param context the application context
     */
    public ProjectAutoSave(Context context) {
        this.context = context.getApplicationContext();
        this.storage = new ProjectStorage(context);
        this.serializer = new ProjectSerializer(storage);
        this.handler = new Handler(Looper.getMainLooper());
        this.intervalMs = DEFAULT_INTERVAL_MS;
        this.dirty = false;
        this.autoSaveRunning = false;
        this.currentProjectId = null;
        this.currentScene = null;
    }

    /**
     * Constructs a ProjectAutoSave with existing storage and serializer.
     *
     * @param context    the application context
     * @param storage    the ProjectStorage instance
     * @param serializer the ProjectSerializer instance
     */
    public ProjectAutoSave(Context context, ProjectStorage storage, ProjectSerializer serializer) {
        this.context = context.getApplicationContext();
        this.storage = storage;
        this.serializer = serializer;
        this.handler = new Handler(Looper.getMainLooper());
        this.intervalMs = DEFAULT_INTERVAL_MS;
        this.dirty = false;
        this.autoSaveRunning = false;
        this.currentProjectId = null;
        this.currentScene = null;
    }

    /**
     * Starts the auto-save system for a specific project.
     * If auto-save is already running, it will be stopped and restarted
     * with the new project.
     *
     * @param projectId  the project ID to auto-save
     * @param scene      the scene reference (will be read at save time)
     * @param intervalMs the auto-save interval in milliseconds
     */
    public void startAutoSave(String projectId, Scene scene, long intervalMs) {
        if (projectId == null || projectId.isEmpty()) {
            Log.e(TAG, "Cannot start auto-save: projectId is null or empty");
            return;
        }

        // Stop any existing auto-save
        stopAutoSave();

        this.currentProjectId = projectId;
        this.currentScene = scene;
        this.intervalMs = clampInterval(intervalMs);
        this.dirty = false;
        this.autoSaveRunning = true;

        Log.i(TAG, "Auto-save started for project: " + projectId
                + " (interval: " + this.intervalMs + "ms)");

        // Schedule the first auto-save
        handler.postDelayed(autoSaveRunnable, this.intervalMs);
    }

    /**
     * Stops the auto-save system. Any pending auto-saves are cancelled.
     * Does NOT perform a final save - call forceSave() before stopping
     * if you need to save current changes.
     */
    public void stopAutoSave() {
        if (autoSaveRunning) {
            handler.removeCallbacks(autoSaveRunnable);
            autoSaveRunning = false;
            Log.i(TAG, "Auto-save stopped for project: " + currentProjectId);
        }
        currentProjectId = null;
        currentScene = null;
    }

    /**
     * Returns whether the auto-save system is currently running.
     *
     * @return true if auto-save is active
     */
    public boolean isAutoSaveRunning() {
        return autoSaveRunning;
    }

    /**
     * Changes the auto-save interval. Takes effect on the next scheduled save.
     *
     * @param intervalMs the new interval in milliseconds
     */
    public void setAutoSaveInterval(long intervalMs) {
        this.intervalMs = clampInterval(intervalMs);

        // If auto-save is running, reschedule with the new interval
        if (autoSaveRunning) {
            handler.removeCallbacks(autoSaveRunnable);
            handler.postDelayed(autoSaveRunnable, this.intervalMs);
            Log.i(TAG, "Auto-save interval updated to: " + this.intervalMs + "ms");
        }
    }

    /**
     * Returns the current auto-save interval.
     *
     * @return interval in milliseconds
     */
    public long getAutoSaveInterval() {
        return intervalMs;
    }

    /**
     * Marks the project as having unsaved changes.
     * The next auto-save cycle will save the data.
     */
    public void setDirty() {
        this.dirty = true;
    }

    /**
     * Clears the dirty flag, indicating all changes have been saved.
     */
    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * Checks whether there are unsaved changes.
     *
     * @return true if there are unsaved changes
     */
    public boolean checkUnsavedChanges() {
        return dirty;
    }

    /**
     * Forces an immediate save of the current project data.
     * Saves to the autosave file first, then replaces the main file.
     *
     * @return true if the save succeeded
     */
    public boolean forceSave() {
        if (currentProjectId == null) {
            Log.w(TAG, "Cannot force save: no project is currently being tracked");
            return false;
        }

        Log.i(TAG, "Force saving project: " + currentProjectId);
        return performSave();
    }

    /**
     * Checks if autosave data exists for a project.
     *
     * @param projectId the project ID
     * @return true if autosave data exists
     */
    public boolean hasAutoSaveData(String projectId) {
        return storage.hasAutoSaveData(projectId);
    }

    /**
     * Attempts to recover a project from its autosave data.
     * This should be called if the main project file is corrupted.
     *
     * @param projectId the project ID to recover
     * @return true if recovery was successful
     */
    public boolean recoverFromAutoSave(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            Log.e(TAG, "Cannot recover: projectId is null or empty");
            return false;
        }

        Log.i(TAG, "Attempting to recover project from autosave: " + projectId);

        // Load the autosave data
        ProjectSerializer.ProjectData autoSaveData = serializer.loadAutoSave(projectId);
        if (autoSaveData == null) {
            Log.e(TAG, "No autosave data found for project: " + projectId);
            return false;
        }

        // Save the autosave data as the main project files
        boolean saved = serializer.saveProject(projectId,
                autoSaveData.getScene(),
                autoSaveData.getMetadata());

        if (saved) {
            // Delete the autosave file after successful recovery
            storage.deleteAutoSave(projectId);
            Log.i(TAG, "Project recovered successfully from autosave: " + projectId);
        } else {
            Log.e(TAG, "Failed to save recovered project data: " + projectId);
        }

        return saved;
    }

    /**
     * Returns the current project ID being tracked by auto-save.
     *
     * @return the project ID, or null if not running
     */
    public String getCurrentProjectId() {
        return currentProjectId;
    }

    /**
     * Returns the timestamp of the last autosave file for a project.
     *
     * @param projectId the project ID
     * @return the last modified timestamp, or 0 if no autosave exists
     */
    public long getAutoSaveTimestamp(String projectId) {
        java.io.File autoSaveFile = storage.getAutoSaveFile(projectId);
        if (autoSaveFile.exists()) {
            return autoSaveFile.lastModified();
        }
        return 0;
    }

    // =========================================================================
    // Private Methods
    // =========================================================================

    /**
     * Performs the periodic auto-save. Only saves if there are dirty changes.
     */
    private void performAutoSave() {
        if (currentProjectId == null) {
            return;
        }

        // Only save if there are unsaved changes
        if (!dirty) {
            Log.d(TAG, "Auto-save skipped: no dirty changes for project: " + currentProjectId);
            return;
        }

        Log.i(TAG, "Performing auto-save for project: " + currentProjectId);
        boolean success = performSave();

        if (success) {
            dirty = false;
            Log.i(TAG, "Auto-save completed successfully for project: " + currentProjectId);
        } else {
            Log.e(TAG, "Auto-save failed for project: " + currentProjectId);
        }
    }

    /**
     * Performs the actual save operation.
     * First saves to the autosave file, then replaces the main project files.
     *
     * @return true if the save succeeded
     */
    private boolean performSave() {
        if (currentProjectId == null || currentScene == null) {
            return false;
        }

        // Step 1: Load or create metadata
        ProjectMetadata metadata = serializer.loadMetadata(currentProjectId);
        if (metadata == null) {
            metadata = ProjectMetadata.createNew("Auto-saved Project");
            metadata.setProjectId(currentProjectId);
        }

        // Step 2: Save to autosave file first
        boolean autoSaveSuccess = serializer.saveAutoSave(currentProjectId, currentScene, metadata);
        if (!autoSaveSuccess) {
            Log.e(TAG, "Failed to write autosave file for project: " + currentProjectId);
            return false;
        }

        // Step 3: Now save to the main project files
        boolean mainSaveSuccess = serializer.saveProject(currentProjectId, currentScene, metadata);
        if (!mainSaveSuccess) {
            Log.e(TAG, "Autosave file written, but main project save failed for: " + currentProjectId);
            // Don't return false here - the autosave file is valid and can be used for recovery
        }

        // Step 4: Show notification
        showAutoSaveNotification();

        return true;
    }

    /**
     * Clamps the auto-save interval to valid bounds.
     */
    private long clampInterval(long intervalMs) {
        if (intervalMs < MIN_INTERVAL_MS) {
            return MIN_INTERVAL_MS;
        }
        if (intervalMs > MAX_INTERVAL_MS) {
            return MAX_INTERVAL_MS;
        }
        return intervalMs;
    }

    /**
     * Shows a brief notification indicating that an auto-save occurred.
     */
    private void showAutoSaveNotification() {
        try {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) {
                return;
            }

            // Create notification channel for Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Auto-Save",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Auto-save notifications");
                channel.setShowBadge(false);
                notificationManager.createNotificationChannel(channel);
            }

            // Build the notification
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }

            builder.setSmallIcon(android.R.drawable.ic_menu_save)
                    .setContentTitle("3D Builder MC")
                    .setContentText("Project auto-saved")
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(Notification.PRIORITY_LOW);

            notificationManager.notify(NOTIFICATION_ID, builder.build());

            // Cancel the notification after a brief delay
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        notificationManager.cancel(NOTIFICATION_ID);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to cancel auto-save notification", e);
                    }
                }
            }, 3000);

        } catch (Exception e) {
            Log.w(TAG, "Failed to show auto-save notification", e);
        }
    }

    /**
     * Returns the ProjectStorage instance used by this auto-save system.
     */
    public ProjectStorage getStorage() {
        return storage;
    }

    /**
     * Returns the ProjectSerializer instance used by this auto-save system.
     */
    public ProjectSerializer getSerializer() {
        return serializer;
    }
}
