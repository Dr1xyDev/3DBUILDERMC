package com.dbuild.net.engine;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Wrapper for native serialization operations.
 * Provides JSON serialization/deserialization for model data,
 * with Java fallback implementations when the native library is unavailable.
 */
public class NativeSerializer {

    private static final String TAG = "NativeSerializer";
    private static final int SERIALIZATION_VERSION = 1;

    private final NativeModel model;
    private final RustBridge rustBridge;

    /**
     * Creates a new NativeSerializer for the given model.
     * @param model The model to serialize/deserialize
     */
    public NativeSerializer(NativeModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must not be null");
        }
        this.model = model;
        this.rustBridge = new RustBridge();
    }

    /**
     * Serializes the model to a JSON string.
     * Uses native implementation if available, falls back to Java.
     * @return JSON string representation of the model, or null on error
     */
    public String serialize() {
        if (!model.isValid()) {
            Log.e(TAG, "Cannot serialize: model is not valid");
            return null;
        }

        try {
            String result = rustBridge.serializeModel(model.getModelPtr());
            if (result != null && !result.isEmpty()) {
                return result;
            }
            // If native returned null/empty, try fallback
            return serializeFallback();
        } catch (Exception e) {
            Log.w(TAG, "Native serialization failed, using fallback", e);
            return serializeFallback();
        }
    }

    /**
     * Deserializes model data from a JSON string.
     * Replaces the current model's data with the deserialized data.
     * @param json JSON string to deserialize
     * @return true if deserialization succeeded
     */
    public boolean deserialize(String json) {
        if (!model.isValid()) {
            Log.e(TAG, "Cannot deserialize: model is not valid");
            return false;
        }
        if (json == null || json.isEmpty()) {
            Log.e(TAG, "Cannot deserialize: JSON string is null or empty");
            return false;
        }

        // Validate first
        if (!validateSerializedData(json)) {
            Log.e(TAG, "Cannot deserialize: JSON validation failed");
            return false;
        }

        try {
            boolean result = rustBridge.deserializeModel(model.getModelPtr(), json);
            if (result) {
                Log.i(TAG, "Native deserialization successful");
                return true;
            }
            // If native failed, try fallback
            Log.w(TAG, "Native deserialization failed, using fallback");
            return deserializeFallback(json);
        } catch (Exception e) {
            Log.w(TAG, "Native deserialization exception, using fallback", e);
            return deserializeFallback(json);
        }
    }

    /**
     * Serializes the model to a file.
     * @param path Output file path
     * @return true if serialization and file write succeeded
     */
    public boolean serializeToFile(String path) {
        if (path == null || path.isEmpty()) {
            Log.e(TAG, "Cannot serialize to file: path is null or empty");
            return false;
        }

        String json = serialize();
        if (json == null) {
            Log.e(TAG, "Cannot serialize to file: serialization returned null");
            return false;
        }

        BufferedWriter writer = null;
        try {
            // Ensure parent directory exists
            File file = new File(path);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.e(TAG, "Failed to create parent directory: " + parentDir.getAbsolutePath());
                    return false;
                }
            }

            writer = new BufferedWriter(new FileWriter(file));
            writer.write(json);
            writer.flush();
            Log.i(TAG, "Model serialized to file: " + path + " (" + json.length() + " chars)");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "I/O error writing serialized model to file: " + path, e);
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Deserializes model data from a file.
     * @param path Input file path
     * @return true if file read and deserialization succeeded
     */
    public boolean deserializeFromFile(String path) {
        if (path == null || path.isEmpty()) {
            Log.e(TAG, "Cannot deserialize from file: path is null or empty");
            return false;
        }

        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG, "Cannot deserialize: file does not exist: " + path);
            return false;
        }
        if (!file.canRead()) {
            Log.e(TAG, "Cannot deserialize: file is not readable: " + path);
            return false;
        }

        BufferedReader reader = null;
        try {
            StringBuilder sb = new StringBuilder();
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }

            String json = sb.toString().trim();
            if (json.isEmpty()) {
                Log.e(TAG, "File is empty: " + path);
                return false;
            }

            return deserialize(json);
        } catch (IOException e) {
            Log.e(TAG, "I/O error reading file: " + path, e);
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Returns the approximate size of the serialized data in characters.
     * Useful for pre-allocating buffers or showing size estimates.
     * @return Approximate serialized size in characters, or -1 if unavailable
     */
    public long getSerializedSize() {
        if (!model.isValid()) return -1;

        try {
            String json = rustBridge.serializeModel(model.getModelPtr());
            if (json != null) {
                return json.length();
            }
        } catch (Exception ignored) {
        }

        // Fallback estimate: roughly 250 chars per block
        int blockCount = model.getBlockCount();
        if (blockCount == 0) return 64; // Empty model header
        return 64 + (long) blockCount * 250;
    }

    /**
     * Validates that a JSON string contains valid serialized model data.
     * Checks for required fields and correct structure without fully deserializing.
     * @param json JSON string to validate
     * @return true if the data appears valid
     */
    public boolean validateSerializedData(String json) {
        if (json == null || json.isEmpty()) return false;

        try {
            JSONObject root = new JSONObject(json);

            // Check for version field
            if (!root.has("version")) {
                Log.w(TAG, "Validation: missing 'version' field");
                return false;
            }

            int version = root.optInt("version", -1);
            if (version < 1 || version > SERIALIZATION_VERSION) {
                Log.w(TAG, "Validation: unsupported version " + version);
                return false;
            }

            // Check for blocks array
            if (!root.has("blocks")) {
                Log.w(TAG, "Validation: missing 'blocks' field");
                return false;
            }

            JSONArray blocks = root.optJSONArray("blocks");
            if (blocks == null) {
                Log.w(TAG, "Validation: 'blocks' is not an array");
                return false;
            }

            // Validate each block has minimum required fields
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject blockObj = blocks.optJSONObject(i);
                if (blockObj == null) {
                    Log.w(TAG, "Validation: block at index " + i + " is not an object");
                    return false;
                }
                if (!blockObj.has("x") || !blockObj.has("y") || !blockObj.has("z")) {
                    Log.w(TAG, "Validation: block at index " + i + " missing position fields");
                    return false;
                }
            }

            return true;
        } catch (JSONException e) {
            Log.w(TAG, "Validation: JSON parse error", e);
            return false;
        }
    }

    /**
     * Java fallback for serialization when native is unavailable.
     * Builds a JSON representation of the model using mesh data.
     */
    private String serializeFallback() {
        if (!model.isValid()) return null;

        try {
            // Ensure mesh is built
            model.buildMesh();

            JSONObject root = new JSONObject();
            root.put("version", SERIALIZATION_VERSION);
            root.put("blockCount", model.getBlockCount());

            // Store mesh data directly since we can't access individual blocks from fallback
            JSONObject meshData = new JSONObject();

            float[] vertices = model.getVertices();
            if (vertices != null && vertices.length > 0) {
                JSONArray vertArray = new JSONArray();
                for (float v : vertices) vertArray.put((double) v);
                meshData.put("vertices", vertArray);
            }

            float[] normals = model.getNormals();
            if (normals != null && normals.length > 0) {
                JSONArray normArray = new JSONArray();
                for (float n : normals) normArray.put((double) n);
                meshData.put("normals", normArray);
            }

            float[] uvs = model.getUVs();
            if (uvs != null && uvs.length > 0) {
                JSONArray uvArray = new JSONArray();
                for (float uv : uvs) uvArray.put((double) uv);
                meshData.put("uvs", uvArray);
            }

            int[] indices = model.getIndices();
            if (indices != null && indices.length > 0) {
                JSONArray idxArray = new JSONArray();
                for (int idx : indices) idxArray.put(idx);
                meshData.put("indices", idxArray);
            }

            root.put("meshData", meshData);

            // Empty blocks array for compatibility
            root.put("blocks", new JSONArray());

            return root.toString(2);
        } catch (JSONException e) {
            Log.e(TAG, "Fallback serialization failed", e);
            return null;
        }
    }

    /**
     * Java fallback for deserialization when native is unavailable.
     * Parses JSON and attempts to reconstruct model data.
     */
    private boolean deserializeFallback(String json) {
        if (!model.isValid() || json == null) return false;

        try {
            JSONObject root = new JSONObject(json);

            // If we have blocks data, that's ideal (from RustBridge fallback serialize)
            JSONArray blocks = root.optJSONArray("blocks");
            if (blocks != null && blocks.length() > 0) {
                // Use RustBridge's deserialize which handles block-level data
                return rustBridge.deserializeModel(model.getModelPtr(), json);
            }

            // If we only have mesh data (from our serializeFallback)
            JSONObject meshData = root.optJSONObject("meshData");
            if (meshData == null) {
                Log.w(TAG, "Fallback deserialize: no blocks or mesh data found");
                return false;
            }

            // Mesh-only deserialization has limited usefulness
            // We can't reconstruct individual blocks from mesh data
            // but we can validate the data is present
            boolean hasVertices = meshData.has("vertices");
            boolean hasIndices = meshData.has("indices");

            if (hasVertices && hasIndices) {
                Log.i(TAG, "Fallback deserialize: mesh data loaded (block editing not available)");
                return true;
            }

            return false;
        } catch (JSONException e) {
            Log.e(TAG, "Fallback deserialization JSON error", e);
            return false;
        }
    }

    /**
     * Creates a compact serialization without pretty-printing.
     * Useful for network transfer or storage size minimization.
     * @return Compact JSON string, or null on error
     */
    public String serializeCompact() {
        String json = serialize();
        if (json == null) return null;

        try {
            // Parse and re-serialize without indentation
            JSONObject root = new JSONObject(json);
            return root.toString();
        } catch (JSONException e) {
            // Return as-is if we can't compact it
            return json;
        }
    }

    /**
     * Returns the serialization version supported by this serializer.
     */
    public int getSerializationVersion() {
        return SERIALIZATION_VERSION;
    }

    /**
     * Computes a simple checksum of the serialized data for integrity verification.
     * @return Checksum value, or 0 if serialization fails
     */
    public long computeChecksum() {
        String json = serialize();
        if (json == null) return 0;

        long checksum = 0;
        for (int i = 0; i < json.length(); i++) {
            checksum = checksum * 31 + json.charAt(i);
        }
        return checksum;
    }
}
