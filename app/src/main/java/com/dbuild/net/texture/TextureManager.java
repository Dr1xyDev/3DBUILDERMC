package com.dbuild.net.texture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Central texture management singleton.
 * Coordinates between TextureLibrary, TextureImporter, and TextureStorage
 * to provide a unified API for texture operations.
 */
public class TextureManager {

    private static volatile TextureManager instance;

    private final Context context;
    private final TextureLibrary library;
    private final TextureImporter importer;
    private final TextureStorage storage;
    private final LruCache<String, Bitmap> thumbnailCache;

    /**
     * Inner class representing metadata about a texture.
     */
    public static class TextureInfo {
        private String textureId;
        private String name;
        private String filePath;
        private String format;
        private int width;
        private int height;
        private long fileSize;
        private long addedAt;

        public TextureInfo() {
            this.textureId = "";
            this.name = "";
            this.filePath = "";
            this.format = "PNG";
            this.width = 0;
            this.height = 0;
            this.fileSize = 0;
            this.addedAt = System.currentTimeMillis();
        }

        public TextureInfo(String textureId, String name, String filePath,
                           String format, int width, int height,
                           long fileSize, long addedAt) {
            this.textureId = textureId;
            this.name = name;
            this.filePath = filePath;
            this.format = format;
            this.width = width;
            this.height = height;
            this.fileSize = fileSize;
            this.addedAt = addedAt;
        }

        public String getTextureId() {
            return textureId;
        }

        public void setTextureId(String textureId) {
            this.textureId = textureId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public long getAddedAt() {
            return addedAt;
        }

        public void setAddedAt(long addedAt) {
            this.addedAt = addedAt;
        }

        /**
         * Returns a human-readable file size string.
         */
        public String getFormattedFileSize() {
            if (fileSize < 1024) {
                return fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                return String.format(Locale.US, "%.1f KB", fileSize / 1024.0);
            } else {
                return String.format(Locale.US, "%.1f MB", fileSize / (1024.0 * 1024.0));
            }
        }

        /**
         * Returns a dimensions string like "256x256".
         */
        public String getDimensionsString() {
            return width + "x" + height;
        }

        /**
         * Checks if this texture is a power-of-two size.
         */
        public boolean isPowerOfTwo() {
            return isPowerOfTwo(width) && isPowerOfTwo(height);
        }

        private boolean isPowerOfTwo(int n) {
            return n > 0 && (n & (n - 1)) == 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TextureInfo that = (TextureInfo) o;
            return textureId != null ? textureId.equals(that.textureId) : that.textureId == null;
        }

        @Override
        public int hashCode() {
            return textureId != null ? textureId.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "TextureInfo{" +
                    "id='" + textureId + '\'' +
                    ", name='" + name + '\'' +
                    ", format='" + format + '\'' +
                    ", dims=" + getDimensionsString() +
                    '}';
        }
    }

    /**
     * Private constructor for singleton pattern.
     *
     * @param context the Android context
     */
    private TextureManager(Context context) {
        this.context = context.getApplicationContext();
        this.storage = new TextureStorage(this.context);
        this.library = new TextureLibrary();
        this.importer = new TextureImporter(this.context);

        // Initialize thumbnail cache (~2MB)
        final int cacheSizeBytes = 2 * 1024 * 1024;
        this.thumbnailCache = new LruCache<String, Bitmap>(cacheSizeBytes) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key,
                                        Bitmap oldValue, Bitmap newValue) {
                if (oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };

        loadLibraryFromStorage();
    }

    /**
     * Returns the singleton instance of TextureManager.
     *
     * @param context the Android context
     * @return the TextureManager instance
     */
    public static TextureManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TextureManager.class) {
                if (instance == null) {
                    instance = new TextureManager(context);
                }
            }
        }
        return instance;
    }

    // ========== Import / Delete ==========

    /**
     * Imports a texture from a file source.
     * Copies the file to the texture storage directory and
     * processes it (resize if needed, generate thumbnail).
     *
     * @param source the source file
     * @return the texture ID of the imported texture, or null on failure
     */
    public String importTexture(File source) {
        if (source == null || !source.exists()) {
            return null;
        }

        TextureInfo info = importer.importFromFile(source);
        if (info == null) {
            return null;
        }

        library.addTexture(info);
        return info.getTextureId();
    }

    /**
     * Deletes a texture by its ID.
     * Removes from library, deletes the file and thumbnail.
     *
     * @param textureId the texture ID to delete
     * @return true if the texture was found and deleted
     */
    public boolean deleteTexture(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return false;
        }

        TextureInfo info = library.getTexture(textureId);
        if (info == null) {
            return false;
        }

        // Remove from cache
        thumbnailCache.remove(textureId);

        // Remove from storage
        storage.deleteTexture(textureId);

        // Remove from library
        library.removeTexture(textureId);

        return true;
    }

    // ========== Getters ==========

    /**
     * Gets the TextureInfo for a texture ID.
     *
     * @param textureId the texture ID
     * @return the TextureInfo, or null if not found
     */
    public TextureInfo getTexture(String textureId) {
        return library.getTexture(textureId);
    }

    /**
     * Returns all textures in the library.
     *
     * @return list of all TextureInfo objects
     */
    public List<TextureInfo> getAllTextures() {
        return library.getAllTextures();
    }

    /**
     * Gets the full bitmap for a texture.
     * Warning: This can be memory-intensive for large textures.
     *
     * @param textureId the texture ID
     * @return the Bitmap, or null if not found or could not be decoded
     */
    public Bitmap getTextureBitmap(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return null;
        }

        TextureInfo info = library.getTexture(textureId);
        if (info == null) {
            return null;
        }

        File textureFile = storage.getTextureFile(textureId);
        if (textureFile == null || !textureFile.exists()) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(textureFile.getAbsolutePath(), options);
    }

    /**
     * Gets the thumbnail bitmap for a texture.
     * Uses an in-memory cache for fast repeated access.
     *
     * @param textureId the texture ID
     * @return the thumbnail Bitmap, or null if not available
     */
    public Bitmap getTextureThumbnail(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return null;
        }

        // Check cache first
        Bitmap cached = thumbnailCache.get(textureId);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        // Try loading from thumbnail file
        File thumbFile = storage.getThumbnailFile(textureId);
        if (thumbFile != null && thumbFile.exists()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), options);
            if (thumb != null) {
                thumbnailCache.put(textureId, thumb);
                return thumb;
            }
        }

        // Fall back: generate thumbnail from full texture
        File textureFile = storage.getTextureFile(textureId);
        if (textureFile != null && textureFile.exists()) {
            Bitmap fullBitmap = BitmapFactory.decodeFile(textureFile.getAbsolutePath());
            if (fullBitmap != null) {
                Bitmap thumbnail = Bitmap.createScaledBitmap(fullBitmap, 128, 128, true);
                if (!fullBitmap.equals(thumbnail)) {
                    fullBitmap.recycle();
                }
                thumbnailCache.put(textureId, thumbnail);
                return thumbnail;
            }
        }

        return null;
    }

    // ========== Search / Filter ==========

    /**
     * Searches for textures by name (case-insensitive).
     *
     * @param query the search query
     * @return list of matching TextureInfo objects
     */
    public List<TextureInfo> searchTextures(String query) {
        return library.search(query);
    }

    /**
     * Gets all textures of a specific format.
     *
     * @param format the format string (e.g., "PNG", "JPG")
     * @return list of matching TextureInfo objects
     */
    public List<TextureInfo> getTexturesByFormat(String format) {
        return library.getByFormat(format);
    }

    /**
     * Returns the total number of textures.
     *
     * @return count of textures in the library
     */
    public int getTextureCount() {
        return library.getAllTextures().size();
    }

    /**
     * Returns the total storage size of all textures.
     *
     * @return total size in bytes
     */
    public long getTotalTextureSize() {
        return storage.getTotalStorageSize();
    }

    // ========== Maintenance ==========

    /**
     * Removes textures that are not referenced by any block.
     * Scans the block registry for used texture IDs and removes
     * textures not in that set.
     */
    public void cleanup() {
        Set<String> usedTextureIds = collectUsedTextureIds();
        storage.cleanupOrphaned(usedTextureIds);

        // Also remove orphaned entries from the library
        List<TextureInfo> allTextures = library.getAllTextures();
        List<String> toRemove = new ArrayList<>();
        for (TextureInfo info : allTextures) {
            if (!usedTextureIds.contains(info.getTextureId()) &&
                    !storage.textureExists(info.getTextureId())) {
                toRemove.add(info.getTextureId());
            }
        }
        for (String id : toRemove) {
            library.removeTexture(id);
            thumbnailCache.remove(id);
        }
    }

    /**
     * Collects all texture IDs that are currently used by blocks in the registry.
     *
     * @return set of used texture IDs
     */
    private Set<String> collectUsedTextureIds() {
        Set<String> usedIds = new HashSet<>();
        com.dbuild.net.blocks.BlockRegistry blockRegistry =
                com.dbuild.net.blocks.BlockRegistry.getInstance();

        for (com.dbuild.net.blocks.CustomBlock block : blockRegistry.getAllBlocks()) {
            com.dbuild.net.blocks.BlockTextureSet textureSet = block.getTextureSet();
            if (textureSet != null) {
                for (com.dbuild.net.blocks.CustomBlock.BlockFace face :
                        com.dbuild.net.blocks.CustomBlock.BlockFace.values()) {
                    String path = textureSet.getTexturePath(face);
                    if (path != null && !path.isEmpty()) {
                        usedIds.add(path);
                    }
                }
            }
        }
        return usedIds;
    }

    /**
     * Preloads all thumbnails into the memory cache.
     * Call this on a background thread.
     */
    public void preloadThumbnails() {
        List<TextureInfo> allTextures = library.getAllTextures();
        for (TextureInfo info : allTextures) {
            String id = info.getTextureId();
            if (thumbnailCache.get(id) == null) {
                File thumbFile = storage.getThumbnailFile(id);
                if (thumbFile != null && thumbFile.exists()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), options);
                    if (thumb != null) {
                        thumbnailCache.put(id, thumb);
                    }
                }
            }
        }
    }

    /**
     * Loads the library from storage on initialization.
     */
    private void loadLibraryFromStorage() {
        library.loadFromStorage(storage);
    }

    /**
     * Clears the thumbnail cache to free memory.
     */
    public void clearThumbnailCache() {
        thumbnailCache.evictAll();
    }

    // ========== Accessors ==========

    public Context getContext() {
        return context;
    }

    public TextureLibrary getLibrary() {
        return library;
    }

    public TextureImporter getImporter() {
        return importer;
    }

    public TextureStorage getStorage() {
        return storage;
    }

    /**
     * Resets the singleton instance. Primarily for testing.
     */
    public static void resetInstance() {
        synchronized (TextureManager.class) {
            if (instance != null) {
                instance.clearThumbnailCache();
            }
            instance = null;
        }
    }
}
