package com.dbuild.net.texture;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles file system storage for textures and their thumbnails.
 * Manages the texture directory structure under the app's external
 * files directory and provides CRUD operations for texture files.
 */
public class TextureStorage {

    private static final String TEXTURES_DIR_NAME = "textures";
    private static final String THUMBNAILS_DIR_NAME = "thumbnails";

    private final Context context;
    private final File texturesDir;
    private final File thumbnailsDir;

    /**
     * Constructs a TextureStorage with the given Android context.
     * Initializes the texture and thumbnail directory paths.
     *
     * @param context the Android context
     */
    public TextureStorage(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();

        // Use external files directory for app-specific storage
        File appDir = context.getExternalFilesDir(null);
        if (appDir == null) {
            // Fallback to internal storage
            appDir = context.getFilesDir();
        }

        this.texturesDir = new File(appDir, TEXTURES_DIR_NAME);
        this.thumbnailsDir = new File(appDir, THUMBNAILS_DIR_NAME);

        ensureDirectoriesExist();
    }

    /**
     * Ensures that the texture and thumbnail directories exist.
     */
    private void ensureDirectoriesExist() {
        if (!texturesDir.exists()) {
            texturesDir.mkdirs();
        }
        createThumbnailsDir();
    }

    // ========== Directory Access ==========

    /**
     * Returns the directory where textures are stored.
     * Path: Android/data/com.dbuild.net/files/textures/
     *
     * @return the textures directory File
     */
    public File getTexturesDir() {
        if (!texturesDir.exists()) {
            texturesDir.mkdirs();
        }
        return texturesDir;
    }

    /**
     * Returns the directory where thumbnails are stored.
     *
     * @return the thumbnails directory File
     */
    public File getThumbnailsDir() {
        createThumbnailsDir();
        return thumbnailsDir;
    }

    /**
     * Creates the thumbnail directory if it doesn't exist.
     */
    public void createThumbnailsDir() {
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs();
        }
    }

    // ========== Save / Delete ==========

    /**
     * Saves a texture file to storage with the given texture ID.
     * Copies the source file to the textures directory using the
     * texture ID as the filename (preserving the original extension).
     *
     * @param source    the source texture file
     * @param textureId the unique texture ID for naming
     * @return the saved File in the textures directory, or null on failure
     */
    public File saveTexture(File source, String textureId) {
        if (source == null || !source.exists() || !source.canRead()) {
            return null;
        }
        if (textureId == null || textureId.isEmpty()) {
            return null;
        }

        String extension = getExtension(source.getName());
        String targetName = textureId + extension;

        File destFile = new File(getTexturesDir(), targetName);

        // If the destination already exists, delete it first
        if (destFile.exists()) {
            destFile.delete();
        }

        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(source);
            fos = new FileOutputStream(destFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();
            return destFile;
        } catch (IOException e) {
            if (destFile.exists()) {
                destFile.delete();
            }
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Deletes a texture and its thumbnail by the texture ID.
     *
     * @param textureId the texture ID to delete
     * @return true if the texture was found and at least one file was deleted
     */
    public boolean deleteTexture(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return false;
        }

        boolean deletedAny = false;

        // Delete texture file(s) matching this ID
        File[] textureFiles = getTexturesDir().listFiles();
        if (textureFiles != null) {
            for (File file : textureFiles) {
                String name = file.getName();
                if (name.startsWith(textureId + ".")) {
                    if (file.delete()) {
                        deletedAny = true;
                    }
                }
            }
        }

        // Delete thumbnail file(s) matching this ID
        File[] thumbFiles = getThumbnailsDir().listFiles();
        if (thumbFiles != null) {
            for (File file : thumbFiles) {
                String name = file.getName();
                if (name.startsWith(textureId + ".")) {
                    if (file.delete()) {
                        deletedAny = true;
                    }
                }
            }
        }

        return deletedAny;
    }

    // ========== File Access ==========

    /**
     * Gets the texture file for a given texture ID.
     *
     * @param textureId the texture ID
     * @return the File object, or null if no matching file exists
     */
    public File getTextureFile(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return null;
        }

        File[] files = getTexturesDir().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(textureId + ".") && file.isFile()) {
                    return file;
                }
            }
        }

        return null;
    }

    /**
     * Gets the thumbnail file for a given texture ID.
     *
     * @param textureId the texture ID
     * @return the File object for the thumbnail, or null if not found
     */
    public File getThumbnailFile(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return null;
        }

        File[] files = getThumbnailsDir().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(textureId + ".") && file.isFile()) {
                    return file;
                }
            }
        }

        return null;
    }

    // ========== Query ==========

    /**
     * Checks if a texture file exists for the given ID.
     *
     * @param textureId the texture ID to check
     * @return true if the texture file exists
     */
    public boolean textureExists(String textureId) {
        return getTextureFile(textureId) != null;
    }

    /**
     * Lists all texture IDs in the storage directory.
     *
     * @return list of texture ID strings
     */
    public List<String> listTextureIds() {
        List<String> ids = new ArrayList<>();
        File[] files = getTexturesDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String name = file.getName();
                    int dotIndex = name.lastIndexOf('.');
                    if (dotIndex > 0) {
                        ids.add(name.substring(0, dotIndex));
                    } else {
                        ids.add(name);
                    }
                }
            }
        }
        return ids;
    }

    /**
     * Gets the file size of a texture by its ID.
     *
     * @param textureId the texture ID
     * @return the file size in bytes, or 0 if not found
     */
    public long getTextureSize(String textureId) {
        File file = getTextureFile(textureId);
        if (file != null && file.exists()) {
            return file.length();
        }
        return 0;
    }

    /**
     * Returns the total storage size of all textures (excluding thumbnails).
     *
     * @return total size in bytes
     */
    public long getTotalStorageSize() {
        return getDirectorySize(texturesDir) + getDirectorySize(thumbnailsDir);
    }

    /**
     * Recursively calculates the total size of a directory.
     *
     * @param dir the directory to measure
     * @return total size in bytes
     */
    private long getDirectorySize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }

        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += getDirectorySize(file);
                }
            }
        }
        return size;
    }

    // ========== Cleanup ==========

    /**
     * Removes texture files that are not in the provided set of used texture IDs.
     * This helps clean up orphaned textures that are no longer referenced
     * by any block.
     *
     * @param usedTextureIds the set of texture IDs that are still in use
     * @return the number of orphaned textures removed
     */
    public int cleanupOrphaned(Set<String> usedTextureIds) {
        if (usedTextureIds == null) {
            usedTextureIds = new java.util.HashSet<>();
        }

        int removedCount = 0;

        // Clean textures directory
        File[] textureFiles = getTexturesDir().listFiles();
        if (textureFiles != null) {
            for (File file : textureFiles) {
                if (file.isFile()) {
                    String name = file.getName();
                    int dotIndex = name.lastIndexOf('.');
                    String fileId = dotIndex > 0 ? name.substring(0, dotIndex) : name;

                    if (!usedTextureIds.contains(fileId) &&
                            !usedTextureIds.contains(name)) {
                        if (file.delete()) {
                            removedCount++;
                        }
                    }
                }
            }
        }

        // Clean thumbnails directory
        File[] thumbFiles = getThumbnailsDir().listFiles();
        if (thumbFiles != null) {
            for (File file : thumbFiles) {
                if (file.isFile()) {
                    String name = file.getName();
                    int dotIndex = name.lastIndexOf('.');
                    String fileId = dotIndex > 0 ? name.substring(0, dotIndex) : name;

                    if (!usedTextureIds.contains(fileId) &&
                            !usedTextureIds.contains(name)) {
                        file.delete();
                    }
                }
            }
        }

        return removedCount;
    }

    // ========== Utility ==========

    /**
     * Extracts the file extension from a filename, including the dot.
     *
     * @param filename the filename
     * @return the extension including the dot (e.g., ".png"), or ".png" as default
     */
    private String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return ".png";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex).toLowerCase(Locale.US);
        }
        return ".png";
    }

    /**
     * Returns the total number of texture files in storage.
     *
     * @return count of texture files
     */
    public int getTextureFileCount() {
        File[] files = getTexturesDir().listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file : files) {
            if (file.isFile()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the total number of thumbnail files in storage.
     *
     * @return count of thumbnail files
     */
    public int getThumbnailFileCount() {
        File[] files = getThumbnailsDir().listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file : files) {
            if (file.isFile()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the Android context.
     *
     * @return the context
     */
    public Context getContext() {
        return context;
    }

    @Override
    public String toString() {
        return "TextureStorage{" +
                "texturesDir=" + texturesDir.getAbsolutePath() +
                ", textureCount=" + getTextureFileCount() +
                ", totalSize=" + getTotalStorageSize() +
                '}';
    }
}
