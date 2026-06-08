package com.dbuild.net.engine;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JNI bridge to the Rust native engine.
 * Provides native method declarations and Java fallback implementations
 * when the native library is not available.
 */
public class RustBridge {

    private static final String TAG = "RustBridge";
    private static final String LIBRARY_NAME = "rust_engine";

    private static boolean nativeLoaded = false;

    // Java fallback model storage: modelPtr -> ModelData
    private static long nextFallbackPtr = 1;
    private static final Map<Long, FallbackModelData> fallbackModels = new HashMap<>();
    private static long fallbackEnginePtr = 0;

    // ==================== Native method declarations ====================

    public native long nativeCreateEngine();

    public native void nativeDestroyEngine(long enginePtr);

    public native long nativeCreateModel(long enginePtr);

    public native void nativeDestroyModel(long modelPtr);

    public native void nativeAddBlock(long modelPtr, float x, float y, float z, float[] colors);

    public native void nativeRemoveBlock(long modelPtr, float x, float y, float z);

    public native void nativeSetBlockTexture(long modelPtr, float x, float y, float z, int face, String texturePath);

    public native void nativeSetBlockUV(long modelPtr, float x, float y, float z, int face, float u, float v, float uScale, float vScale);

    public native void nativeBuildMesh(long modelPtr);

    public native float[] nativeGetVertices(long modelPtr);

    public native float[] nativeGetNormals(long modelPtr);

    public native float[] nativeGetUVs(long modelPtr);

    public native int[] nativeGetIndices(long modelPtr);

    public native boolean nativeExportGLB(long modelPtr, String path);

    public native boolean nativeExportGLTF(long modelPtr, String path);

    public native boolean nativeExportOBJ(long modelPtr, String path);

    public native boolean nativeExportM3X(long modelPtr, String path);

    public native boolean nativeImportM3X(long modelPtr, String path);

    public native String nativeSerializeModel(long modelPtr);

    public native boolean nativeDeserializeModel(long modelPtr, String json);

    public native int nativeGetBlockCount(long modelPtr);

    // ==================== Static initializer ====================

    static {
        load();
    }

    /**
     * Attempts to load the native Rust library.
     * On failure, sets nativeLoaded=false and provides Java fallback implementations.
     */
    public static void load() {
        if (nativeLoaded) return;

        try {
            System.loadLibrary(LIBRARY_NAME);
            nativeLoaded = true;
            Log.i(TAG, "Native Rust engine library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            nativeLoaded = false;
            Log.w(TAG, "Native Rust engine library not available, using Java fallback: " + e.getMessage());
        }
    }

    /**
     * Returns whether the native Rust engine is available.
     */
    public static boolean isNativeAvailable() {
        return nativeLoaded;
    }

    // ==================== High-level bridge methods with fallback ====================

    /**
     * Creates a new engine instance.
     * @return Engine pointer (native or fallback)
     */
    public long createEngine() {
        if (nativeLoaded) {
            return nativeCreateEngine();
        }
        return createFallbackEngine();
    }

    /**
     * Destroys an engine instance.
     */
    public void destroyEngine(long enginePtr) {
        if (nativeLoaded) {
            nativeDestroyEngine(enginePtr);
        } else {
            destroyFallbackEngine(enginePtr);
        }
    }

    /**
     * Creates a new model.
     */
    public long createModel(long enginePtr) {
        if (nativeLoaded) {
            return nativeCreateModel(enginePtr);
        }
        return createFallbackModel();
    }

    /**
     * Destroys a model.
     */
    public void destroyModel(long modelPtr) {
        if (nativeLoaded) {
            nativeDestroyModel(modelPtr);
        } else {
            destroyFallbackModel(modelPtr);
        }
    }

    /**
     * Adds a block to a model.
     */
    public void addBlock(long modelPtr, float x, float y, float z, float[] colors) {
        if (nativeLoaded) {
            nativeAddBlock(modelPtr, x, y, z, colors);
        } else {
            addBlockFallback(modelPtr, x, y, z, colors);
        }
    }

    /**
     * Removes a block from a model.
     */
    public void removeBlock(long modelPtr, float x, float y, float z) {
        if (nativeLoaded) {
            nativeRemoveBlock(modelPtr, x, y, z);
        } else {
            removeBlockFallback(modelPtr, x, y, z);
        }
    }

    /**
     * Sets the texture for a block face.
     */
    public void setBlockTexture(long modelPtr, float x, float y, float z, int face, String texturePath) {
        if (nativeLoaded) {
            nativeSetBlockTexture(modelPtr, x, y, z, face, texturePath);
        } else {
            setBlockTextureFallback(modelPtr, x, y, z, face, texturePath);
        }
    }

    /**
     * Sets the UV mapping for a block face.
     */
    public void setBlockUV(long modelPtr, float x, float y, float z, int face, float u, float v, float uScale, float vScale) {
        if (nativeLoaded) {
            nativeSetBlockUV(modelPtr, x, y, z, face, u, v, uScale, vScale);
        } else {
            setBlockUVFallback(modelPtr, x, y, z, face, u, v, uScale, vScale);
        }
    }

    /**
     * Builds the mesh for a model (required before getting vertex data).
     */
    public void buildMesh(long modelPtr) {
        if (nativeLoaded) {
            nativeBuildMesh(modelPtr);
        } else {
            buildMeshFallback(modelPtr);
        }
    }

    /**
     * Gets the vertex array for a built mesh.
     */
    public float[] getVertices(long modelPtr) {
        if (nativeLoaded) {
            return nativeGetVertices(modelPtr);
        }
        return getVerticesFallback(modelPtr);
    }

    /**
     * Gets the normal array for a built mesh.
     */
    public float[] getNormals(long modelPtr) {
        if (nativeLoaded) {
            return nativeGetNormals(modelPtr);
        }
        return getNormalsFallback(modelPtr);
    }

    /**
     * Gets the UV array for a built mesh.
     */
    public float[] getUVs(long modelPtr) {
        if (nativeLoaded) {
            return nativeGetUVs(modelPtr);
        }
        return getUVsFallback(modelPtr);
    }

    /**
     * Gets the index array for a built mesh.
     */
    public int[] getIndices(long modelPtr) {
        if (nativeLoaded) {
            return nativeGetIndices(modelPtr);
        }
        return getIndicesFallback(modelPtr);
    }

    /**
     * Exports a model to GLB format.
     */
    public boolean exportGLB(long modelPtr, String path) {
        if (nativeLoaded) {
            return nativeExportGLB(modelPtr, path);
        }
        return exportGLBFallback(modelPtr, path);
    }

    /**
     * Exports a model to GLTF format.
     */
    public boolean exportGLTF(long modelPtr, String path) {
        if (nativeLoaded) {
            return nativeExportGLTF(modelPtr, path);
        }
        return exportGLTFFallback(modelPtr, path);
    }

    /**
     * Exports a model to OBJ format.
     */
    public boolean exportOBJ(long modelPtr, String path) {
        if (nativeLoaded) {
            return nativeExportOBJ(modelPtr, path);
        }
        return exportOBJFallback(modelPtr, path);
    }

    /**
     * Exports a model to M3X format.
     */
    public boolean exportM3X(long modelPtr, String path) {
        if (nativeLoaded) {
            return nativeExportM3X(modelPtr, path);
        }
        return exportM3XFallback(modelPtr, path);
    }

    /**
     * Imports a model from M3X format.
     */
    public boolean importM3X(long modelPtr, String path) {
        if (nativeLoaded) {
            return nativeImportM3X(modelPtr, path);
        }
        return importM3XFallback(modelPtr, path);
    }

    /**
     * Serializes a model to JSON string.
     */
    public String serializeModel(long modelPtr) {
        if (nativeLoaded) {
            return nativeSerializeModel(modelPtr);
        }
        return serializeModelFallback(modelPtr);
    }

    /**
     * Deserializes a model from JSON string.
     */
    public boolean deserializeModel(long modelPtr, String json) {
        if (nativeLoaded) {
            return nativeDeserializeModel(modelPtr, json);
        }
        return deserializeModelFallback(modelPtr, json);
    }

    /**
     * Gets the block count for a model.
     */
    public int getBlockCount(long modelPtr) {
        if (nativeLoaded) {
            return nativeGetBlockCount(modelPtr);
        }
        return getBlockCountFallback(modelPtr);
    }

    // ==================== Java Fallback Implementation ====================

    private static class FallbackBlockData {
        float x, y, z;
        float[] colors; // RGBA per face (6 faces * 4 = 24 floats)
        String[] textures; // Texture path per face (6)
        float[] uvData; // u, v, uScale, vScale per face (6 * 4 = 24 floats)

        FallbackBlockData(float x, float y, float z, float[] colors) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.colors = new float[24];
            if (colors != null && colors.length >= 4) {
                for (int face = 0; face < 6; face++) {
                    System.arraycopy(colors, 0, this.colors, face * 4, Math.min(4, colors.length));
                }
            } else {
                for (int i = 0; i < 24; i++) this.colors[i] = 1.0f;
            }
            this.textures = new String[6];
            this.uvData = new float[24];
            for (int face = 0; face < 6; face++) {
                this.uvData[face * 4 + 0] = 0.0f; // u
                this.uvData[face * 4 + 1] = 0.0f; // v
                this.uvData[face * 4 + 2] = 1.0f; // uScale
                this.uvData[face * 4 + 3] = 1.0f; // vScale
            }
        }
    }

    private static class FallbackModelData {
        List<FallbackBlockData> blocks = new ArrayList<>();
        float[] cachedVertices;
        float[] cachedNormals;
        float[] cachedUVs;
        int[] cachedIndices;
        boolean meshDirty = true;

        // Face definitions: each face has a normal and 4 vertex offsets
        // Faces: 0=Top(+Y), 1=Bottom(-Y), 2=North(-Z), 3=South(+Z), 4=East(+X), 5=West(-X)
        static final float[][] FACE_NORMALS = {
                {0, 1, 0},   // Top
                {0, -1, 0},  // Bottom
                {0, 0, -1},  // North
                {0, 0, 1},   // South
                {1, 0, 0},   // East
                {-1, 0, 0}   // West
        };

        // Vertex offsets for each face (4 corners per face)
        static final float[][][] FACE_VERTICES = {
                // Top face (+Y)
                {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
                // Bottom face (-Y)
                {{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}},
                // North face (-Z)
                {{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}},
                // South face (+Z)
                {{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}},
                // East face (+X)
                {{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}},
                // West face (-X)
                {{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}}
        };

        // UV corners for each face vertex
        static final float[][] FACE_UVS = {
                {0, 0}, {1, 0}, {1, 1}, {0, 1}
        };

        void buildMesh() {
            int blockCount = blocks.size();
            if (blockCount == 0) {
                cachedVertices = new float[0];
                cachedNormals = new float[0];
                cachedUVs = new float[0];
                cachedIndices = new int[0];
                meshDirty = false;
                return;
            }

            int vertsPerBlock = 6 * 4; // 6 faces, 4 vertices each
            int indicesPerBlock = 6 * 6; // 6 faces, 2 triangles each

            cachedVertices = new float[blockCount * vertsPerBlock * 3];
            cachedNormals = new float[blockCount * vertsPerBlock * 3];
            cachedUVs = new float[blockCount * vertsPerBlock * 2];
            cachedIndices = new int[blockCount * indicesPerBlock];

            int vertOffset = 0;
            int idxOffset = 0;
            int vertexIndex = 0;

            for (FallbackBlockData block : blocks) {
                for (int face = 0; face < 6; face++) {
                    float uOff = block.uvData[face * 4 + 0];
                    float vOff = block.uvData[face * 4 + 1];
                    float uScl = block.uvData[face * 4 + 2];
                    float vScl = block.uvData[face * 4 + 3];

                    for (int v = 0; v < 4; v++) {
                        // Position
                        cachedVertices[vertOffset] = block.x + FACE_VERTICES[face][v][0];
                        cachedVertices[vertOffset + 1] = block.y + FACE_VERTICES[face][v][1];
                        cachedVertices[vertOffset + 2] = block.z + FACE_VERTICES[face][v][2];

                        // Normal
                        cachedNormals[vertOffset] = FACE_NORMALS[face][0];
                        cachedNormals[vertOffset + 1] = FACE_NORMALS[face][1];
                        cachedNormals[vertOffset + 2] = FACE_NORMALS[face][2];

                        vertOffset += 3;

                        // UV
                        int uvIdx = (vertexIndex * 2);
                        cachedUVs[uvIdx] = uOff + FACE_UVS[v][0] * uScl;
                        cachedUVs[uvIdx + 1] = vOff + FACE_UVS[v][1] * vScl;

                        vertexIndex++;
                    }

                    // Two triangles per face
                    int baseIdx = vertexIndex - 4;
                    cachedIndices[idxOffset++] = baseIdx;
                    cachedIndices[idxOffset++] = baseIdx + 1;
                    cachedIndices[idxOffset++] = baseIdx + 2;
                    cachedIndices[idxOffset++] = baseIdx;
                    cachedIndices[idxOffset++] = baseIdx + 2;
                    cachedIndices[idxOffset++] = baseIdx + 3;
                }
            }

            meshDirty = false;
        }
    }

    private synchronized long createFallbackEngine() {
        fallbackEnginePtr = nextFallbackPtr++;
        return fallbackEnginePtr;
    }

    private synchronized void destroyFallbackEngine(long enginePtr) {
        // Clean up all models associated with this engine
        // (In a real implementation we'd track which models belong to which engine)
    }

    private synchronized long createFallbackModel() {
        long ptr = nextFallbackPtr++;
        fallbackModels.put(ptr, new FallbackModelData());
        return ptr;
    }

    private synchronized void destroyFallbackModel(long modelPtr) {
        fallbackModels.remove(modelPtr);
    }

    private synchronized void addBlockFallback(long modelPtr, float x, float y, float z, float[] colors) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return;
        model.blocks.add(new FallbackBlockData(x, y, z, colors));
        model.meshDirty = true;
    }

    private synchronized void removeBlockFallback(long modelPtr, float x, float y, float z) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return;
        for (int i = model.blocks.size() - 1; i >= 0; i--) {
            FallbackBlockData block = model.blocks.get(i);
            if (Float.compare(block.x, x) == 0 && Float.compare(block.y, y) == 0 && Float.compare(block.z, z) == 0) {
                model.blocks.remove(i);
                model.meshDirty = true;
                break;
            }
        }
    }

    private synchronized void setBlockTextureFallback(long modelPtr, float x, float y, float z, int face, String texturePath) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return;
        for (FallbackBlockData block : model.blocks) {
            if (Float.compare(block.x, x) == 0 && Float.compare(block.y, y) == 0 && Float.compare(block.z, z) == 0) {
                if (face >= 0 && face < 6) {
                    block.textures[face] = texturePath;
                    model.meshDirty = true;
                }
                break;
            }
        }
    }

    private synchronized void setBlockUVFallback(long modelPtr, float x, float y, float z, int face, float u, float v, float uScale, float vScale) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return;
        for (FallbackBlockData block : model.blocks) {
            if (Float.compare(block.x, x) == 0 && Float.compare(block.y, y) == 0 && Float.compare(block.z, z) == 0) {
                if (face >= 0 && face < 6) {
                    block.uvData[face * 4 + 0] = u;
                    block.uvData[face * 4 + 1] = v;
                    block.uvData[face * 4 + 2] = uScale;
                    block.uvData[face * 4 + 3] = vScale;
                    model.meshDirty = true;
                }
                break;
            }
        }
    }

    private synchronized void buildMeshFallback(long modelPtr) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return;
        model.buildMesh();
    }

    private synchronized float[] getVerticesFallback(long modelPtr) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null || model.meshDirty) return new float[0];
        return model.cachedVertices != null ? model.cachedVertices.clone() : new float[0];
    }

    private synchronized float[] getNormalsFallback(long modelPtr) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null || model.meshDirty) return new float[0];
        return model.cachedNormals != null ? model.cachedNormals.clone() : new float[0];
    }

    private synchronized float[] getUVsFallback(long modelPtr) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null || model.meshDirty) return new float[0];
        return model.cachedUVs != null ? model.cachedUVs.clone() : new float[0];
    }

    private synchronized int[] getIndicesFallback(long modelPtr) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null || model.meshDirty) return new int[0];
        return model.cachedIndices != null ? model.cachedIndices.clone() : new int[0];
    }

    private synchronized boolean exportGLBFallback(long modelPtr, String path) {
        // Java fallback for GLB export - basic implementation
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return false;
        Log.w(TAG, "GLB export fallback - limited functionality");
        return false;
    }

    private synchronized boolean exportGLTFFallback(long modelPtr, String path) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return false;
        Log.w(TAG, "GLTF export fallback - limited functionality");
        return false;
    }

    private synchronized boolean exportOBJFallback(long modelPtr, String path) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return false;
        if (model.meshDirty) model.buildMesh();
        if (model.cachedVertices == null || model.cachedVertices.length == 0) return false;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# 3D Builder MC OBJ Export\n");
            sb.append("# Generated by Java fallback exporter\n\n");

            // Write vertices
            for (int i = 0; i < model.cachedVertices.length; i += 3) {
                sb.append(String.format("v %.6f %.6f %.6f\n",
                        model.cachedVertices[i],
                        model.cachedVertices[i + 1],
                        model.cachedVertices[i + 2]));
            }
            sb.append("\n");

            // Write normals
            for (int i = 0; i < model.cachedNormals.length; i += 3) {
                sb.append(String.format("vn %.6f %.6f %.6f\n",
                        model.cachedNormals[i],
                        model.cachedNormals[i + 1],
                        model.cachedNormals[i + 2]));
            }
            sb.append("\n");

            // Write UVs
            for (int i = 0; i < model.cachedUVs.length; i += 2) {
                sb.append(String.format("vt %.6f %.6f\n",
                        model.cachedUVs[i],
                        model.cachedUVs[i + 1]));
            }
            sb.append("\n");

            // Write faces (using indices)
            for (int i = 0; i < model.cachedIndices.length; i += 3) {
                int i0 = model.cachedIndices[i] + 1;
                int i1 = model.cachedIndices[i + 1] + 1;
                int i2 = model.cachedIndices[i + 2] + 1;
                sb.append(String.format("f %d/%d/%d %d/%d/%d %d/%d/%d\n",
                        i0, i0, i0, i1, i1, i1, i2, i2, i2));
            }

            java.io.FileWriter writer = new java.io.FileWriter(path);
            writer.write(sb.toString());
            writer.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "OBJ export fallback failed", e);
            return false;
        }
    }

    private synchronized boolean exportM3XFallback(long modelPtr, String path) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return false;
        String json = serializeModelFallback(modelPtr);
        if (json == null) return false;
        try {
            java.io.FileWriter writer = new java.io.FileWriter(path);
            writer.write(json);
            writer.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "M3X export fallback failed", e);
            return false;
        }
    }

    private synchronized boolean importM3XFallback(long modelPtr, String path) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(path));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return deserializeModelFallback(modelPtr, sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "M3X import fallback failed", e);
            return false;
        }
    }

    private synchronized String serializeModelFallback(long modelPtr) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return null;

        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("blockCount", model.blocks.size());

            JSONArray blocksArray = new JSONArray();
            for (FallbackBlockData block : model.blocks) {
                JSONObject blockObj = new JSONObject();
                blockObj.put("x", (double) block.x);
                blockObj.put("y", (double) block.y);
                blockObj.put("z", (double) block.z);

                JSONArray colorsArray = new JSONArray();
                for (float c : block.colors) colorsArray.put((double) c);
                blockObj.put("colors", colorsArray);

                JSONArray texturesArray = new JSONArray();
                for (String tex : block.textures) {
                    texturesArray.put(tex != null ? tex : JSONObject.NULL);
                }
                blockObj.put("textures", texturesArray);

                JSONArray uvArray = new JSONArray();
                for (float uv : block.uvData) uvArray.put((double) uv);
                blockObj.put("uvData", uvArray);

                blocksArray.put(blockObj);
            }
            root.put("blocks", blocksArray);

            return root.toString(2);
        } catch (JSONException e) {
            Log.e(TAG, "Serialize fallback failed", e);
            return null;
        }
    }

    private synchronized boolean deserializeModelFallback(long modelPtr, String json) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null || json == null) return false;

        try {
            JSONObject root = new JSONObject(json);
            JSONArray blocksArray = root.optJSONArray("blocks");
            if (blocksArray == null) return false;

            model.blocks.clear();

            for (int i = 0; i < blocksArray.length(); i++) {
                JSONObject blockObj = blocksArray.getJSONObject(i);
                float x = (float) blockObj.optDouble("x", 0);
                float y = (float) blockObj.optDouble("y", 0);
                float z = (float) blockObj.optDouble("z", 0);

                float[] colors = new float[24];
                JSONArray colorsArray = blockObj.optJSONArray("colors");
                if (colorsArray != null) {
                    for (int j = 0; j < Math.min(24, colorsArray.length()); j++) {
                        colors[j] = (float) colorsArray.optDouble(j, 1.0);
                    }
                } else {
                    for (int j = 0; j < 24; j++) colors[j] = 1.0f;
                }

                FallbackBlockData block = new FallbackBlockData(x, y, z, new float[]{1, 1, 1, 1});
                block.colors = colors;

                JSONArray texturesArray = blockObj.optJSONArray("textures");
                if (texturesArray != null) {
                    for (int j = 0; j < Math.min(6, texturesArray.length()); j++) {
                        block.textures[j] = texturesArray.isNull(j) ? null : texturesArray.optString(j, null);
                    }
                }

                JSONArray uvArray = blockObj.optJSONArray("uvData");
                if (uvArray != null) {
                    for (int j = 0; j < Math.min(24, uvArray.length()); j++) {
                        block.uvData[j] = (float) uvArray.optDouble(j, j % 4 == 2 ? 1.0 : j % 4 == 3 ? 1.0 : 0.0);
                    }
                }

                model.blocks.add(block);
            }

            model.meshDirty = true;
            return true;
        } catch (JSONException e) {
            Log.e(TAG, "Deserialize fallback failed", e);
            return false;
        }
    }

    private synchronized int getBlockCountFallback(long modelPtr) {
        FallbackModelData model = fallbackModels.get(modelPtr);
        if (model == null) return 0;
        return model.blocks.size();
    }
}
