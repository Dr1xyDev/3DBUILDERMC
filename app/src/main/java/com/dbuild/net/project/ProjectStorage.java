package com.dbuild.net.project;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Handles all file system operations for 3D Builder MC projects.
 * Projects are stored under Android/data/com.dbuild.net/files/projects/
 * Each project directory contains: project.m3x, textures/, preview.png,
 * metadata.json, scene.json, blocks.json, camera.json, settings.json
 */
public class ProjectStorage {

    private static final String TAG = "ProjectStorage";

    private static final String PROJECTS_RELATIVE_PATH = "projects";
    private static final String FILE_PROJECT = "project.m3x";
    private static final String DIR_TEXTURES = "textures";
    private static final String FILE_PREVIEW = "preview.png";
    private static final String FILE_METADATA = "metadata.json";
    private static final String FILE_SCENE = "scene.json";
    private static final String FILE_BLOCKS = "blocks.json";
    private static final String FILE_CAMERA = "camera.json";
    private static final String FILE_SETTINGS = "settings.json";
    private static final String FILE_AUTOSAVE = "autosave.m3x";
    private static final String TEMP_PREFIX = "tmp_";
    private static final int BUFFER_SIZE = 8192;

    private final Context context;

    public ProjectStorage(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Returns the base projects directory.
     * Points to Android/data/com.dbuild.net/files/projects/
     *
     * @return the File representing the projects directory
     */
    public File getProjectsDir() {
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            // Fall back to internal storage if external is unavailable
            externalDir = new File(context.getFilesDir(), "Android/data/com.dbuild.net/files");
        }
        File projectsDir = new File(externalDir, PROJECTS_RELATIVE_PATH);
        if (!projectsDir.exists()) {
            projectsDir.mkdirs();
        }
        return projectsDir;
    }

    /**
     * Returns the directory for a specific project.
     *
     * @param projectId the UUID of the project
     * @return the File representing the project directory
     */
    public File getProjectDir(String projectId) {
        return new File(getProjectsDir(), projectId);
    }

    /**
     * Creates the full directory structure and empty files for a new project.
     *
     * @param projectId the UUID for the new project
     * @return true if the structure was created successfully
     */
    public boolean createProjectStructure(String projectId) {
        File projectDir = getProjectDir(projectId);
        if (projectDir.exists()) {
            Log.w(TAG, "Project directory already exists: " + projectId);
            return false;
        }

        boolean created = projectDir.mkdirs();
        if (!created) {
            Log.e(TAG, "Failed to create project directory: " + projectId);
            return false;
        }

        // Create textures subdirectory
        File texturesDir = new File(projectDir, DIR_TEXTURES);
        if (!texturesDir.mkdirs()) {
            Log.e(TAG, "Failed to create textures directory for project: " + projectId);
            return false;
        }

        // Create empty data files
        String[] filesToCreate = {
                FILE_PROJECT, FILE_SCENE, FILE_BLOCKS,
                FILE_CAMERA, FILE_SETTINGS, FILE_METADATA
        };

        for (String filename : filesToCreate) {
            File file = new File(projectDir, filename);
            try {
                if (!file.exists()) {
                    boolean fileCreated = file.createNewFile();
                    if (!fileCreated) {
                        Log.e(TAG, "Failed to create file: " + filename);
                        return false;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException creating file: " + filename, e);
                return false;
            }
        }

        Log.i(TAG, "Project structure created successfully: " + projectId);
        return true;
    }

    /**
     * Deletes an entire project directory and all its contents.
     *
     * @param projectId the ID of the project to delete
     * @return true if deletion succeeded
     */
    public boolean deleteProject(String projectId) {
        File projectDir = getProjectDir(projectId);
        if (!projectDir.exists()) {
            Log.w(TAG, "Project directory does not exist: " + projectId);
            return false;
        }
        boolean deleted = deleteRecursive(projectDir);
        if (deleted) {
            Log.i(TAG, "Project deleted: " + projectId);
        } else {
            Log.e(TAG, "Failed to delete project: " + projectId);
        }
        return deleted;
    }

    /**
     * Recursively deletes a file or directory.
     */
    private boolean deleteRecursive(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        return fileOrDir.delete();
    }

    /**
     * Checks whether a project with the given ID exists.
     *
     * @param projectId the project ID
     * @return true if the project directory exists
     */
    public boolean projectExists(String projectId) {
        File projectDir = getProjectDir(projectId);
        return projectDir.exists() && projectDir.isDirectory();
    }

    /**
     * Returns a File object for a specific file within a project.
     *
     * @param projectId the project ID
     * @param filename  the name of the file
     * @return the File object
     */
    public File getProjectFile(String projectId, String filename) {
        return new File(getProjectDir(projectId), filename);
    }

    /**
     * Returns a list of all project IDs found in the projects directory.
     *
     * @return an unmodifiable list of project ID strings
     */
    public List<String> listProjectIds() {
        File projectsDir = getProjectsDir();
        File[] dirs = projectsDir.listFiles();
        if (dirs == null) {
            return Collections.emptyList();
        }
        List<String> projectIds = new ArrayList<>();
        for (File dir : dirs) {
            if (dir.isDirectory() && !dir.getName().startsWith(TEMP_PREFIX)) {
                // Verify it has at least a metadata.json to be considered a valid project
                File metadataFile = new File(dir, FILE_METADATA);
                if (metadataFile.exists()) {
                    projectIds.add(dir.getName());
                }
            }
        }
        return Collections.unmodifiableList(projectIds);
    }

    /**
     * Calculates the total size of a project in bytes.
     *
     * @param projectId the project ID
     * @return the total size in bytes, or 0 if the project does not exist
     */
    public long getProjectSize(String projectId) {
        File projectDir = getProjectDir(projectId);
        if (!projectDir.exists()) {
            return 0L;
        }
        return calculateDirectorySize(projectDir);
    }

    /**
     * Recursively calculates the total size of a directory.
     */
    private long calculateDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    /**
     * Copies an entire project to a new project ID.
     *
     * @param sourceId    the source project ID
     * @param newProjectId the destination project ID
     * @return true if the copy succeeded
     */
    public boolean copyProject(String sourceId, String newProjectId) {
        File sourceDir = getProjectDir(sourceId);
        File destDir = getProjectDir(newProjectId);

        if (!sourceDir.exists()) {
            Log.e(TAG, "Source project does not exist: " + sourceId);
            return false;
        }
        if (destDir.exists()) {
            Log.e(TAG, "Destination project already exists: " + newProjectId);
            return false;
        }

        return copyRecursive(sourceDir, destDir);
    }

    /**
     * Recursively copies files and directories.
     */
    private boolean copyRecursive(File source, File dest) {
        if (source.isDirectory()) {
            if (!dest.mkdirs() && !dest.exists()) {
                Log.e(TAG, "Failed to create directory: " + dest.getAbsolutePath());
                return false;
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!copyRecursive(child, new File(dest, child.getName()))) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return copyFile(source, dest);
        }
    }

    /**
     * Copies a single file using stream I/O.
     */
    private boolean copyFile(File source, File dest) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file: " + source.getAbsolutePath()
                    + " -> " + dest.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Renames a project by updating its metadata.json file.
     *
     * @param projectId the project ID
     * @param newName   the new project name
     * @return true if the rename succeeded
     */
    public boolean renameProject(String projectId, String newName) {
        File metadataFile = getProjectFile(projectId, FILE_METADATA);
        if (!metadataFile.exists()) {
            Log.e(TAG, "Metadata file not found for project: " + projectId);
            return false;
        }

        try {
            // Read existing metadata
            byte[] data = readFileBytes(metadataFile);
            String jsonStr = new String(data, "UTF-8");
            JSONObject json = new JSONObject(jsonStr);
            json.put("projectName", newName);
            json.put("modifiedAt", System.currentTimeMillis());

            // Write back using safe write
            return writeTextFileSafe(metadataFile, json.toString(2));
        } catch (Exception e) {
            Log.e(TAG, "Failed to rename project: " + projectId, e);
            return false;
        }
    }

    /**
     * Reads the entire contents of a file into a byte array.
     */
    private byte[] readFileBytes(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read == -1) break;
                offset += read;
            }
            return data;
        }
    }

    /**
     * Writes text to a file safely by writing to a temp file first, then renaming.
     *
     * @param file the target file
     * @param text the text content
     * @return true if the write succeeded
     */
    private boolean writeTextFileSafe(File file, String text) {
        File tempFile = new File(file.getParent(), TEMP_PREFIX + file.getName());
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write temp file: " + tempFile.getAbsolutePath(), e);
            tempFile.delete();
            return false;
        }

        // Delete original and rename temp
        if (file.exists()) {
            file.delete();
        }
        boolean renamed = tempFile.renameTo(file);
        if (!renamed) {
            Log.e(TAG, "Failed to rename temp file to: " + file.getAbsolutePath());
            tempFile.delete();
            return false;
        }
        return true;
    }

    /**
     * Removes temporary and orphaned files from the projects directory.
     *
     * @return the number of files removed
     */
    public int cleanOrphanedFiles() {
        int removed = 0;
        File projectsDir = getProjectsDir();
        File[] entries = projectsDir.listFiles();
        if (entries == null) {
            return 0;
        }

        for (File entry : entries) {
            // Remove temp directories
            if (entry.isDirectory() && entry.getName().startsWith(TEMP_PREFIX)) {
                if (deleteRecursive(entry)) {
                    removed++;
                    Log.i(TAG, "Removed temp directory: " + entry.getName());
                }
            }
            // Remove temp files in project directories
            else if (entry.isDirectory()) {
                File[] projectFiles = entry.listFiles();
                if (projectFiles != null) {
                    for (File pf : projectFiles) {
                        if (pf.getName().startsWith(TEMP_PREFIX)) {
                            if (pf.delete()) {
                                removed++;
                                Log.i(TAG, "Removed temp file: " + pf.getAbsolutePath());
                            }
                        }
                    }
                    // Remove autosave files that are older than 7 days
                    File autosave = new File(entry, FILE_AUTOSAVE);
                    if (autosave.exists()) {
                        long age = System.currentTimeMillis() - autosave.lastModified();
                        if (age > 7L * 24 * 60 * 60 * 1000) {
                            if (autosave.delete()) {
                                removed++;
                                Log.i(TAG, "Removed stale autosave: " + autosave.getAbsolutePath());
                            }
                        }
                    }
                }
            }
            // Remove stray files in the projects root (shouldn't be any)
            else if (entry.isFile()) {
                if (entry.delete()) {
                    removed++;
                    Log.i(TAG, "Removed stray file: " + entry.getAbsolutePath());
                }
            }
        }

        Log.i(TAG, "Orphaned files cleanup completed. Removed: " + removed);
        return removed;
    }

    /**
     * Exports a project to a ZIP file.
     *
     * @param projectId the project ID to export
     * @param dest      the destination ZIP file
     * @return true if the export succeeded
     */
    public boolean exportProjectToZip(String projectId, File dest) {
        File projectDir = getProjectDir(projectId);
        if (!projectDir.exists()) {
            Log.e(TAG, "Project does not exist for export: " + projectId);
            return false;
        }

        File tempDest = new File(dest.getParent(), TEMP_PREFIX + dest.getName());
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempDest)))) {
            zipDirectory(projectDir, projectDir.getName(), zos);
            zos.finish();
        } catch (IOException e) {
            Log.e(TAG, "Failed to export project to zip: " + projectId, e);
            tempDest.delete();
            return false;
        }

        // Replace destination with the temp file
        if (dest.exists()) {
            dest.delete();
        }
        boolean renamed = tempDest.renameTo(dest);
        if (!renamed) {
            Log.e(TAG, "Failed to rename zip file to destination");
            tempDest.delete();
            return false;
        }

        Log.i(TAG, "Project exported successfully: " + projectId + " -> " + dest.getAbsolutePath());
        return true;
    }

    /**
     * Recursively adds files from a directory to a ZipOutputStream.
     */
    private void zipDirectory(File dir, String basePath, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                zipDirectory(file, basePath + "/" + file.getName(), zos);
            } else {
                ZipEntry entry = new ZipEntry(basePath + "/" + file.getName());
                zos.putNextEntry(entry);
                try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * Imports a project from a ZIP file.
     * Extracts the ZIP and creates a project structure in the projects directory.
     *
     * @param zipFile the source ZIP file
     * @return the project ID of the imported project, or null on failure
     */
    public String importProjectFromZip(File zipFile) {
        if (!zipFile.exists()) {
            Log.e(TAG, "Zip file does not exist: " + zipFile.getAbsolutePath());
            return null;
        }

        // Extract to a temp directory first
        File tempExtractDir = new File(getProjectsDir(), TEMP_PREFIX + System.currentTimeMillis());
        if (!tempExtractDir.mkdirs()) {
            Log.e(TAG, "Failed to create temp extraction directory");
            return null;
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(tempExtractDir, entry.getName());

                // Security: prevent path traversal
                String canonicalExtractPath = outFile.getCanonicalPath();
                String canonicalTempPath = tempExtractDir.getCanonicalPath();
                if (!canonicalExtractPath.startsWith(canonicalTempPath)) {
                    Log.e(TAG, "Path traversal detected in zip entry: " + entry.getName());
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parentDir = outFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract zip file", e);
            deleteRecursive(tempExtractDir);
            return null;
        }

        // The extracted directory should contain one subdirectory named after the project ID
        File[] extractedContents = tempExtractDir.listFiles();
        if (extractedContents == null || extractedContents.length == 0) {
            Log.e(TAG, "Zip file was empty or had unexpected structure");
            deleteRecursive(tempExtractDir);
            return null;
        }

        // Find the project directory (first directory in extracted contents)
        File extractedProjectDir = null;
        for (File f : extractedContents) {
            if (f.isDirectory()) {
                extractedProjectDir = f;
                break;
            }
        }

        if (extractedProjectDir == null) {
            // Maybe the zip directly contains project files (no subdirectory)
            // Treat the temp dir itself as the project dir
            extractedProjectDir = tempExtractDir;
        }

        // Determine the project ID from metadata.json or directory name
        String projectId = extractedProjectDir.getName();
        File metadataFile = new File(extractedProjectDir, FILE_METADATA);
        if (metadataFile.exists()) {
            try {
                byte[] data = readFileBytes(metadataFile);
                String jsonStr = new String(data, "UTF-8");
                JSONObject json = new JSONObject(jsonStr);
                if (json.has("projectId")) {
                    projectId = json.getString("projectId");
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not read projectId from metadata, using directory name", e);
            }
        }

        // Make sure the project ID doesn't conflict with existing projects
        if (projectExists(projectId)) {
            projectId = projectId + "_" + System.currentTimeMillis();
        }

        // Move the extracted project to its final location
        File finalDir = getProjectDir(projectId);
        boolean moved = extractedProjectDir.renameTo(finalDir);
        if (!moved) {
            // Try copying instead
            boolean copied = copyRecursive(extractedProjectDir, finalDir);
            if (!copied) {
                Log.e(TAG, "Failed to move/copy extracted project to final location");
                deleteRecursive(tempExtractDir);
                return null;
            }
            deleteRecursive(extractedProjectDir);
        }

        // Clean up temp extraction dir
        if (tempExtractDir.exists()) {
            deleteRecursive(tempExtractDir);
        }

        // Validate the imported structure
        if (!validateProjectStructure(projectId)) {
            Log.w(TAG, "Imported project has incomplete structure: " + projectId);
        }

        // Update metadata with new modification time
        try {
            File finalMetadata = getProjectFile(projectId, FILE_METADATA);
            if (finalMetadata.exists()) {
                byte[] data = readFileBytes(finalMetadata);
                String jsonStr = new String(data, "UTF-8");
                JSONObject json = new JSONObject(jsonStr);
                json.put("projectId", projectId);
                json.put("modifiedAt", System.currentTimeMillis());
                writeTextFileSafe(finalMetadata, json.toString(2));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not update metadata after import", e);
        }

        Log.i(TAG, "Project imported successfully: " + projectId);
        return projectId;
    }

    /**
     * Validates that a project has all required files and directories.
     *
     * @param projectId the project ID
     * @return true if the structure is valid and complete
     */
    public boolean validateProjectStructure(String projectId) {
        File projectDir = getProjectDir(projectId);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return false;
        }

        // Check required directories
        File texturesDir = new File(projectDir, DIR_TEXTURES);
        if (!texturesDir.exists() || !texturesDir.isDirectory()) {
            Log.w(TAG, "Missing textures directory for project: " + projectId);
            return false;
        }

        // Check required files
        String[] requiredFiles = {
                FILE_METADATA, FILE_SCENE, FILE_BLOCKS,
                FILE_CAMERA, FILE_SETTINGS
        };

        for (String filename : requiredFiles) {
            File file = new File(projectDir, filename);
            if (!file.exists() || !file.isFile()) {
                Log.w(TAG, "Missing required file '" + filename + "' for project: " + projectId);
                return false;
            }
        }

        // Validate metadata can be parsed
        File metadataFile = new File(projectDir, FILE_METADATA);
        try {
            byte[] data = readFileBytes(metadataFile);
            String jsonStr = new String(data, "UTF-8");
            JSONObject json = new JSONObject(jsonStr);
            if (!json.has("projectId") || !json.has("projectName")) {
                Log.w(TAG, "Metadata missing required fields for project: " + projectId);
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Metadata is not valid JSON for project: " + projectId, e);
            return false;
        }

        return true;
    }

    /**
     * Returns the autosave file for a project.
     *
     * @param projectId the project ID
     * @return the File object for autosave.m3x
     */
    public File getAutoSaveFile(String projectId) {
        return getProjectFile(projectId, FILE_AUTOSAVE);
    }

    /**
     * Checks whether autosave data exists for a project.
     *
     * @param projectId the project ID
     * @return true if autosave data exists and is non-empty
     */
    public boolean hasAutoSaveData(String projectId) {
        File autosave = getAutoSaveFile(projectId);
        return autosave.exists() && autosave.length() > 0;
    }

    /**
     * Deletes the autosave file for a project.
     *
     * @param projectId the project ID
     * @return true if the autosave file was deleted or didn't exist
     */
    public boolean deleteAutoSave(String projectId) {
        File autosave = getAutoSaveFile(projectId);
        if (autosave.exists()) {
            return autosave.delete();
        }
        return true;
    }

    /**
     * Returns the total storage used across all projects.
     *
     * @return total bytes used
     */
    public long getTotalStorageUsed() {
        File projectsDir = getProjectsDir();
        return calculateDirectorySize(projectsDir);
    }

    /**
     * Returns the project file name constants for external use.
     */
    public String getFileNameMetadata() { return FILE_METADATA; }
    public String getFileNameScene() { return FILE_SCENE; }
    public String getFileNameBlocks() { return FILE_BLOCKS; }
    public String getFileNameCamera() { return FILE_CAMERA; }
    public String getFileNameSettings() { return FILE_SETTINGS; }
    public String getFileNamePreview() { return FILE_PREVIEW; }
    public String getFileNameProject() { return FILE_PROJECT; }
    public String getFileNameAutoSave() { return FILE_AUTOSAVE; }
    public String getDirNameTextures() { return DIR_TEXTURES; }
}
