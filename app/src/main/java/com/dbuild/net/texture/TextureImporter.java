package com.dbuild.net.texture;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles importing textures from various sources including files,
 * content URIs, and gallery intents. Provides validation, processing
 * (resize if too large), thumbnail generation, and deduplication.
 */
public class TextureImporter {

    private static final int MAX_TEXTURE_SIZE = 2048;
    private static final int THUMBNAIL_SIZE = 128;

    private final Context context;

    /**
     * Constructs a TextureImporter with the given context.
     *
     * @param context the Android context
     */
    public TextureImporter(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
    }

    // ========== Import Methods ==========

    /**
     * Imports a texture from a file source.
     * Validates the image, processes it, generates a thumbnail,
     * and stores it via TextureStorage.
     *
     * @param source the source file to import
     * @return TextureInfo for the imported texture, or null on failure
     */
    public TextureManager.TextureInfo importFromFile(File source) {
        if (source == null || !source.exists() || !source.canRead()) {
            return null;
        }

        // Validate
        if (!validateImage(source)) {
            return null;
        }

        // Check for duplicates
        if (isDuplicate(source)) {
            return null;
        }

        // Generate a unique texture ID
        String textureId = UUID.randomUUID().toString();

        // Process and store
        TextureStorage storage = new TextureStorage(context);
        File destFile = storage.saveTexture(source, textureId);
        if (destFile == null) {
            return null;
        }

        // Process texture (resize if needed)
        processTexture(destFile, destFile);

        // Generate thumbnail
        File thumbFile = storage.getThumbnailFile(textureId);
        Bitmap thumbnail = generateThumbnail(destFile, thumbFile);

        // Get dimensions
        int[] dimensions = getTextureDimensions(destFile);

        // Build TextureInfo
        String format = getFormatFromFilename(source.getName());
        String name = source.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }

        TextureManager.TextureInfo info = new TextureManager.TextureInfo();
        info.setTextureId(textureId);
        info.setName(name);
        info.setFilePath(destFile.getAbsolutePath());
        info.setFormat(format);
        info.setWidth(dimensions[0]);
        info.setHeight(dimensions[1]);
        info.setFileSize(destFile.length());
        info.setAddedAt(System.currentTimeMillis());

        if (thumbnail != null && !thumbnail.isRecycled()) {
            thumbnail.recycle();
        }

        return info;
    }

    /**
     * Imports a texture from a content:// URI.
     *
     * @param uri the content URI to import from
     * @return TextureInfo for the imported texture, or null on failure
     */
    public TextureManager.TextureInfo importFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }

        try {
            // Copy URI content to a temp file
            File tempFile = copyUriToTempFile(uri);
            if (tempFile == null) {
                return null;
            }

            // Import from the temp file
            TextureManager.TextureInfo info = importFromFile(tempFile);

            // Clean up temp file
            tempFile.delete();

            // Update name from URI if possible
            if (info != null) {
                String uriName = getDisplayNameFromUri(uri);
                if (uriName != null && !uriName.isEmpty()) {
                    int dotIndex = uriName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        info.setName(uriName.substring(0, dotIndex));
                    } else {
                        info.setName(uriName);
                    }
                }
            }

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Imports a texture from a gallery intent result.
     *
     * @param data the Intent data returned from the gallery picker
     * @return TextureInfo for the imported texture, or null on failure
     */
    public TextureManager.TextureInfo importFromGallery(Intent data) {
        if (data == null) {
            return null;
        }

        Uri uri = data.getData();
        if (uri == null) {
            return null;
        }

        return importFromUri(uri);
    }

    /**
     * Imports multiple textures from a gallery intent that returned
     * multiple image URIs.
     *
     * @param data the Intent data with multiple selections
     * @return list of TextureInfo objects for successfully imported textures
     */
    public List<TextureManager.TextureInfo> importMultipleFromGallery(Intent data) {
        List<TextureManager.TextureInfo> results = new ArrayList<>();
        if (data == null) {
            return results;
        }

        // Check for multiple selections (clip data)
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                if (uri != null) {
                    TextureManager.TextureInfo info = importFromUri(uri);
                    if (info != null) {
                        results.add(info);
                    }
                }
            }
        } else {
            // Single selection
            TextureManager.TextureInfo info = importFromGallery(data);
            if (info != null) {
                results.add(info);
            }
        }

        return results;
    }

    // ========== Validation ==========

    /**
     * Checks if a file is a valid, decodable image.
     *
     * @param file the file to validate
     * @return true if the file is a valid image
     */
    public boolean validateImage(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return false;
        }

        if (file.length() == 0) {
            return false;
        }

        // Check format from filename
        if (!validateFormat(file.getName())) {
            return false;
        }

        // Try to decode just the bounds to verify it's a real image
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        return options.outWidth > 0 && options.outHeight > 0;
    }

    /**
     * Validates that the filename has a supported image format extension.
     * Supported formats: PNG, JPG, JPEG, WEBP.
     *
     * @param filename the filename to validate
     * @return true if the format is supported
     */
    public boolean validateFormat(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        String lower = filename.toLowerCase(Locale.US);
        return lower.endsWith(".png") ||
               lower.endsWith(".jpg") ||
               lower.endsWith(".jpeg") ||
               lower.endsWith(".webp");
    }

    /**
     * Gets the format string from a filename.
     *
     * @param filename the filename to extract the format from
     * @return the format string ("PNG", "JPG", "JPEG", "WEBP", or "UNKNOWN")
     */
    public String getFormatFromFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String lower = filename.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) {
            return "PNG";
        } else if (lower.endsWith(".jpeg")) {
            return "JPEG";
        } else if (lower.endsWith(".jpg")) {
            return "JPG";
        } else if (lower.endsWith(".webp")) {
            return "WEBP";
        }
        return "UNKNOWN";
    }

    // ========== Processing ==========

    /**
     * Processes a texture file, resizing it if it exceeds the maximum
     * allowed dimensions (2048x2048).
     *
     * @param source the source texture file
     * @param dest   the destination file (may be the same as source)
     * @return true if processing succeeded
     */
    public boolean processTexture(File source, File dest) {
        if (source == null || !source.exists()) {
            return false;
        }

        int[] dimensions = getTextureDimensions(source);
        if (dimensions[0] <= 0 || dimensions[1] <= 0) {
            return false;
        }

        // Check if resize is needed
        if (dimensions[0] <= MAX_TEXTURE_SIZE && dimensions[1] <= MAX_TEXTURE_SIZE) {
            // No resize needed, just copy if source != dest
            if (!source.equals(dest)) {
                return copyFile(source, dest);
            }
            return true;
        }

        // Calculate new dimensions maintaining aspect ratio
        int newWidth = dimensions[0];
        int newHeight = dimensions[1];

        if (newWidth > MAX_TEXTURE_SIZE) {
            float ratio = (float) MAX_TEXTURE_SIZE / newWidth;
            newWidth = MAX_TEXTURE_SIZE;
            newHeight = Math.round(newHeight * ratio);
        }
        if (newHeight > MAX_TEXTURE_SIZE) {
            float ratio = (float) MAX_TEXTURE_SIZE / newHeight;
            newHeight = MAX_TEXTURE_SIZE;
            newWidth = Math.round(newWidth * ratio);
        }

        // Decode and resize
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap original = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (original == null) {
            return false;
        }

        Bitmap resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);

        FileOutputStream fos = null;
        try {
            File parentDir = dest.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            fos = new FileOutputStream(dest);
            Bitmap.CompressFormat compressFormat = getCompressFormat(dest.getName());
            resized.compress(compressFormat, 95, fos);
            fos.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
            if (original != null && !original.isRecycled() && original != resized) {
                original.recycle();
            }
            if (resized != null && !resized.isRecycled()) {
                resized.recycle();
            }
        }
    }

    /**
     * Generates a 128x128 thumbnail from the source texture.
     *
     * @param source the source texture file
     * @param dest   the destination thumbnail file
     * @return the generated thumbnail Bitmap
     */
    public Bitmap generateThumbnail(File source, File dest) {
        if (source == null || !source.exists()) {
            return null;
        }

        // Decode with sampling for efficiency
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap original = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (original == null) {
            return null;
        }

        Bitmap thumbnail = Bitmap.createScaledBitmap(original, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true);

        // Save thumbnail to file if dest is provided
        if (dest != null) {
            FileOutputStream fos = null;
            try {
                File parentDir = dest.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                fos = new FileOutputStream(dest);
                thumbnail.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
            } catch (IOException e) {
                // Thumbnail save failed, but still return the bitmap
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        if (original != null && !original.isRecycled() && original != thumbnail) {
            original.recycle();
        }

        return thumbnail;
    }

    /**
     * Gets the dimensions of a texture file without fully decoding it.
     *
     * @param file the texture file
     * @return int array with [width, height], or [0, 0] on failure
     */
    public int[] getTextureDimensions(File file) {
        if (file == null || !file.exists()) {
            return new int[]{0, 0};
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        return new int[]{options.outWidth, options.outHeight};
    }

    // ========== Deduplication ==========

    /**
     * Calculates the MD5 hash of a file for deduplication purposes.
     *
     * @param file the file to hash
     * @return the hex string of the MD5 hash, or null on failure
     */
    public String calculateTextureHash(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        FileInputStream fis = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Checks if a file is a duplicate of an existing texture.
     * Compares file hashes for deduplication.
     *
     * @param file the file to check
     * @return true if the file's hash matches an existing texture
     */
    public boolean isDuplicate(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        String hash = calculateTextureHash(file);
        if (hash == null) {
            return false;
        }

        TextureManager manager = TextureManager.getInstance(context);
        List<TextureManager.TextureInfo> existingTextures = manager.getAllTextures();

        for (TextureManager.TextureInfo info : existingTextures) {
            File existingFile = new File(info.getFilePath());
            if (existingFile.exists()) {
                String existingHash = calculateTextureHash(existingFile);
                if (hash.equals(existingHash)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ========== Helper Methods ==========

    /**
     * Copies a URI's content to a temporary file.
     *
     * @param uri the content URI
     * @return the temp file, or null on failure
     */
    private File copyUriToTempFile(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        InputStream is = null;
        OutputStream os = null;

        try {
            is = resolver.openInputStream(uri);
            if (is == null) {
                return null;
            }

            // Determine extension from URI
            String displayName = getDisplayNameFromUri(uri);
            String extension = ".png";
            if (displayName != null) {
                int dotIndex = displayName.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = displayName.substring(dotIndex);
                }
            }

            File tempDir = new File(context.getCacheDir(), "texture_import");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            File tempFile = File.createTempFile("import_", extension, tempDir);
            os = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();

            return tempFile;
        } catch (IOException e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Gets the display name from a content URI using the content resolver.
     *
     * @param uri the content URI
     * @return the display name, or null if not available
     */
    private String getDisplayNameFromUri(Uri uri) {
        if (uri == null) return null;

        String displayName = null;
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        if (displayName == null) {
            displayName = uri.getLastPathSegment();
        }

        return displayName;
    }

    /**
     * Copies a file from source to destination.
     *
     * @param source the source file
     * @param dest   the destination file
     * @return true if the copy succeeded
     */
    private boolean copyFile(File source, File dest) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            File parentDir = dest.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            fis = new FileInputStream(source);
            fos = new FileOutputStream(dest);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();
            return true;
        } catch (IOException e) {
            return false;
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
     * Gets the appropriate Bitmap.CompressFormat for a given filename.
     *
     * @param filename the filename to determine format from
     * @return the CompressFormat
     */
    private Bitmap.CompressFormat getCompressFormat(String filename) {
        if (filename == null) {
            return Bitmap.CompressFormat.PNG;
        }

        String lower = filename.toLowerCase(Locale.US);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return Bitmap.CompressFormat.JPEG;
        } else if (lower.endsWith(".webp")) {
            return Bitmap.CompressFormat.WEBP;
        }
        return Bitmap.CompressFormat.PNG;
    }
}
