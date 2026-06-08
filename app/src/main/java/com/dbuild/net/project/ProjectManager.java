package com.dbuild.net.project;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.dbuild.net.model.Scene;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Central manager for all project operations in 3D Builder MC.
 * Implements the Singleton pattern and coordinates between ProjectStorage,
 * ProjectSerializer, ProjectLoader, ProjectThumbnailGenerator, and ProjectAutoSave.
 * Provides thread safety through synchronized blocks where needed.
 */
public class ProjectManager {

    private static final String TAG = "ProjectManager";

    /** File provider authority for sharing. */
    private static final String FILE_PROVIDER_AUTHORITY = "com.dbuild.net.fileprovider";

    /** Maximum number of recent projects to return. */
    private static final int MAX_RECENT_PROJECTS = 20;

    private static volatile ProjectManager instance;

    private final Context context;
    private final ProjectStorage storage;
    private final ProjectSerializer serializer;
    private final ProjectLoader loader;
    private final ProjectThumbnailGenerator thumbnailGenerator;
    private final ProjectAutoSave autoSave;

    /** Lock object for thread-safe operations. */
    private final Object lock = new Object();

    /**
     * Private constructor. Use getInstance(Context) to obtain the singleton.
     */
    private ProjectManager(Context context) {
        this.context = context.getApplicationContext();
        this.storage = new ProjectStorage(this.context);
        this.serializer = new ProjectSerializer(this.storage);
        this.loader = new ProjectLoader(this.storage, this.serializer);
        this.thumbnailGenerator = new ProjectThumbnailGenerator(this.storage);
        this.autoSave = new ProjectAutoSave(this.context, this.storage, this.serializer);
    }

    /**
     * Returns the singleton instance of ProjectManager.
     *
     * @param context the application context
     * @return the ProjectManager instance
     */
    public static ProjectManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ProjectManager.class) {
                if (instance == null) {
                    instance = new ProjectManager(context);
                }
            }
        }
        return instance;
    }

    // =========================================================================
    // Project CRUD Operations
    // =========================================================================

    /**
     * Creates a new project with the given name.
     * Generates a unique project ID, creates the directory structure,
     * and saves initial metadata.
     *
     * @param name the project name
     * @return the new project's ID, or null on failure
     */
    public String createProject(String name) {
        synchronized (lock) {
            if (name == null || name.trim().isEmpty()) {
                Log.e(TAG, "Cannot create project: name is null or empty");
                return null;
            }

            String projectId = UUID.randomUUID().toString();
            ProjectMetadata metadata = ProjectMetadata.createNew(name.trim());
            metadata.setProjectId(projectId);

            // Create directory structure
            if (!storage.createProjectStructure(projectId)) {
                Log.e(TAG, "Failed to create project structure for: " + projectId);
                return null;
            }

            // Create initial scene
            Scene scene = new Scene(name.trim());

            // Save initial data
            boolean saved = serializer.saveProject(projectId, scene, metadata);
            if (!saved) {
                Log.e(TAG, "Failed to save initial project data for: " + projectId);
                storage.deleteProject(projectId);
                return null;
            }

            // Generate and save thumbnail
            thumbnailGenerator.generateAndSave(projectId, scene);

            Log.i(TAG, "Project created: " + projectId + " (" + name + ")");
            return projectId;
        }
    }

    /**
     * Opens a project and loads its data.
     *
     * @param projectId the ID of the project to open
     * @return the ProjectData, or null on failure
     */
    public ProjectSerializer.ProjectData openProject(String projectId) {
        synchronized (lock) {
            if (projectId == null || projectId.isEmpty()) {
                Log.e(TAG, "Cannot open project: projectId is null or empty");
                return null;
            }

            if (!storage.projectExists(projectId)) {
                Log.e(TAG, "Project does not exist: " + projectId);
                return null;
            }

            // Try loading normally first
            ProjectSerializer.ProjectData data = serializer.loadProject(projectId);

            // If loading failed, try autosave recovery
            if (data == null && autoSave.hasAutoSaveData(projectId)) {
                Log.i(TAG, "Normal load failed, trying autosave recovery for: " + projectId);
                boolean recovered = autoSave.recoverFromAutoSave(projectId);
                if (recovered) {
                    data = serializer.loadProject(projectId);
                }
            }

            if (data != null) {
                Log.i(TAG, "Project opened: " + projectId);
            } else {
                Log.e(TAG, "Failed to open project: " + projectId);
            }

            return data;
        }
    }

    /**
     * Saves a project with the given scene and metadata.
     *
     * @param projectId the project ID
     * @param scene     the scene data
     * @param metadata  the project metadata
     * @return true if saved successfully
     */
    public boolean saveProject(String projectId, Scene scene, ProjectMetadata metadata) {
        synchronized (lock) {
            if (projectId == null || projectId.isEmpty()) {
                return false;
            }

            boolean saved = serializer.saveProject(projectId, scene, metadata);

            if (saved && scene != null) {
                // Update thumbnail after successful save
                try {
                    thumbnailGenerator.generateAndSave(projectId, scene);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to update thumbnail after save", e);
                }

                // Clear the autosave file since we just saved successfully
                storage.deleteAutoSave(projectId);
            }

            return saved;
        }
    }

    /**
     * Deletes a project and all its data.
     *
     * @param projectId the ID of the project to delete
     * @return true if deletion succeeded
     */
    public boolean deleteProject(String projectId) {
        synchronized (lock) {
            if (projectId == null || projectId.isEmpty()) {
                return false;
            }

            // Stop auto-save if it's running for this project
            if (projectId.equals(autoSave.getCurrentProjectId())) {
                autoSave.stopAutoSave();
            }

            // Clear thumbnail cache
            thumbnailGenerator.clearCache(projectId);

            boolean deleted = storage.deleteProject(projectId);
            if (deleted) {
                Log.i(TAG, "Project deleted: " + projectId);
            } else {
                Log.e(TAG, "Failed to delete project: " + projectId);
            }

            return deleted;
        }
    }

    /**
     * Duplicates a project with a new name.
     *
     * @param projectId the ID of the project to duplicate
     * @param newName   the name for the duplicated project
     * @return the new project's ID, or null on failure
     */
    public String duplicateProject(String projectId, String newName) {
        synchronized (lock) {
            if (projectId == null || projectId.isEmpty() || newName == null) {
                return null;
            }

            String newProjectId = UUID.randomUUID().toString();

            // Copy the project files
            boolean copied = storage.copyProject(projectId, newProjectId);
            if (!copied) {
                Log.e(TAG, "Failed to copy project files for duplication");
                return null;
            }

            // Update the metadata with the new name and ID
            ProjectMetadata metadata = serializer.loadMetadata(newProjectId);
            if (metadata != null) {
                metadata.setProjectId(newProjectId);
                metadata.setProjectName(newName);
                metadata.setCreatedAt(System.currentTimeMillis());
                metadata.setModifiedAt(System.currentTimeMillis());
                serializer.saveMetadata(newProjectId, metadata);
            } else {
                // Create new metadata if the copied one couldn't be loaded
                metadata = ProjectMetadata.createNew(newName);
                metadata.setProjectId(newProjectId);
                serializer.saveMetadata(newProjectId, metadata);
            }

            Log.i(TAG, "Project duplicated: " + projectId + " -> " + newProjectId);
            return newProjectId;
        }
    }

    /**
     * Renames a project.
     *
     * @param projectId the project ID
     * @param newName   the new name
     * @return true if renamed successfully
     */
    public boolean renameProject(String projectId, String newName) {
        synchronized (lock) {
            if (projectId == null || projectId.isEmpty() || newName == null || newName.trim().isEmpty()) {
                return false;
            }
            boolean renamed = storage.renameProject(projectId, newName.trim());
            if (renamed) {
                Log.i(TAG, "Project renamed: " + projectId + " -> " + newName);
            }
            return renamed;
        }
    }

    // =========================================================================
    // Project Query Operations
    // =========================================================================

    /**
     * Returns metadata for all projects.
     *
     * @return an unmodifiable list of all project metadata
     */
    public List<ProjectMetadata> getAllProjects() {
        synchronized (lock) {
            return loader.loadAllProjectMetadata();
        }
    }

    /**
     * Returns the total number of projects.
     *
     * @return the project count
     */
    public int getProjectCount() {
        synchronized (lock) {
            return storage.listProjectIds().size();
        }
    }

    /**
     * Searches projects by name or description.
     *
     * @param query the search query (case-insensitive)
     * @return a list of matching project metadata
     */
    public List<ProjectMetadata> searchProjects(String query) {
        synchronized (lock) {
            if (query == null || query.trim().isEmpty()) {
                return getAllProjects();
            }

            String lowerQuery = query.trim().toLowerCase();
            List<ProjectMetadata> allProjects = getAllProjects();
            List<ProjectMetadata> results = new ArrayList<>();

            for (ProjectMetadata metadata : allProjects) {
                if (metadata == null) continue;

                String name = metadata.getProjectName();
                String desc = metadata.getDescription();

                boolean matches = false;
                if (name != null && name.toLowerCase().contains(lowerQuery)) {
                    matches = true;
                }
                if (desc != null && desc.toLowerCase().contains(lowerQuery)) {
                    matches = true;
                }
                if (metadata.getProjectId() != null && metadata.getProjectId().toLowerCase().contains(lowerQuery)) {
                    matches = true;
                }

                if (matches) {
                    results.add(metadata);
                }
            }

            return results;
        }
    }

    /**
     * Sorts a list of project metadata by the specified criterion.
     *
     * @param projects the list to sort
     * @param sortBy   the sort criterion: "name", "date", or "size"
     * @return a new sorted list
     */
    public List<ProjectMetadata> sortProjects(List<ProjectMetadata> projects, String sortBy) {
        if (projects == null) {
            return Collections.emptyList();
        }

        List<ProjectMetadata> sorted = new ArrayList<>(projects);

        if ("name".equalsIgnoreCase(sortBy)) {
            Collections.sort(sorted, new Comparator<ProjectMetadata>() {
                @Override
                public int compare(ProjectMetadata a, ProjectMetadata b) {
                    String nameA = a.getProjectName() != null ? a.getProjectName() : "";
                    String nameB = b.getProjectName() != null ? b.getProjectName() : "";
                    return nameA.compareToIgnoreCase(nameB);
                }
            });
        } else if ("date".equalsIgnoreCase(sortBy)) {
            Collections.sort(sorted, new Comparator<ProjectMetadata>() {
                @Override
                public int compare(ProjectMetadata a, ProjectMetadata b) {
                    // Most recent first
                    return Long.compare(b.getModifiedAt(), a.getModifiedAt());
                }
            });
        } else if ("size".equalsIgnoreCase(sortBy)) {
            Collections.sort(sorted, new Comparator<ProjectMetadata>() {
                @Override
                public int compare(ProjectMetadata a, ProjectMetadata b) {
                    // Largest first
                    return Long.compare(b.getFileSize(), a.getFileSize());
                }
            });
        } else {
            // Default: sort by modification date (most recent first)
            Collections.sort(sorted, new Comparator<ProjectMetadata>() {
                @Override
                public int compare(ProjectMetadata a, ProjectMetadata b) {
                    return Long.compare(b.getModifiedAt(), a.getModifiedAt());
                }
            });
        }

        return sorted;
    }

    /**
     * Returns the most recently modified projects.
     *
     * @param limit the maximum number of projects to return
     * @return a list of the most recent project metadata, sorted by modification date
     */
    public List<ProjectMetadata> getRecentProjects(int limit) {
        synchronized (lock) {
            List<ProjectMetadata> allProjects = getAllProjects();
            List<ProjectMetadata> sorted = sortProjects(allProjects, "date");

            if (sorted.size() <= limit) {
                return sorted;
            }
            return new ArrayList<>(sorted.subList(0, limit));
        }
    }

    /**
     * Returns the most recently modified projects (default limit).
     *
     * @return a list of the most recent project metadata
     */
    public List<ProjectMetadata> getRecentProjects() {
        return getRecentProjects(MAX_RECENT_PROJECTS);
    }

    // =========================================================================
    // Import / Export Operations
    // =========================================================================

    /**
     * Exports a project to the specified format and destination.
     *
     * @param projectId the project ID to export
     * @param format    the export format (currently only "zip" is supported)
     * @param dest      the destination file
     * @return true if the export succeeded
     */
    public boolean exportProject(String projectId, String format, File dest) {
        synchronized (lock) {
            if (projectId == null || dest == null) {
                return false;
            }

            if ("zip".equalsIgnoreCase(format)) {
                return storage.exportProjectToZip(projectId, dest);
            } else {
                Log.e(TAG, "Unsupported export format: " + format);
                return false;
            }
        }
    }

    /**
     * Imports a project from a source file.
     *
     * @param source the source file (ZIP format)
     * @return the imported project's ID, or null on failure
     */
    public String importProject(File source) {
        synchronized (lock) {
            if (source == null || !source.exists()) {
                Log.e(TAG, "Cannot import: source file is null or does not exist");
                return null;
            }

            String projectId = storage.importProjectFromZip(source);
            if (projectId != null) {
                Log.i(TAG, "Project imported: " + projectId);

                // Generate thumbnail for the imported project
                ProjectSerializer.ProjectData data = serializer.loadProject(projectId);
                if (data != null && data.getScene() != null) {
                    thumbnailGenerator.generateAndSave(projectId, data.getScene());
                }
            }

            return projectId;
        }
    }

    /**
     * Creates an Intent for sharing a project file.
     *
     * @param projectId the project ID to share
     * @return an Intent that can be started with startActivity(), or null on error
     */
    public Intent shareProject(String projectId) {
        synchronized (lock) {
            if (projectId == null || projectId.isEmpty()) {
                return null;
            }

            if (!storage.projectExists(projectId)) {
                Log.e(TAG, "Cannot share project: does not exist - " + projectId);
                return null;
            }

            // Create a temporary zip file in the cache directory for sharing
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = context.getCacheDir();
            }

            File shareDir = new File(cacheDir, "sharing");
            if (!shareDir.exists()) {
                shareDir.mkdirs();
            }

            String zipFileName = projectId + ".3dbmc";
            File zipFile = new File(shareDir, zipFileName);

            boolean exported = storage.exportProjectToZip(projectId, zipFile);
            if (!exported) {
                Log.e(TAG, "Failed to export project for sharing: " + projectId);
                return null;
            }

            // Get content URI via FileProvider
            Uri contentUri;
            try {
                contentUri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, zipFile);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get content URI for sharing", e);
                return null;
            }

            // Create share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/zip");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "3D Builder MC Project");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out my 3D Builder MC project!");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            return Intent.createChooser(shareIntent, "Share Project");
        }
    }

    // =========================================================================
    // Auto-Save Operations
    // =========================================================================

    /**
     * Starts auto-save for a project.
     *
     * @param projectId the project ID
     * @param scene     the scene reference
     * @param interval  the auto-save interval in milliseconds
     */
    public void startAutoSave(String projectId, Scene scene, long interval) {
        autoSave.startAutoSave(projectId, scene, interval);
    }

    /**
     * Stops auto-save.
     */
    public void stopAutoSave() {
        autoSave.stopAutoSave();
    }

    /**
     * Returns whether auto-save is currently running.
     */
    public boolean isAutoSaveRunning() {
        return autoSave.isAutoSaveRunning();
    }

    /**
     * Marks the current project as having unsaved changes.
     */
    public void markDirty() {
        autoSave.setDirty();
    }

    /**
     * Checks if there are unsaved changes.
     */
    public boolean hasUnsavedChanges() {
        return autoSave.checkUnsavedChanges();
    }

    /**
     * Forces an immediate save.
     */
    public boolean forceAutoSave() {
        return autoSave.forceSave();
    }

    // =========================================================================
    // Storage and Cleanup Operations
    // =========================================================================

    /**
     * Returns the total storage used by all projects.
     *
     * @return total bytes used
     */
    public long getStorageUsage() {
        synchronized (lock) {
            return storage.getTotalStorageUsed();
        }
    }

    /**
     * Cleans up temporary files, orphaned data, and stale autosave files.
     *
     * @return the number of files removed
     */
    public int cleanup() {
        synchronized (lock) {
            int removed = storage.cleanOrphanedFiles();

            // Also clear the thumbnail cache to free memory
            thumbnailGenerator.clearAllCache();

            Log.i(TAG, "Cleanup completed. Removed " + removed + " files.");
            return removed;
        }
    }

    // =========================================================================
    // Component Accessors
    // =========================================================================

    /**
     * Returns the ProjectStorage instance.
     */
    public ProjectStorage getStorage() {
        return storage;
    }

    /**
     * Returns the ProjectSerializer instance.
     */
    public ProjectSerializer getSerializer() {
        return serializer;
    }

    /**
     * Returns the ProjectLoader instance.
     */
    public ProjectLoader getLoader() {
        return loader;
    }

    /**
     * Returns the ProjectThumbnailGenerator instance.
     */
    public ProjectThumbnailGenerator getThumbnailGenerator() {
        return thumbnailGenerator;
    }

    /**
     * Returns the ProjectAutoSave instance.
     */
    public ProjectAutoSave getAutoSave() {
        return autoSave;
    }

    /**
     * Returns the application context.
     */
    public Context getContext() {
        return context;
    }

    // =========================================================================
    // Validation and Utility
    // =========================================================================

    /**
     * Validates a project's structure and data integrity.
     *
     * @param projectId the project ID to validate
     * @return true if the project is valid
     */
    public boolean validateProject(String projectId) {
        synchronized (lock) {
            if (projectId == null || projectId.isEmpty()) {
                return false;
            }
            return storage.validateProjectStructure(projectId);
        }
    }

    /**
     * Gets the metadata for a specific project.
     *
     * @param projectId the project ID
     * @return the metadata, or null if not found
     */
    public ProjectMetadata getProjectMetadata(String projectId) {
        synchronized (lock) {
            return loader.loadProjectMetadata(projectId);
        }
    }

    /**
     * Gets a project thumbnail bitmap.
     *
     * @param projectId the project ID
     * @return the thumbnail bitmap, or null if not available
     */
    public android.graphics.Bitmap getProjectThumbnail(String projectId) {
        return thumbnailGenerator.loadThumbnail(projectId);
    }

    /**
     * Checks if a project exists.
     *
     * @param projectId the project ID
     * @return true if the project exists
     */
    public boolean projectExists(String projectId) {
        synchronized (lock) {
            return storage.projectExists(projectId);
        }
    }

    /**
     * Returns the storage path for a project.
     *
     * @param projectId the project ID
     * @return the path string, or null if the project doesn't exist
     */
    public String getProjectPath(String projectId) {
        File dir = storage.getProjectDir(projectId);
        return dir != null ? dir.getAbsolutePath() : null;
    }

    /**
     * Returns the size of a specific project.
     *
     * @param projectId the project ID
     * @return the size in bytes
     */
    public long getProjectSize(String projectId) {
        synchronized (lock) {
            return storage.getProjectSize(projectId);
        }
    }

    /**
     * Generates a thumbnail for a project.
     *
     * @param projectId the project ID
     * @param scene     the scene to render
     * @return the generated bitmap
     */
    public android.graphics.Bitmap generateThumbnail(String projectId, Scene scene) {
        return thumbnailGenerator.generateAndSave(projectId, scene);
    }
}
