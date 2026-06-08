package com.dbuild.net.texture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manages the collection of available textures.
 * Provides add, remove, search, filter, and sort operations
 * on the texture library.
 */
public class TextureLibrary {

    /** Sort mode constants. */
    public static final String SORT_BY_NAME = "name";
    public static final String SORT_BY_DATE = "date";
    public static final String SORT_BY_SIZE = "size";

    private final Map<String, TextureManager.TextureInfo> textures;
    private String currentSortMode;

    /**
     * Constructs an empty TextureLibrary.
     */
    public TextureLibrary() {
        textures = new LinkedHashMap<>();
        currentSortMode = SORT_BY_NAME;
    }

    // ========== Add / Remove ==========

    /**
     * Adds a texture to the library.
     * If a texture with the same ID already exists, it will be replaced.
     *
     * @param info the TextureInfo to add
     * @throws IllegalArgumentException if info is null or has no ID
     */
    public void addTexture(TextureManager.TextureInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("TextureInfo cannot be null");
        }
        if (info.getTextureId() == null || info.getTextureId().isEmpty()) {
            throw new IllegalArgumentException("TextureInfo must have a valid ID");
        }
        textures.put(info.getTextureId(), info);
    }

    /**
     * Removes a texture from the library by its ID.
     *
     * @param textureId the ID of the texture to remove
     * @return true if the texture was found and removed
     */
    public boolean removeTexture(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return false;
        }
        return textures.remove(textureId) != null;
    }

    // ========== Get ==========

    /**
     * Gets a texture by its ID.
     *
     * @param textureId the texture ID
     * @return the TextureInfo, or null if not found
     */
    public TextureManager.TextureInfo getTexture(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return null;
        }
        return textures.get(textureId);
    }

    /**
     * Returns all textures in the library.
     *
     * @return unmodifiable list of all TextureInfo objects
     */
    public List<TextureManager.TextureInfo> getAllTextures() {
        return Collections.unmodifiableList(new ArrayList<>(textures.values()));
    }

    // ========== Search / Filter ==========

    /**
     * Searches for textures whose name contains the query (case-insensitive).
     *
     * @param query the search query
     * @return list of matching TextureInfo objects
     */
    public List<TextureManager.TextureInfo> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllTextures();
        }

        String lowerQuery = query.toLowerCase(Locale.US);
        List<TextureManager.TextureInfo> results = new ArrayList<>();
        for (TextureManager.TextureInfo info : textures.values()) {
            if (info.getName().toLowerCase(Locale.US).contains(lowerQuery) ||
                    info.getFormat().toLowerCase(Locale.US).contains(lowerQuery) ||
                    info.getFilePath().toLowerCase(Locale.US).contains(lowerQuery)) {
                results.add(info);
            }
        }
        return results;
    }

    /**
     * Returns all textures of a specific format.
     *
     * @param format the format to filter by (e.g., "PNG", "JPG")
     * @return list of matching TextureInfo objects
     */
    public List<TextureManager.TextureInfo> getByFormat(String format) {
        if (format == null || format.isEmpty()) {
            return Collections.emptyList();
        }

        String upperFormat = format.toUpperCase(Locale.US);
        List<TextureManager.TextureInfo> results = new ArrayList<>();
        for (TextureManager.TextureInfo info : textures.values()) {
            if (upperFormat.equalsIgnoreCase(info.getFormat())) {
                results.add(info);
            }
        }
        return results;
    }

    // ========== Sort ==========

    /**
     * Sorts the internal texture list by the specified criterion.
     *
     * @param sortBy one of SORT_BY_NAME, SORT_BY_DATE, SORT_BY_SIZE
     */
    public void sortBy(String sortBy) {
        if (sortBy == null) {
            sortBy = SORT_BY_NAME;
        }

        this.currentSortMode = sortBy;

        List<Map.Entry<String, TextureManager.TextureInfo>> entries =
                new ArrayList<>(textures.entrySet());

        Comparator<Map.Entry<String, TextureManager.TextureInfo>> comparator;
        switch (sortBy) {
            case SORT_BY_DATE:
                comparator = new Comparator<Map.Entry<String, TextureManager.TextureInfo>>() {
                    @Override
                    public int compare(Map.Entry<String, TextureManager.TextureInfo> o1,
                                       Map.Entry<String, TextureManager.TextureInfo> o2) {
                        long diff = o2.getValue().getAddedAt() - o1.getValue().getAddedAt();
                        return Long.compare(diff, 0);
                    }
                };
                break;
            case SORT_BY_SIZE:
                comparator = new Comparator<Map.Entry<String, TextureManager.TextureInfo>>() {
                    @Override
                    public int compare(Map.Entry<String, TextureManager.TextureInfo> o1,
                                       Map.Entry<String, TextureManager.TextureInfo> o2) {
                        long diff = o2.getValue().getFileSize() - o1.getValue().getFileSize();
                        return Long.compare(diff, 0);
                    }
                };
                break;
            case SORT_BY_NAME:
            default:
                comparator = new Comparator<Map.Entry<String, TextureManager.TextureInfo>>() {
                    @Override
                    public int compare(Map.Entry<String, TextureManager.TextureInfo> o1,
                                       Map.Entry<String, TextureManager.TextureInfo> o2) {
                        return o1.getValue().getName().compareToIgnoreCase(o2.getValue().getName());
                    }
                };
                break;
        }

        Collections.sort(entries, comparator);

        // Rebuild the map with sorted order
        textures.clear();
        for (Map.Entry<String, TextureManager.TextureInfo> entry : entries) {
            textures.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Returns the current sort mode.
     *
     * @return the current sort mode string
     */
    public String getCurrentSortMode() {
        return currentSortMode;
    }

    // ========== Query ==========

    /**
     * Checks if the library contains a texture with the given ID.
     *
     * @param textureId the texture ID to check
     * @return true if the texture exists in the library
     */
    public boolean contains(String textureId) {
        if (textureId == null || textureId.isEmpty()) {
            return false;
        }
        return textures.containsKey(textureId);
    }

    /**
     * Gets the file path for a texture by its ID.
     *
     * @param textureId the texture ID
     * @return the file path, or null if not found
     */
    public String getTexturePath(String textureId) {
        TextureManager.TextureInfo info = getTexture(textureId);
        return info != null ? info.getFilePath() : null;
    }

    /**
     * Returns the total number of textures in the library.
     *
     * @return texture count
     */
    public int getTextureCount() {
        return textures.size();
    }

    // ========== Storage Integration ==========

    /**
     * Loads all textures from storage into the library.
     *
     * @param storage the TextureStorage to load from
     */
    public void loadFromStorage(TextureStorage storage) {
        if (storage == null) {
            return;
        }

        List<String> textureIds = storage.listTextureIds();
        for (String textureId : textureIds) {
            File textureFile = storage.getTextureFile(textureId);
            if (textureFile != null && textureFile.exists()) {
                TextureManager.TextureInfo info = new TextureManager.TextureInfo();
                info.setTextureId(textureId);
                info.setName(textureFile.getName());
                info.setFilePath(textureFile.getAbsolutePath());

                String filename = textureFile.getName().toLowerCase(Locale.US);
                if (filename.endsWith(".png")) {
                    info.setFormat("PNG");
                } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                    info.setFormat("JPG");
                } else if (filename.endsWith(".webp")) {
                    info.setFormat("WEBP");
                } else {
                    info.setFormat("UNKNOWN");
                }

                info.setFileSize(textureFile.length());
                info.setAddedAt(textureFile.lastModified());

                // Get dimensions
                android.graphics.BitmapFactory.Options options =
                        new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(
                        textureFile.getAbsolutePath(), options);
                info.setWidth(options.outWidth);
                info.setHeight(options.outHeight);

                textures.put(textureId, info);
            }
        }
    }

    /**
     * Clears all textures from the library.
     */
    public void clear() {
        textures.clear();
    }

    /**
     * Returns the number of textures that match a given format.
     *
     * @param format the format to count
     * @return count of textures with that format
     */
    public int getCountByFormat(String format) {
        return getByFormat(format).size();
    }

    /**
     * Returns the total file size of all textures in the library.
     *
     * @return total size in bytes
     */
    public long getTotalFileSize() {
        long total = 0;
        for (TextureManager.TextureInfo info : textures.values()) {
            total += info.getFileSize();
        }
        return total;
    }

    @Override
    public String toString() {
        return "TextureLibrary{count=" + textures.size() +
                ", sortMode='" + currentSortMode + "'}";
    }
}
