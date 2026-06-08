package com.dbuild.net.engine;

import android.util.Log;

/**
 * Wrapper for a native 3D model.
 * Provides methods to add/remove blocks, set textures and UVs,
 * build meshes, and retrieve mesh data for rendering.
 */
public class NativeModel {

    private static final String TAG = "NativeModel";

    private long modelPtr;
    private final RustBridge rustBridge;
    private final NativeEngine engine;
    private boolean destroyed = false;

    /**
     * Creates a new NativeModel wrapper.
     * @param modelPtr Native model pointer
     * @param rustBridge Bridge to native calls
     * @param engine Parent engine that owns this model
     */
    NativeModel(long modelPtr, RustBridge rustBridge, NativeEngine engine) {
        this.modelPtr = modelPtr;
        this.rustBridge = rustBridge;
        this.engine = engine;
    }

    /**
     * Adds a block to the model at the specified position.
     * @param x Block X position
     * @param y Block Y position
     * @param z Block Z position
     * @param colors Block color data (at least 4 floats: RGBA). If null, uses white.
     */
    public void addBlock(float x, float y, float z, float[] colors) {
        checkValid();
        try {
            float[] colorData = colors;
            if (colorData == null) {
                colorData = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
            }
            rustBridge.addBlock(modelPtr, x, y, z, colorData);
        } catch (Exception e) {
            Log.e(TAG, "Error adding block at (" + x + ", " + y + ", " + z + ")", e);
        }
    }

    /**
     * Removes a block from the model at the specified position.
     * @param x Block X position
     * @param y Block Y position
     * @param z Block Z position
     */
    public void removeBlock(float x, float y, float z) {
        checkValid();
        try {
            rustBridge.removeBlock(modelPtr, x, y, z);
        } catch (Exception e) {
            Log.e(TAG, "Error removing block at (" + x + ", " + y + ", " + z + ")", e);
        }
    }

    /**
     * Sets the texture for a specific face of a block.
     * @param x Block X position
     * @param y Block Y position
     * @param z Block Z position
     * @param face Face index (0=Top, 1=Bottom, 2=North, 3=South, 4=East, 5=West)
     * @param texturePath Path to the texture file
     */
    public void setBlockTexture(float x, float y, float z, int face, String texturePath) {
        checkValid();
        if (face < 0 || face > 5) {
            Log.w(TAG, "Invalid face index: " + face + ", must be 0-5");
            return;
        }
        try {
            rustBridge.setBlockTexture(modelPtr, x, y, z, face, texturePath);
        } catch (Exception e) {
            Log.e(TAG, "Error setting texture for face " + face, e);
        }
    }

    /**
     * Sets the UV mapping for a specific face of a block.
     * @param x Block X position
     * @param y Block Y position
     * @param z Block Z position
     * @param face Face index (0=Top, 1=Bottom, 2=North, 3=South, 4=East, 5=West)
     * @param u UV X offset
     * @param v UV Y offset
     * @param uScale UV Width scale
     * @param vScale UV Height scale
     */
    public void setBlockUV(float x, float y, float z, int face, float u, float v, float uScale, float vScale) {
        checkValid();
        if (face < 0 || face > 5) {
            Log.w(TAG, "Invalid face index: " + face + ", must be 0-5");
            return;
        }
        try {
            rustBridge.setBlockUV(modelPtr, x, y, z, face, u, v, uScale, vScale);
        } catch (Exception e) {
            Log.e(TAG, "Error setting UV for face " + face, e);
        }
    }

    /**
     * Triggers a mesh rebuild. Must be called after adding/removing blocks
     * or changing textures/UVs before retrieving mesh data.
     */
    public void buildMesh() {
        checkValid();
        try {
            rustBridge.buildMesh(modelPtr);
        } catch (Exception e) {
            Log.e(TAG, "Error building mesh", e);
        }
    }

    /**
     * Returns the vertex array of the built mesh.
     * Each vertex is 3 floats (x, y, z).
     * Must call buildMesh() first after any model changes.
     * @return Vertex data array, or empty array if mesh not built
     */
    public float[] getVertices() {
        checkValid();
        try {
            return rustBridge.getVertices(modelPtr);
        } catch (Exception e) {
            Log.e(TAG, "Error getting vertices", e);
            return new float[0];
        }
    }

    /**
     * Returns the normal array of the built mesh.
     * Each normal is 3 floats (nx, ny, nz).
     * @return Normal data array, or empty array if mesh not built
     */
    public float[] getNormals() {
        checkValid();
        try {
            return rustBridge.getNormals(modelPtr);
        } catch (Exception e) {
            Log.e(TAG, "Error getting normals", e);
            return new float[0];
        }
    }

    /**
     * Returns the UV coordinate array of the built mesh.
     * Each UV is 2 floats (u, v).
     * @return UV data array, or empty array if mesh not built
     */
    public float[] getUVs() {
        checkValid();
        try {
            return rustBridge.getUVs(modelPtr);
        } catch (Exception e) {
            Log.e(TAG, "Error getting UVs", e);
            return new float[0];
        }
    }

    /**
     * Returns the index array of the built mesh.
     * Each triangle is 3 indices.
     * @return Index data array, or empty array if mesh not built
     */
    public int[] getIndices() {
        checkValid();
        try {
            return rustBridge.getIndices(modelPtr);
        } catch (Exception e) {
            Log.e(TAG, "Error getting indices", e);
            return new int[0];
        }
    }

    /**
     * Returns the number of blocks in this model.
     * @return Block count
     */
    public int getBlockCount() {
        checkValid();
        try {
            return rustBridge.getBlockCount(modelPtr);
        } catch (Exception e) {
            Log.e(TAG, "Error getting block count", e);
            return 0;
        }
    }

    /**
     * Returns whether this model is valid (not destroyed).
     * @return true if the model can be used
     */
    public boolean isValid() {
        return !destroyed && modelPtr != 0;
    }

    /**
     * Returns the native model pointer.
     */
    public long getModelPtr() {
        return modelPtr;
    }

    /**
     * Destroys this model and frees native resources.
     * After calling this, the model cannot be used.
     */
    public void destroy() {
        if (destroyed) return;

        try {
            if (modelPtr != 0) {
                rustBridge.destroyModel(modelPtr);
                Log.d(TAG, "Destroyed native model (ptr=" + modelPtr + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error destroying native model", e);
        }

        modelPtr = 0;
        destroyed = true;

        if (engine != null) {
            engine.removeModel(this);
        }
    }

    /**
     * Checks that the model is valid before operations.
     * @throws IllegalStateException if the model has been destroyed
     */
    private void checkValid() {
        if (destroyed || modelPtr == 0) {
            throw new IllegalStateException("NativeModel has been destroyed or is invalid");
        }
    }

    /**
     * Returns a summary of this model's data for debugging.
     */
    public String getModelInfo() {
        if (!isValid()) return "NativeModel{INVALID}";
        int blockCount = 0;
        try {
            blockCount = rustBridge.getBlockCount(modelPtr);
        } catch (Exception ignored) {
        }
        return "NativeModel{ptr=" + modelPtr + ", blocks=" + blockCount + "}";
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!destroyed) {
                Log.w(TAG, "NativeModel finalized without explicit destroy() call (ptr=" + modelPtr + ")");
                destroy();
            }
        } finally {
            super.finalize();
        }
    }
}
