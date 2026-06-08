package com.dbuild.net.blocks;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Generates isometric preview images for blocks.
 * Draws a 3D-looking isometric cube showing the top, front (south),
 * and right (east) faces with appropriate colors or textures,
 * simple lighting/shading, and grid lines.
 */
public class BlockPreviewGenerator {

    private static final int SMALL_SIZE = 64;
    private static final int LARGE_SIZE = 256;
    private static final int DEFAULT_SIZE = 128;

    /** LRU cache for generated previews keyed by block ID and size. */
    private final LruCache<String, Bitmap> previewCache;

    /** Paint for grid lines. */
    private final Paint gridPaint;

    /** Paint for block faces (solid color). */
    private final Paint facePaint;

    /** Paint for block outlines. */
    private final Paint outlinePaint;

    /** Paint for face shading overlays. */
    private final Paint shadePaint;

    /**
     * Constructs a BlockPreviewGenerator with a default cache size.
     */
    public BlockPreviewGenerator() {
        // Cache up to ~4MB of bitmaps (assuming 256x256 * 4 bytes ~ 256KB each, ~16 entries)
        final int cacheSizeBytes = 4 * 1024 * 1024;
        previewCache = new LruCache<String, Bitmap>(cacheSizeBytes) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

        gridPaint = new Paint();
        gridPaint.setColor(Color.argb(40, 0, 0, 0));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.0f);
        gridPaint.setAntiAlias(true);

        facePaint = new Paint();
        facePaint.setStyle(Paint.Style.FILL);
        facePaint.setAntiAlias(true);

        outlinePaint = new Paint();
        outlinePaint.setColor(Color.argb(120, 0, 0, 0));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(1.5f);
        outlinePaint.setAntiAlias(true);

        shadePaint = new Paint();
        shadePaint.setStyle(Paint.Style.FILL);
        shadePaint.setAntiAlias(true);
    }

    // ========== Public API ==========

    /**
     * Generates a default-size (128x128) preview for a CustomBlock.
     *
     * @param block the block to preview
     * @return a Bitmap of the isometric block preview
     */
    public Bitmap generatePreview(CustomBlock block) {
        if (block == null) {
            return createEmptyPreview(DEFAULT_SIZE);
        }
        return generatePreviewInternal(block, DEFAULT_SIZE);
    }

    /**
     * Generates a default-size preview for a BlockTextureSet.
     *
     * @param textureSet the texture set to preview
     * @return a Bitmap of the isometric preview
     */
    public Bitmap generatePreview(BlockTextureSet textureSet) {
        if (textureSet == null) {
            return createEmptyPreview(DEFAULT_SIZE);
        }
        return generatePreviewFromTextureSet(textureSet, DEFAULT_SIZE);
    }

    /**
     * Generates a small (64x64) preview for a CustomBlock.
     *
     * @param block the block to preview
     * @return a 64x64 Bitmap
     */
    public Bitmap generateSmallPreview(CustomBlock block) {
        if (block == null) {
            return createEmptyPreview(SMALL_SIZE);
        }
        return generatePreviewInternal(block, SMALL_SIZE);
    }

    /**
     * Generates a large (256x256) preview for a CustomBlock.
     *
     * @param block the block to preview
     * @return a 256x256 Bitmap
     */
    public Bitmap generateLargePreview(CustomBlock block) {
        if (block == null) {
            return createEmptyPreview(LARGE_SIZE);
        }
        return generatePreviewInternal(block, LARGE_SIZE);
    }

    /**
     * Saves a preview image to a file.
     *
     * @param block the block to preview
     * @param dest  the destination file
     * @return true if the save succeeded
     */
    public boolean savePreview(CustomBlock block, File dest) {
        Bitmap preview = generateLargePreview(block);
        if (preview == null) {
            return false;
        }
        if (dest == null) {
            return false;
        }

        File parentDir = dest.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dest);
            preview.compress(Bitmap.CompressFormat.PNG, 100, fos);
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
        }
    }

    /**
     * Clears the preview cache.
     */
    public void clearCache() {
        previewCache.evictAll();
    }

    /**
     * Removes a specific block's previews from the cache.
     *
     * @param blockId the block ID to evict
     */
    public void evictFromCache(String blockId) {
        if (blockId == null) return;
        previewCache.remove(blockId + "_small");
        previewCache.remove(blockId + "_default");
        previewCache.remove(blockId + "_large");
    }

    // ========== Internal Rendering ==========

    /**
     * Generates a preview with caching.
     */
    private Bitmap generatePreviewInternal(CustomBlock block, int size) {
        String cacheKey = block.getBlockId() + "_" + size;
        Bitmap cached = previewCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        Bitmap preview = generatePreviewFromTextureSet(block.getTextureSet(), size);
        if (block.isEmissive()) {
            applyEmissiveGlow(preview, block.getEmissiveStrength());
        }

        previewCache.put(cacheKey, preview);
        return preview;
    }

    /**
     * Generates an isometric preview from a texture set.
     */
    private Bitmap generatePreviewFromTextureSet(BlockTextureSet textureSet, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        float cx = size / 2.0f;
        float cy = size / 2.0f;
        float unit = size / 4.5f;

        // Isometric cube vertices
        // Center point of the cube
        float topX = cx;
        float topY = cy - unit * 1.5f;

        float leftX = cx - unit;
        float leftY = cy - unit * 0.5f;

        float rightX = cx + unit;
        float rightY = cy - unit * 0.5f;

        float bottomLeftX = cx - unit;
        float bottomLeftY = cy + unit * 0.5f;

        float bottomRightX = cx + unit;
        float bottomRightY = cy + unit * 0.5f;

        float farLeftX = cx;
        float farLeftY = cy + unit * 1.5f;

        float bottomX = cx;
        float bottomY = cy + unit * 0.5f;

        // Top face color (lightest)
        int topColor = getFaceColor(textureSet, CustomBlock.BlockFace.TOP, 1.0f);
        // Front (south) face color (medium)
        int frontColor = getFaceColor(textureSet, CustomBlock.BlockFace.SOUTH, 0.75f);
        // Right (east) face color (darkest)
        int rightColor = getFaceColor(textureSet, CustomBlock.BlockFace.EAST, 0.55f);

        // Draw top face
        Path topPath = new Path();
        topPath.moveTo(topX, topY);
        topPath.lineTo(rightX, rightY);
        topPath.lineTo(bottomX, bottomY);
        topPath.lineTo(leftX, leftY);
        topPath.close();

        facePaint.setColor(topColor);
        canvas.drawPath(topPath, facePaint);
        drawGridOnFace(canvas, topPath, topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY, 4);
        canvas.drawPath(topPath, outlinePaint);

        // Draw front (south) face
        Path frontPath = new Path();
        frontPath.moveTo(leftX, leftY);
        frontPath.lineTo(bottomX, bottomY);
        frontPath.lineTo(farLeftX, farLeftY);
        frontPath.lineTo(bottomLeftX, bottomLeftY);
        frontPath.close();

        facePaint.setColor(frontColor);
        canvas.drawPath(frontPath, facePaint);
        drawGridOnFace(canvas, frontPath, leftX, leftY, bottomX, bottomY, farLeftX, farLeftY, bottomLeftX, bottomLeftY, 4);
        canvas.drawPath(frontPath, outlinePaint);

        // Draw right (east) face
        Path rightPath = new Path();
        rightPath.moveTo(rightX, rightY);
        rightPath.lineTo(bottomRightX, bottomRightY);
        rightPath.lineTo(farLeftX, farLeftY);
        rightPath.lineTo(bottomX, bottomY);
        rightPath.close();

        facePaint.setColor(rightColor);
        canvas.drawPath(rightPath, facePaint);
        drawGridOnFace(canvas, rightPath, rightX, rightY, bottomRightX, bottomRightY, farLeftX, farLeftY, bottomX, bottomY, 4);
        canvas.drawPath(rightPath, outlinePaint);

        return bitmap;
    }

    /**
     * Gets the color for a face, applying a brightness multiplier for shading.
     */
    private int getFaceColor(BlockTextureSet textureSet, CustomBlock.BlockFace face, float brightness) {
        float[] defaultColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        float r = defaultColor[0];
        float g = defaultColor[1];
        float b = defaultColor[2];
        float a = defaultColor[3];

        // Use a default gray-white color; if the texture set has a color it would be applied here
        r = r * brightness;
        g = g * brightness;
        b = b * brightness;

        int ri = Math.min(255, Math.max(0, Math.round(r * 255)));
        int gi = Math.min(255, Math.max(0, Math.round(g * 255)));
        int bi = Math.min(255, Math.max(0, Math.round(b * 255)));
        int ai = Math.min(255, Math.max(0, Math.round(a * 255)));

        return Color.argb(ai, ri, gi, bi);
    }

    /**
     * Applies a face color from the CustomBlock's custom color with brightness adjustment.
     */
    private int applyColorWithBrightness(float[] rgba, float brightness) {
        float r = rgba[0] * brightness;
        float g = rgba[1] * brightness;
        float b = rgba[2] * brightness;
        float a = rgba[3];

        int ri = Math.min(255, Math.max(0, Math.round(r * 255)));
        int gi = Math.min(255, Math.max(0, Math.round(g * 255)));
        int bi = Math.min(255, Math.max(0, Math.round(b * 255)));
        int ai = Math.min(255, Math.max(0, Math.round(a * 255)));

        return Color.argb(ai, ri, gi, bi);
    }

    /**
     * Generates a preview that uses the block's custom color directly.
     */
    public Bitmap generatePreviewWithColor(CustomBlock block, int size) {
        if (block == null) {
            return createEmptyPreview(size);
        }

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        float cx = size / 2.0f;
        float cy = size / 2.0f;
        float unit = size / 4.5f;

        float topX = cx;
        float topY = cy - unit * 1.5f;
        float leftX = cx - unit;
        float leftY = cy - unit * 0.5f;
        float rightX = cx + unit;
        float rightY = cy - unit * 0.5f;
        float bottomX = cx;
        float bottomY = cy + unit * 0.5f;
        float bottomLeftX = cx - unit;
        float bottomLeftY = cy + unit * 0.5f;
        float bottomRightX = cx + unit;
        float bottomRightY = cy + unit * 0.5f;
        float farLeftX = cx;
        float farLeftY = cy + unit * 1.5f;

        float[] color = block.getCustomColor();

        // Draw top face (brightest)
        Path topPath = new Path();
        topPath.moveTo(topX, topY);
        topPath.lineTo(rightX, rightY);
        topPath.lineTo(bottomX, bottomY);
        topPath.lineTo(leftX, leftY);
        topPath.close();
        facePaint.setColor(applyColorWithBrightness(color, 1.0f));
        canvas.drawPath(topPath, facePaint);
        drawGridOnFace(canvas, topPath, topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY, 4);
        canvas.drawPath(topPath, outlinePaint);

        // Draw front face (medium)
        Path frontPath = new Path();
        frontPath.moveTo(leftX, leftY);
        frontPath.lineTo(bottomX, bottomY);
        frontPath.lineTo(farLeftX, farLeftY);
        frontPath.lineTo(bottomLeftX, bottomLeftY);
        frontPath.close();
        facePaint.setColor(applyColorWithBrightness(color, 0.75f));
        canvas.drawPath(frontPath, facePaint);
        drawGridOnFace(canvas, frontPath, leftX, leftY, bottomX, bottomY, farLeftX, farLeftY, bottomLeftX, bottomLeftY, 4);
        canvas.drawPath(frontPath, outlinePaint);

        // Draw right face (darkest)
        Path rightPath = new Path();
        rightPath.moveTo(rightX, rightY);
        rightPath.lineTo(bottomRightX, bottomRightY);
        rightPath.lineTo(farLeftX, farLeftY);
        rightPath.lineTo(bottomX, bottomY);
        rightPath.close();
        facePaint.setColor(applyColorWithBrightness(color, 0.55f));
        canvas.drawPath(rightPath, facePaint);
        drawGridOnFace(canvas, rightPath, rightX, rightY, bottomRightX, bottomRightY, farLeftX, farLeftY, bottomX, bottomY, 4);
        canvas.drawPath(rightPath, outlinePaint);

        if (block.isEmissive()) {
            applyEmissiveGlow(bitmap, block.getEmissiveStrength());
        }

        return bitmap;
    }

    /**
     * Draws grid lines on an isometric face.
     * The grid lines subdivide the face into a grid pattern.
     */
    private void drawGridOnFace(Canvas canvas, Path facePath,
                                float x0, float y0, float x1, float y1,
                                float x2, float y2, float x3, float y3,
                                int divisions) {
        if (divisions <= 1) return;

        float oldStrokeWidth = gridPaint.getStrokeWidth();
        float strokeWidth = Math.max(0.5f, oldStrokeWidth);

        // Draw lines from edge 0->1 to edge 3->2
        for (int i = 1; i < divisions; i++) {
            float t = (float) i / divisions;
            float sx = lerp(x0, x1, t);
            float sy = lerp(y0, y1, t);
            float ex = lerp(x3, x2, t);
            float ey = lerp(y3, y2, t);
            canvas.drawLine(sx, sy, ex, ey, gridPaint);
        }

        // Draw lines from edge 0->3 to edge 1->2
        for (int i = 1; i < divisions; i++) {
            float t = (float) i / divisions;
            float sx = lerp(x0, x3, t);
            float sy = lerp(y0, y3, t);
            float ex = lerp(x1, x2, t);
            float ey = lerp(y1, y2, t);
            canvas.drawLine(sx, sy, ex, ey, gridPaint);
        }
    }

    /**
     * Linear interpolation between two values.
     */
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Applies an emissive glow effect to the preview bitmap.
     */
    private void applyEmissiveGlow(Bitmap bitmap, float strength) {
        if (bitmap == null || bitmap.isRecycled()) return;

        int size = bitmap.getWidth();
        Canvas canvas = new Canvas(bitmap);

        float clampedStrength = Math.min(1.0f, Math.max(0.0f, strength));
        int glowAlpha = Math.round(clampedStrength * 80);

        Paint glowPaint = new Paint();
        glowPaint.setColor(Color.argb(glowAlpha, 255, 240, 180));
        glowPaint.setStyle(Paint.Style.FILL);

        float cx = size / 2.0f;
        float cy = size / 2.0f;
        float radius = size / 3.0f;

        // Draw a soft radial glow
        canvas.drawCircle(cx, cy, radius, glowPaint);
    }

    /**
     * Creates an empty transparent preview bitmap.
     */
    private Bitmap createEmptyPreview(int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // Draw a faint question mark or placeholder indicator
        Paint placeholderPaint = new Paint();
        placeholderPaint.setColor(Color.argb(60, 128, 128, 128));
        placeholderPaint.setTextSize(size * 0.5f);
        placeholderPaint.setAntiAlias(true);
        placeholderPaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("?", size / 2.0f, size * 0.65f, placeholderPaint);
        return bitmap;
    }
}
