package com.dbuild.net.project;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dbuild.net.model.Scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads projects asynchronously with progress tracking and cancellation support.
 * Uses background threads for I/O operations and posts results back to the main thread
 * via a Handler.
 */
public class ProjectLoader {

    private static final String TAG = "ProjectLoader";

    private final ProjectSerializer serializer;
    private final ProjectStorage storage;
    private final Handler mainHandler;

    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Thread> activeThreads = new ConcurrentLinkedQueue<>();

    /**
     * Callback interface for project loading events.
     */
    public interface ProjectLoadCallback {

        /**
         * Called when a project has been successfully loaded.
         *
         * @param data the loaded project data
         */
        void onProjectLoaded(ProjectSerializer.ProjectData data);

        /**
         * Called when an error occurs during loading.
         *
         * @param message a human-readable error message
         */
        void onError(String message);

        /**
         * Called to report loading progress.
         *
         * @param percent the progress percentage (0-100)
         */
        void onProgress(int percent);
    }

    /**
     * Callback interface for bulk metadata loading events.
     */
    public interface MetadataLoadCallback {

        /**
         * Called when all metadata has been loaded.
         *
         * @param metadataList the list of loaded metadata objects
         */
        void onMetadataLoaded(List<ProjectMetadata> metadataList);

        /**
         * Called when an error occurs during metadata loading.
         *
         * @param message a human-readable error message
         */
        void onError(String message);
    }

    /**
     * Constructs a ProjectLoader with the given context.
     *
     * @param context the application context used for storage access
     */
    public ProjectLoader(android.content.Context context) {
        this.storage = new ProjectStorage(context);
        this.serializer = new ProjectSerializer(storage);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Constructs a ProjectLoader with existing storage and serializer instances.
     *
     * @param storage    the ProjectStorage to use
     * @param serializer the ProjectSerializer to use
     */
    public ProjectLoader(ProjectStorage storage, ProjectSerializer serializer) {
        this.storage = storage;
        this.serializer = serializer;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Loads a single project asynchronously on a background thread.
     * Results are posted back to the main thread via the callback.
     *
     * @param projectId the ID of the project to load
     * @param callback  the callback to receive loading events
     */
    public void loadProjectAsync(final String projectId, final ProjectLoadCallback callback) {
        if (projectId == null || projectId.isEmpty()) {
            postError(callback, "Project ID is null or empty");
            return;
        }
        if (callback == null) {
            Log.w(TAG, "loadProjectAsync called with null callback");
            return;
        }

        cancelFlag.set(false);

        Thread loadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                activeThreads.add(Thread.currentThread());

                try {
                    // Step 1: Validate project exists (10%)
                    postProgress(callback, 10);
                    if (cancelFlag.get()) {
                        postError(callback, "Loading cancelled");
                        return;
                    }

                    if (!storage.projectExists(projectId)) {
                        postError(callback, "Project not found: " + projectId);
                        return;
                    }

                    // Step 2: Validate project structure (20%)
                    postProgress(callback, 20);
                    if (cancelFlag.get()) {
                        postError(callback, "Loading cancelled");
                        return;
                    }

                    // Step 3: Check for autosave data (30%)
                    postProgress(callback, 30);
                    if (cancelFlag.get()) {
                        postError(callback, "Loading cancelled");
                        return;
                    }

                    boolean hasAutoSave = storage.hasAutoSaveData(projectId);

                    // Step 4: Load project data (50%)
                    postProgress(callback, 50);
                    if (cancelFlag.get()) {
                        postError(callback, "Loading cancelled");
                        return;
                    }

                    ProjectSerializer.ProjectData data = serializer.loadProject(projectId);

                    // Step 5: If main load failed, try autosave (70%)
                    postProgress(callback, 70);
                    if (cancelFlag.get()) {
                        postError(callback, "Loading cancelled");
                        return;
                    }

                    if (data == null && hasAutoSave) {
                        Log.i(TAG, "Main load failed, attempting autosave recovery for: " + projectId);
                        data = serializer.loadAutoSave(projectId);
                    }

                    // Step 6: Final validation (90%)
                    postProgress(callback, 90);
                    if (cancelFlag.get()) {
                        postError(callback, "Loading cancelled");
                        return;
                    }

                    if (data == null) {
                        postError(callback, "Failed to load project: " + projectId);
                        return;
                    }

                    // Step 7: Complete (100%)
                    postProgress(callback, 100);

                    // Post result on main thread
                    final ProjectSerializer.ProjectData finalData = data;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onProjectLoaded(finalData);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error loading project: " + projectId, e);
                    postError(callback, "Error loading project: " + e.getMessage());
                } finally {
                    activeThreads.remove(Thread.currentThread());
                }
            }
        }, "ProjectLoader-" + projectId);

        loadThread.setPriority(Thread.NORM_PRIORITY - 1);
        loadThread.start();
    }

    /**
     * Loads multiple projects asynchronously with progress tracking.
     * Each project is loaded sequentially to avoid excessive I/O contention.
     *
     * @param projectIds the list of project IDs to load
     * @param callback   the callback to receive loading events
     */
    public void loadMultipleProjectsAsync(final List<String> projectIds, final ProjectLoadCallback callback) {
        if (projectIds == null || projectIds.isEmpty()) {
            postError(callback, "Project ID list is null or empty");
            return;
        }
        if (callback == null) {
            Log.w(TAG, "loadMultipleProjectsAsync called with null callback");
            return;
        }

        cancelFlag.set(false);

        Thread loadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                activeThreads.add(Thread.currentThread());

                try {
                    int total = projectIds.size();
                    for (int i = 0; i < total; i++) {
                        if (cancelFlag.get()) {
                            postError(callback, "Loading cancelled");
                            return;
                        }

                        String projectId = projectIds.get(i);
                        int progress = (int) (((i + 0.5) / total) * 100);
                        postProgress(callback, progress);

                        ProjectSerializer.ProjectData data = serializer.loadProject(projectId);
                        if (data != null) {
                            final ProjectSerializer.ProjectData finalData = data;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onProjectLoaded(finalData);
                                }
                            });
                        } else {
                            Log.w(TAG, "Failed to load project in batch: " + projectId);
                        }

                        // Update progress after loading each project
                        progress = (int) (((i + 1) / (float) total) * 100);
                        postProgress(callback, Math.min(progress, 100));
                    }

                    postProgress(callback, 100);

                } catch (Exception e) {
                    Log.e(TAG, "Error loading multiple projects", e);
                    postError(callback, "Error loading projects: " + e.getMessage());
                } finally {
                    activeThreads.remove(Thread.currentThread());
                }
            }
        }, "ProjectLoader-Batch");

        loadThread.setPriority(Thread.NORM_PRIORITY - 1);
        loadThread.start();
    }

    /**
     * Loads project metadata synchronously. This is a lightweight operation
     * that only reads the metadata.json file.
     *
     * @param projectId the project ID
     * @return the ProjectMetadata, or null if not found or on error
     */
    public ProjectMetadata loadProjectMetadata(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return null;
        }
        try {
            return serializer.loadMetadata(projectId);
        } catch (Exception e) {
            Log.e(TAG, "Error loading metadata for: " + projectId, e);
            return null;
        }
    }

    /**
     * Loads metadata for all projects synchronously.
     * Iterates through all project directories and reads their metadata.
     *
     * @return an unmodifiable list of all project metadata objects
     */
    public List<ProjectMetadata> loadAllProjectMetadata() {
        List<String> projectIds = storage.listProjectIds();
        List<ProjectMetadata> result = new ArrayList<>();

        for (String projectId : projectIds) {
            try {
                ProjectMetadata metadata = serializer.loadMetadata(projectId);
                if (metadata != null) {
                    // Update the file size to reflect current state
                    metadata.setFileSize(storage.getProjectSize(projectId));
                    result.add(metadata);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load metadata for project: " + projectId, e);
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Loads all project metadata asynchronously.
     *
     * @param callback the callback to receive the metadata list
     */
    public void loadAllProjectMetadataAsync(final MetadataLoadCallback callback) {
        if (callback == null) {
            return;
        }

        cancelFlag.set(false);

        Thread loadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                activeThreads.add(Thread.currentThread());

                try {
                    List<ProjectMetadata> result = loadAllProjectMetadata();

                    final List<ProjectMetadata> finalResult = result;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onMetadataLoaded(finalResult);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error loading all project metadata", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("Error loading projects: " + e.getMessage());
                        }
                    });
                } finally {
                    activeThreads.remove(Thread.currentThread());
                }
            }
        }, "ProjectLoader-AllMetadata");

        loadThread.setPriority(Thread.NORM_PRIORITY - 1);
        loadThread.start();
    }

    /**
     * Cancels any ongoing loading operations.
     * Already-loaded results may still be delivered to callbacks.
     */
    public void cancelLoading() {
        cancelFlag.set(true);
        Log.i(TAG, "Loading cancellation requested");

        // Interrupt active threads
        for (Thread thread : activeThreads) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }
    }

    /**
     * Returns true if loading is currently cancelled.
     *
     * @return true if the cancel flag is set
     */
    public boolean isCancelled() {
        return cancelFlag.get();
    }

    /**
     * Checks whether there are any active loading threads.
     *
     * @return true if at least one loading thread is running
     */
    public boolean isLoading() {
        return !activeThreads.isEmpty();
    }

    /**
     * Posts an error message to the callback on the main thread.
     */
    private void postError(final ProjectLoadCallback callback, final String message) {
        if (callback == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(message);
            }
        });
    }

    /**
     * Posts a progress update to the callback on the main thread.
     */
    private void postProgress(final ProjectLoadCallback callback, final int percent) {
        if (callback == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onProgress(percent);
            }
        });
    }

    /**
     * Returns the ProjectStorage instance used by this loader.
     */
    public ProjectStorage getStorage() {
        return storage;
    }

    /**
     * Returns the ProjectSerializer instance used by this loader.
     */
    public ProjectSerializer getSerializer() {
        return serializer;
    }
}
