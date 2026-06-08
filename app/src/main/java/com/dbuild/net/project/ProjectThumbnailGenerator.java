package com.dbuild.net.project;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;
import android.util.Log;

import com.dbuild.net.model.Block3D;
import com.dbuild.net.model.Scene;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Generates preview thumbnail images for projects.
 * Creates a simple isometric view of the scene's blocks on a Canvas,
 * with a gradient background, grid floor, and shaded cubes.
 * Thumbnails are cached in an in-memory LRU cache.
 */
public class ProjectThumbnailGenerator {

    private static final String TAG = "ProjectThumbnailGen";

    /** Thumbnail dimensions in pixels. */
    private static final int THUMBNAIL_SIZE = 256;

    /** LRU cache capacity. */
    private static final int CACHE_CAPACITY = 50;

    /** Isometric projection constants. */
    private static final float ISO_ANGLE = (float) Math.toRadians(30);
    private static final float COS_30 = (float) Math.cos(ISO_ANGLE);
    private static final float SIN_30 = (float) Math.sin(ISO_ANGLE);

    /** Scale factor for block rendering in the isometric view. */
    private static final float BLOCK_SCALE = 12.0f;

    /** Shading multipliers for different face sides. */
    private static final float SHADE_TOP = 1.0f;
    private static final float SHADE_LEFT = 0.75f;
    private static final float SHADE_RIGHT = 0.55f;

    /** Grid line color. */
    private static final int GRID_COLOR = Color.argb(40, 255, 255, 255);

    /** Background gradient colors. */
    private static final int BG_TOP_COLOR = Color.rgb(45, 55, 85);
    private static final int BG_BOTTOM_COLOR = Color.rgb(25, 30, 50);

    private final ProjectStorage storage;

    /** In-memory LRU cache for thumbnail bitmaps. */
    private final LruCache<String, Bitmap> thumbnailCache;

    /** Reusable Paint objects to avoid allocation during drawing. */
    private final Paint gridPaint;
    private final Paint blockPaint;
    private final Paint shadowPaint;
    private final Paint bgPaint;

    /**
     * Constructs a ProjectThumbnailGenerator with the given context.
     *
     * @param context the application context
     */
    public ProjectThumbnailGenerator(android.content.Context context) {
        this.storage = new ProjectStorage(context);

        this.thumbnailCache = new LruCache<String, Bitmap>(CACHE_CAPACITY) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return 1; // Count entries, not bytes
            }

            @Override
            protected void entryRemoved(boolean evicted, String key,
                                        Bitmap oldValue, Bitmap newValue) {
                if (evicted && oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };

        // Initialize reusable paint objects
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStrokeWidth(1.0f);
        gridPaint.setStyle(Paint.Style.STROKE);

        blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blockPaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(50, 0, 0, 0));

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    /**
     * Constructs a ProjectThumbnailGenerator with an existing ProjectStorage.
     *
     * @param storage the ProjectStorage to use for file I/O
     */
    public ProjectThumbnailGenerator(ProjectStorage storage) {
        this.storage = storage;
        this.thumbnailCache = new LruCache<String, Bitmap>(CACHE_CAPACITY) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return 1;
            }

            @Override
            protected void entryRemoved(boolean evicted, String key,
                                        Bitmap oldValue, Bitmap newValue) {
                if (evicted && oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStrokeWidth(1.0f);
        gridPaint.setStyle(Paint.Style.STROKE);

        blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blockPaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(50, 0, 0, 0));

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    /**
     * Generates a thumbnail bitmap for a project's scene.
     * This method does NOT save the thumbnail to disk.
     *
     * @param projectId the project ID (used for cache key)
     * @param scene     the scene to render
     * @return a 256x256 Bitmap of the isometric scene view
     */
    public Bitmap generateThumbnail(String projectId, Scene scene) {
        Bitmap bitmap = Bitmap.createBitmap(THUMBNAIL_SIZE, THUMBNAIL_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Draw background gradient
        drawBackground(canvas);

        // Draw grid floor
        drawGridFloor(canvas);

        if (scene == null || scene.getBlockCount() == 0) {
            // Draw an empty scene indicator
            drawEmptyIndicator(canvas);
            return bitmap;
        }

        // Get blocks sorted for isometric rendering (painter's algorithm)
        List<Block3D> blocks = getSortedBlocks(scene);

        // Calculate center offset so blocks are centered in the thumbnail
        float centerX = THUMBNAIL_SIZE / 2.0f;
        float centerY = THUMBNAIL_SIZE * 0.55f; // Slightly below center for better framing

        // Draw shadow layer
        for (Block3D block : blocks) {
            drawBlockShadow(canvas, block, centerX, centerY);
        }

        // Draw blocks (top face, left face, right face)
        for (Block3D block : blocks) {
            drawBlock(canvas, block, centerX, centerY);
        }

        return bitmap;
    }

    /**
     * Saves a thumbnail bitmap to the project's preview.png file
     * and updates the in-memory cache.
     *
     * @param projectId the project ID
     * @param thumbnail the bitmap to save
     * @return true if saved successfully
     */
    public boolean saveThumbnail(String projectId, Bitmap thumbnail) {
        if (projectId == null || thumbnail == null) {
            return false;
        }

        File previewFile = storage.getProjectFile(projectId, storage.getFileNamePreview());
        File parentDir = previewFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(previewFile))) {
            boolean compressed = thumbnail.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();

            if (compressed) {
                // Update cache
                thumbnailCache.put(projectId, thumbnail);
                Log.i(TAG, "Thumbnail saved for project: " + projectId);
                return true;
            } else {
                Log.e(TAG, "Failed to compress thumbnail for project: " + projectId);
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save thumbnail for project: " + projectId, e);
            return false;
        }
    }

    /**
     * Loads a thumbnail bitmap from the project's preview.png file.
     * Returns the cached version if available.
     *
     * @param projectId the project ID
     * @return the thumbnail bitmap, or null if not available
     */
    public Bitmap loadThumbnail(String projectId) {
        if (projectId == null) {
            return null;
        }

        // Check cache first
        Bitmap cached = thumbnailCache.get(projectId);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        // Load from file
        File previewFile = storage.getProjectFile(projectId, storage.getFileNamePreview());
        if (!previewFile.exists() || previewFile.length() == 0) {
            return null;
        }

        try {
            // Read file into byte array first to avoid streaming issues
            java.io.FileInputStream fis = new java.io.FileInputStream(previewFile);
            Bitmap bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(fis.getFD());
            fis.close();

            if (bitmap != null) {
                thumbnailCache.put(projectId, bitmap);
            }
            return bitmap;
        } catch (IOException e) {
            Log.e(TAG, "Failed to load thumbnail for project: " + projectId, e);
            return null;
        }
    }

    /**
     * Generates and saves a thumbnail in one operation.
     *
     * @param projectId the project ID
     * @param scene     the scene to render
     * @return the generated thumbnail bitmap
     */
    public Bitmap generateAndSave(String projectId, Scene scene) {
        Bitmap thumbnail = generateThumbnail(projectId, scene);
        if (thumbnail != null) {
            saveThumbnail(projectId, thumbnail);
        }
        return thumbnail;
    }

    /**
     * Clears a cached thumbnail for a specific project.
     *
     * @param projectId the project ID
     */
    public void clearCache(String projectId) {
        thumbnailCache.remove(projectId);
    }

    /**
     * Clears all cached thumbnails.
     */
    public void clearAllCache() {
        thumbnailCache.evictAll();
    }

    /**
     * Returns the current number of cached thumbnails.
     */
    public int getCacheSize() {
        return thumbnailCache.size();
    }

    // =========================================================================
    // Private Drawing Methods
    // =========================================================================

    /**
     * Draws the gradient background.
     */
    private void drawBackground(Canvas canvas) {
        LinearGradient gradient = new LinearGradient(
                0, 0, 0, THUMBNAIL_SIZE,
                BG_TOP_COLOR, BG_BOTTOM_COLOR,
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(gradient);
        canvas.drawRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, bgPaint);
        bgPaint.setShader(null);
    }

    /**
     * Draws the isometric grid floor.
     */
    private void drawGridFloor(Canvas canvas) {
        float centerX = THUMBNAIL_SIZE / 2.0f;
        float centerY = THUMBNAIL_SIZE * 0.55f;
        int gridSize = 8;

        for (int i = -gridSize; i <= gridSize; i++) {
            // Lines along X axis in grid space
            float[] start1 = isoProject(i * BLOCK_SCALE, 0, -gridSize * BLOCK_SCALE, centerX, centerY);
            float[] end1 = isoProject(i * BLOCK_SCALE, 0, gridSize * BLOCK_SCALE, centerX, centerY);
            canvas.drawLine(start1[0], start1[1], end1[0], end1[1], gridPaint);

            // Lines along Z axis in grid space
            float[] start2 = isoProject(-gridSize * BLOCK_SCALE, 0, i * BLOCK_SCALE, centerX, centerY);
            float[] end2 = isoProject(gridSize * BLOCK_SCALE, 0, i * BLOCK_SCALE, centerX, centerY);
            canvas.drawLine(start2[0], start2[1], end2[0], end2[1], gridPaint);
        }
    }

    /**
     * Draws an indicator for empty scenes.
     */
    private void drawEmptyIndicator(Canvas canvas) {
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.argb(120, 255, 255, 255));
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("Empty", THUMBNAIL_SIZE / 2.0f, THUMBNAIL_SIZE / 2.0f, textPaint);
    }

    /**
     * Gets blocks sorted for isometric rendering using painter's algorithm.
     * Blocks are sorted by Y (ascending), then Z (ascending), then X (ascending)
     * so that blocks in front and above are drawn last.
     */
    private List<Block3D> getSortedBlocks(Scene scene) {
        List<Block3D> blocks = new ArrayList<>(scene.getBlocksList());
        Collections.sort(blocks, new Comparator<Block3D>() {
            @Override
            public int compare(Block3D a, Block3D b) {
                // Sort by Y ascending (lower blocks drawn first)
                if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
                // Then by Z ascending
                if (a.getZ() != b.getZ()) return Integer.compare(a.getZ(), b.getZ());
                // Then by X ascending
                return Integer.compare(a.getX(), b.getX());
            }
        });
        return blocks;
    }

    /**
     * Draws a shadow for a block on the ground plane.
     */
    private void drawBlockShadow(Canvas canvas, Block3D block, float centerX, float centerY) {
        float x = block.getX() * BLOCK_SCALE;
        float y = block.getY() * BLOCK_SCALE;
        float z = block.getZ() * BLOCK_SCALE;

        // Shadow projected onto the Y=0 plane, offset slightly
        float shadowOffsetX = 3.0f;
        float shadowOffsetY = 5.0f;

        float[] p0 = isoProject(x - BLOCK_SCALE / 2 + shadowOffsetX, 0,
                z - BLOCK_SCALE / 2 + shadowOffsetY, centerX, centerY);
        float[] p1 = isoProject(x + BLOCK_SCALE / 2 + shadowOffsetX, 0,
                z - BLOCK_SCALE / 2 + shadowOffsetY, centerX, centerY);
        float[] p2 = isoProject(x + BLOCK_SCALE / 2 + shadowOffsetX, 0,
                z + BLOCK_SCALE / 2 + shadowOffsetY, centerX, centerY);
        float[] p3 = isoProject(x - BLOCK_SCALE / 2 + shadowOffsetX, 0,
                z + BLOCK_SCALE / 2 + shadowOffsetY, centerX, centerY);

        Path shadowPath = new Path();
        shadowPath.moveTo(p0[0], p0[1]);
        shadowPath.lineTo(p1[0], p1[1]);
        shadowPath.lineTo(p2[0], p2[1]);
        shadowPath.lineTo(p3[0], p3[1]);
        shadowPath.close();

        canvas.drawPath(shadowPath, shadowPaint);
    }

    /**
     * Draws an isometric block with three visible faces (top, left, right)
     * and simple directional shading.
     */
    private void drawBlock(Canvas canvas, Block3D block, float centerX, float centerY) {
        float x = block.getX() * BLOCK_SCALE;
        float y = block.getY() * BLOCK_SCALE;
        float z = block.getZ() * BLOCK_SCALE;

        float halfSize = BLOCK_SCALE / 2.0f;

        // Get block color (default to a nice blue if white)
        float[] blockColor = block.getColor();
        int baseColor = Color.rgb(
                (int) (blockColor[0] * 255),
                (int) (blockColor[1] * 255),
                (int) (blockColor[2] * 255)
        );

        // If the block is pure white, give it a default color for visual interest
        if (blockColor[0] >= 0.99f && blockColor[1] >= 0.99f && blockColor[2] >= 0.99f) {
            baseColor = getDefaultBlockColor(block);
        }

        // Calculate isometric positions for the 8 cube corners
        // Bottom face
        float[] b0 = isoProject(x - halfSize, y - halfSize, z - halfSize, centerX, centerY);
        float[] b1 = isoProject(x + halfSize, y - halfSize, z - halfSize, centerX, centerY);
        float[] b2 = isoProject(x + halfSize, y - halfSize, z + halfSize, centerX, centerY);
        float[] b3 = isoProject(x - halfSize, y - halfSize, z + halfSize, centerX, centerY);

        // Top face
        float[] t0 = isoProject(x - halfSize, y + halfSize, z - halfSize, centerX, centerY);
        float[] t1 = isoProject(x + halfSize, y + halfSize, z - halfSize, centerX, centerY);
        float[] t2 = isoProject(x + halfSize, y + halfSize, z + halfSize, centerX, centerY);
        float[] t3 = isoProject(x - halfSize, y + halfSize, z + halfSize, centerX, centerY);

        // Draw left face (darker shade) - the face visible on the left side
        Path leftPath = new Path();
        leftPath.moveTo(b3[0], b3[1]);
        leftPath.lineTo(b2[0], b2[1]);
        leftPath.lineTo(t2[0], t2[1]);
        leftPath.lineTo(t3[0], t3[1]);
        leftPath.close();

        blockPaint.setColor(shadeColor(baseColor, SHADE_LEFT));
        canvas.drawPath(leftPath, blockPaint);

        // Draw left face outline
        Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(1.0f);
        outlinePaint.setColor(Color.argb(30, 0, 0, 0));
        canvas.drawPath(leftPath, outlinePaint);

        // Draw right face (medium shade) - the face visible on the right side
        Path rightPath = new Path();
        rightPath.moveTo(b1[0], b1[1]);
        rightPath.lineTo(b2[0], b2[1]);
        rightPath.lineTo(t2[0], t2[1]);
        rightPath.lineTo(t1[0], t1[1]);
        rightPath.close();

        blockPaint.setColor(shadeColor(baseColor, SHADE_RIGHT));
        canvas.drawPath(rightPath, blockPaint);

        canvas.drawPath(rightPath, outlinePaint);

        // Draw top face (brightest shade)
        Path topPath = new Path();
        topPath.moveTo(t0[0], t0[1]);
        topPath.lineTo(t1[0], t1[1]);
        topPath.lineTo(t2[0], t2[1]);
        topPath.lineTo(t3[0], t3[1]);
        topPath.close();

        blockPaint.setColor(shadeColor(baseColor, SHADE_TOP));
        canvas.drawPath(topPath, blockPaint);

        canvas.drawPath(topPath, outlinePaint);
    }

    /**
     * Returns a default color for blocks that have a pure white color,
     * based on the block's position for visual variety.
     */
    private int getDefaultBlockColor(Block3D block) {
        // Generate a nice color based on position
        int hash = (block.getX() * 7 + block.getY() * 13 + block.getZ() * 23) & 0xFF;
        float hue = (hash / 255.0f) * 360.0f;
        float[] hsv = {hue, 0.5f, 0.8f};
        return Color.HSVToColor(hsv);
    }

    /**
     * Projects a 3D point to 2D isometric coordinates.
     *
     * @param x       3D x coordinate
     * @param y       3D y coordinate
     * @param z       3D z coordinate
     * @param centerX the center x offset on the canvas
     * @param centerY the center y offset on the canvas
     * @return float[2] with screen {x, y} coordinates
     */
    private float[] isoProject(float x, float y, float z, float centerX, float centerY) {
        // Isometric projection:
        // screenX = (x - z) * cos(30)
        // screenY = (x + z) * sin(30) - y
        float screenX = (x - z) * COS_30 + centerX;
        float screenY = (x + z) * SIN_30 - y + centerY;
        return new float[]{screenX, screenY};
    }

    /**
     * Applies a shading multiplier to a color.
     *
     * @param color     the base ARGB color
     * @param shadeMult the shade multiplier (0-1, where 1 is full brightness)
     * @return the shaded color
     */
    private int shadeColor(int color, float shadeMult) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        r = Math.min(255, Math.max(0, (int) (r * shadeMult)));
        g = Math.min(255, Math.max(0, (int) (g * shadeMult)));
        b = Math.min(255, Math.max(0, (int) (b * shadeMult)));

        return Color.rgb(r, g, b);
    }

    /**
     * Returns the thumbnail size in pixels.
     */
    public int getThumbnailSize() {
        return THUMBNAIL_SIZE;
    }
}
