package com.dbuild.net.project;

import android.content.Context;
import android.util.Log;

import com.dbuild.net.model.Block3D;
import com.dbuild.net.model.CameraSettings;
import com.dbuild.net.model.Scene;

import org.json.JSONArray;
import org.json.JSONException;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Handles JSON serialization and deserialization of project data.
 * Responsible for converting Scene, Block3D, CameraSettings, and settings
 * to/from JSON, and for reading/writing project files to disk.
 * Uses safe write pattern (write to temp file, then rename) to prevent corruption.
 */
public class ProjectSerializer {

    private static final String TAG = "ProjectSerializer";

    private static final int BUFFER_SIZE = 8192;
    private static final String TEMP_PREFIX = "tmp_";

    private final ProjectStorage storage;

    /**
     * Constructs a ProjectSerializer with the given context.
     *
     * @param context the application context
     */
    public ProjectSerializer(Context context) {
        this.storage = new ProjectStorage(context);
    }

    /**
     * Constructs a ProjectSerializer with an existing ProjectStorage instance.
     *
     * @param storage the ProjectStorage to use for file I/O
     */
    public ProjectSerializer(ProjectStorage storage) {
        this.storage = storage;
    }

    // =========================================================================
    // Scene Serialization
    // =========================================================================

    /**
     * Serializes a Scene object to a JSONObject.
     *
     * @param scene the Scene to serialize
     * @return the JSONObject representation
     */
    public JSONObject serializeScene(Scene scene) {
        if (scene == null) {
            return new JSONObject();
        }
        return scene.toJSON();
    }

    /**
     * Deserializes a Scene from a JSONObject.
     *
     * @param json the JSON object representing the scene
     * @return the deserialized Scene
     * @throws JSONException if the JSON is malformed
     */
    public Scene deserializeScene(JSONObject json) throws JSONException {
        if (json == null || json.length() == 0) {
            return new Scene();
        }
        return Scene.fromJSON(json);
    }

    // =========================================================================
    // Block3D List Serialization
    // =========================================================================

    /**
     * Serializes a list of Block3D objects to a JSONArray.
     *
     * @param blocks the list of blocks to serialize
     * @return the JSONArray representation
     */
    public JSONArray serializeBlocks(List<Block3D> blocks) {
        JSONArray array = new JSONArray();
        if (blocks == null) {
            return array;
        }
        for (Block3D block : blocks) {
            if (block != null) {
                array.put(block.toJSON());
            }
        }
        return array;
    }

    /**
     * Deserializes a list of Block3D objects from a JSONArray.
     *
     * @param json the JSONArray representing the blocks
     * @return the deserialized list of blocks
     * @throws JSONException if the JSON is malformed
     */
    public List<Block3D> deserializeBlocks(JSONArray json) throws JSONException {
        List<Block3D> blocks = new ArrayList<>();
        if (json == null) {
            return blocks;
        }
        for (int i = 0; i < json.length(); i++) {
            JSONObject blockJson = json.getJSONObject(i);
            Block3D block = Block3D.fromJSON(blockJson);
            blocks.add(block);
        }
        return blocks;
    }

    // =========================================================================
    // CameraSettings Serialization
    // =========================================================================

    /**
     * Serializes CameraSettings to a JSONObject.
     *
     * @param camera the camera settings to serialize
     * @return the JSONObject representation
     */
    public JSONObject serializeCamera(CameraSettings camera) {
        if (camera == null) {
            return new CameraSettings().toJSON();
        }
        return camera.toJSON();
    }

    /**
     * Deserializes CameraSettings from a JSONObject.
     *
     * @param json the JSON object representing camera settings
     * @return the deserialized CameraSettings
     * @throws JSONException if the JSON is malformed
     */
    public CameraSettings deserializeCamera(JSONObject json) throws JSONException {
        if (json == null || json.length() == 0) {
            return new CameraSettings();
        }
        return CameraSettings.fromJSON(json);
    }

    // =========================================================================
    // Settings Map Serialization
    // =========================================================================

    /**
     * Serializes a settings map to a JSONObject.
     * Supports String, Number, Boolean, and JSONObject/JSONArray values.
     *
     * @param settings the settings map to serialize
     * @return the JSONObject representation
     */
    public JSONObject serializeSettings(Map<String, Object> settings) {
        JSONObject json = new JSONObject();
        if (settings == null) {
            return json;
        }
        try {
            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    json.put(entry.getKey(), wrapValue(entry.getValue()));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize settings map", e);
        }
        return json;
    }

    /**
     * Wraps an Object value into a JSON-compatible type.
     */
    private Object wrapValue(Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value;
        }
        return String.valueOf(value);
    }

    /**
     * Deserializes a settings map from a JSONObject.
     * Values are returned as their natural types (String, Integer, Long, Double, Boolean).
     *
     * @param json the JSON object representing settings
     * @return the deserialized map
     */
    public Map<String, Object> deserializeSettings(JSONObject json) {
        Map<String, Object> settings = new HashMap<>();
        if (json == null) {
            return settings;
        }
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object value = json.get(key);
                settings.put(key, unwrapValue(value));
            } catch (JSONException e) {
                Log.w(TAG, "Failed to read setting: " + key, e);
            }
        }
        return settings;
    }

    /**
     * Unwraps a JSON value to its natural Java type.
     */
    private Object unwrapValue(Object value) {
        if (value instanceof Integer) {
            return value;
        }
        if (value instanceof Long) {
            return value;
        }
        if (value instanceof Double) {
            return value;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String) {
            return value;
        }
        // For nested objects/arrays, return their string representation
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    // =========================================================================
    // Project Save / Load
    // =========================================================================

    /**
     * Saves a complete project to disk. Writes all component files (scene, blocks,
     * camera, settings, metadata) using safe-write (temp file + rename) pattern.
     *
     * @param projectId the project ID
     * @param scene     the scene data
     * @param metadata  the project metadata
     * @return true if all files were saved successfully
     */
    public boolean saveProject(String projectId, Scene scene, ProjectMetadata metadata) {
        if (projectId == null || projectId.isEmpty()) {
            Log.e(TAG, "Cannot save project: projectId is null or empty");
            return false;
        }

        // Ensure project structure exists
        if (!storage.projectExists(projectId)) {
            if (!storage.createProjectStructure(projectId)) {
                Log.e(TAG, "Failed to create project structure for: " + projectId);
                return false;
            }
        }

        // Update metadata timestamps and block count
        metadata.touchModified();
        if (scene != null) {
            metadata.setBlockCount(scene.getBlockCount());
        }
        metadata.setFileSize(storage.getProjectSize(projectId));

        boolean allSuccess = true;

        // Save scene.json
        if (scene != null) {
            JSONObject sceneJson = serializeScene(scene);
            if (!writeJsonFileSafe(projectId, storage.getFileNameScene(), sceneJson)) {
                Log.e(TAG, "Failed to save scene for project: " + projectId);
                allSuccess = false;
            }
        }

        // Save blocks.json (redundant with scene, but kept for compatibility)
        if (scene != null) {
            JSONArray blocksJson = serializeBlocks(scene.getBlocksList());
            if (!writeJsonArrayFileSafe(projectId, storage.getFileNameBlocks(), blocksJson)) {
                Log.e(TAG, "Failed to save blocks for project: " + projectId);
                allSuccess = false;
            }
        }

        // Save camera.json (extract from scene properties or use default)
        CameraSettings camera = new CameraSettings();
        String cameraJsonStr = readTextFile(storage.getProjectFile(projectId, storage.getFileNameCamera()));
        if (cameraJsonStr != null) {
            try {
                JSONObject existingCamera = new JSONObject(cameraJsonStr);
                camera = deserializeCamera(existingCamera);
            } catch (JSONException e) {
                Log.w(TAG, "Could not parse existing camera settings, using default", e);
            }
        }
        if (!writeJsonFileSafe(projectId, storage.getFileNameCamera(), serializeCamera(camera))) {
            Log.e(TAG, "Failed to save camera for project: " + projectId);
            allSuccess = false;
        }

        // Save settings.json
        Map<String, Object> settings = new HashMap<>();
        settings.put("gridSize", scene != null ? scene.getGridSize() : 32);
        settings.put("ambientLight", scene != null ? scene.getAmbientLight() : 0.8f);
        settings.put("version", metadata.getVersion());
        JSONObject settingsJson = serializeSettings(settings);
        if (!writeJsonFileSafe(projectId, storage.getFileNameSettings(), settingsJson)) {
            Log.e(TAG, "Failed to save settings for project: " + projectId);
            allSuccess = false;
        }

        // Save metadata.json
        JSONObject metadataJson = metadata.toJSON();
        if (!writeJsonFileSafe(projectId, storage.getFileNameMetadata(), metadataJson)) {
            Log.e(TAG, "Failed to save metadata for project: " + projectId);
            allSuccess = false;
        }

        // Save project.m3x (bundled project file)
        JSONObject bundled = new JSONObject();
        try {
            bundled.put("metadata", metadataJson);
            bundled.put("scene", scene != null ? serializeScene(scene) : new JSONObject());
            bundled.put("settings", settingsJson);
            bundled.put("camera", serializeCamera(camera));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create bundled project file", e);
            allSuccess = false;
        }
        if (!writeJsonFileSafe(projectId, storage.getFileNameProject(), bundled)) {
            Log.e(TAG, "Failed to save project.m3x for project: " + projectId);
            allSuccess = false;
        }

        // Update file size in metadata
        metadata.setFileSize(storage.getProjectSize(projectId));

        if (allSuccess) {
            Log.i(TAG, "Project saved successfully: " + projectId);
        } else {
            Log.w(TAG, "Project saved with some errors: " + projectId);
        }

        return allSuccess;
    }

    /**
     * Loads a complete project from disk.
     *
     * @param projectId the project ID to load
     * @return ProjectData containing the loaded scene and metadata, or null on failure
     */
    public ProjectData loadProject(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            Log.e(TAG, "Cannot load project: projectId is null or empty");
            return null;
        }

        if (!storage.projectExists(projectId)) {
            Log.e(TAG, "Project does not exist: " + projectId);
            return null;
        }

        ProjectData data = new ProjectData();

        // Try loading from project.m3x first (bundled format)
        File bundledFile = storage.getProjectFile(projectId, storage.getFileNameProject());
        if (bundledFile.exists() && bundledFile.length() > 0) {
            String bundledStr = readTextFile(bundledFile);
            if (bundledStr != null) {
                try {
                    JSONObject bundled = new JSONObject(bundledStr);
                    if (bundled.has("scene")) {
                        data.scene = deserializeScene(bundled.getJSONObject("scene"));
                    }
                    if (bundled.has("metadata")) {
                        data.metadata = ProjectMetadata.fromJSON(bundled.getJSONObject("metadata"));
                    }
                    if (bundled.has("settings")) {
                        data.settings = deserializeSettings(bundled.getJSONObject("settings"));
                    }
                    Log.i(TAG, "Project loaded from bundled format: " + projectId);
                    return data;
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse bundled project file, falling back to separate files", e);
                }
            }
        }

        // Fall back to loading separate files
        // Load metadata
        File metadataFile = storage.getProjectFile(projectId, storage.getFileNameMetadata());
        if (metadataFile.exists()) {
            String metadataStr = readTextFile(metadataFile);
            if (metadataStr != null) {
                try {
                    data.metadata = ProjectMetadata.fromJSON(new JSONObject(metadataStr));
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse metadata for project: " + projectId, e);
                    data.metadata = ProjectMetadata.createNew("Unknown");
                }
            }
        } else {
            Log.w(TAG, "No metadata file found for project: " + projectId);
            data.metadata = ProjectMetadata.createNew("Unknown");
        }

        // Load scene
        File sceneFile = storage.getProjectFile(projectId, storage.getFileNameScene());
        if (sceneFile.exists() && sceneFile.length() > 0) {
            String sceneStr = readTextFile(sceneFile);
            if (sceneStr != null) {
                try {
                    data.scene = deserializeScene(new JSONObject(sceneStr));
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse scene for project: " + projectId, e);
                    data.scene = new Scene();
                }
            }
        } else {
            data.scene = new Scene();
        }

        // Load settings
        File settingsFile = storage.getProjectFile(projectId, storage.getFileNameSettings());
        if (settingsFile.exists() && settingsFile.length() > 0) {
            String settingsStr = readTextFile(settingsFile);
            if (settingsStr != null) {
                try {
                    data.settings = deserializeSettings(new JSONObject(settingsStr));
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse settings for project: " + projectId, e);
                    data.settings = new HashMap<>();
                }
            }
        } else {
            data.settings = new HashMap<>();
        }

        Log.i(TAG, "Project loaded from separate files: " + projectId);
        return data;
    }

    /**
     * Loads only the metadata for a project (lightweight operation).
     *
     * @param projectId the project ID
     * @return the ProjectMetadata, or null if not found
     */
    public ProjectMetadata loadMetadata(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return null;
        }
        File metadataFile = storage.getProjectFile(projectId, storage.getFileNameMetadata());
        if (!metadataFile.exists()) {
            return null;
        }
        String metadataStr = readTextFile(metadataFile);
        if (metadataStr == null) {
            return null;
        }
        try {
            return ProjectMetadata.fromJSON(new JSONObject(metadataStr));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse metadata for: " + projectId, e);
            return null;
        }
    }

    /**
     * Saves only the metadata for a project.
     *
     * @param projectId the project ID
     * @param metadata  the metadata to save
     * @return true if saved successfully
     */
    public boolean saveMetadata(String projectId, ProjectMetadata metadata) {
        if (projectId == null || metadata == null) {
            return false;
        }
        return writeJsonFileSafe(projectId, storage.getFileNameMetadata(), metadata.toJSON());
    }

    /**
     * Saves project data to the autosave file instead of the main files.
     *
     * @param projectId the project ID
     * @param scene     the scene data
     * @param metadata  the project metadata
     * @return true if saved successfully
     */
    public boolean saveAutoSave(String projectId, Scene scene, ProjectMetadata metadata) {
        if (projectId == null || projectId.isEmpty()) {
            return false;
        }

        JSONObject autoSaveData = new JSONObject();
        try {
            autoSaveData.put("timestamp", System.currentTimeMillis());
            autoSaveData.put("metadata", metadata != null ? metadata.toJSON() : new JSONObject());
            autoSaveData.put("scene", scene != null ? serializeScene(scene) : new JSONObject());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create autosave data", e);
            return false;
        }

        File autoSaveFile = storage.getAutoSaveFile(projectId);
        return writeTextFileSafe(autoSaveFile, autoSaveData.toString());
    }

    /**
     * Loads project data from the autosave file.
     *
     * @param projectId the project ID
     * @return ProjectData from autosave, or null if not available
     */
    public ProjectData loadAutoSave(String projectId) {
        File autoSaveFile = storage.getAutoSaveFile(projectId);
        if (!autoSaveFile.exists() || autoSaveFile.length() == 0) {
            return null;
        }

        String autoSaveStr = readTextFile(autoSaveFile);
        if (autoSaveStr == null) {
            return null;
        }

        try {
            JSONObject autoSaveData = new JSONObject(autoSaveStr);
            ProjectData data = new ProjectData();

            if (autoSaveData.has("metadata")) {
                data.metadata = ProjectMetadata.fromJSON(autoSaveData.getJSONObject("metadata"));
            }
            if (autoSaveData.has("scene")) {
                data.scene = deserializeScene(autoSaveData.getJSONObject("scene"));
            }

            Log.i(TAG, "Autosave data loaded for project: " + projectId);
            return data;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse autosave data for: " + projectId, e);
            return null;
        }
    }

    // =========================================================================
    // Safe File I/O
    // =========================================================================

    /**
     * Writes a JSONObject to a file using the safe-write pattern.
     * Writes to a temp file first, then renames to the target file.
     *
     * @param projectId the project ID
     * @param filename  the target filename
     * @param json      the JSON data to write
     * @return true if the write succeeded
     */
    private boolean writeJsonFileSafe(String projectId, String filename, JSONObject json) {
        File targetFile = storage.getProjectFile(projectId, filename);
        String content = json.toString(2);
        return writeTextFileSafe(targetFile, content);
    }

    /**
     * Writes a JSONArray to a file using the safe-write pattern.
     *
     * @param projectId the project ID
     * @param filename  the target filename
     * @param json      the JSON array to write
     * @return true if the write succeeded
     */
    private boolean writeJsonArrayFileSafe(String projectId, String filename, JSONArray json) {
        File targetFile = storage.getProjectFile(projectId, filename);
        String content = json.toString(2);
        return writeTextFileSafe(targetFile, content);
    }

    /**
     * Writes text content to a file safely.
     * Creates a temporary file first, writes content, then atomically renames.
     *
     * @param targetFile the final target file
     * @param content    the text content to write
     * @return true if the write succeeded
     */
    private boolean writeTextFileSafe(File targetFile, String content) {
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = new File(targetFile.getParent(), TEMP_PREFIX + targetFile.getName());

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            out.write(content.getBytes("UTF-8"));
            out.flush();
            out.getFD().sync(); // Ensure data is flushed to storage
        } catch (IOException e) {
            Log.e(TAG, "Failed to write temp file: " + tempFile.getAbsolutePath(), e);
            tempFile.delete();
            return false;
        }

        // Delete the old target file if it exists
        if (targetFile.exists()) {
            if (!targetFile.delete()) {
                Log.w(TAG, "Could not delete old file: " + targetFile.getAbsolutePath());
            }
        }

        // Atomically rename temp to target
        boolean renamed = tempFile.renameTo(targetFile);
        if (!renamed) {
            // Fallback: try copying the content
            Log.w(TAG, "Rename failed, attempting copy fallback");
            boolean copied = copyFileContent(tempFile, targetFile);
            tempFile.delete();
            return copied;
        }

        return true;
    }

    /**
     * Copies the content of one file to another.
     */
    private boolean copyFileContent(File source, File dest) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file content", e);
            return false;
        }
    }

    /**
     * Reads the entire content of a text file as a String.
     *
     * @param file the file to read
     * @return the file content, or null on error
     */
    private String readTextFile(File file) {
        if (!file.exists() || file.length() == 0) {
            return null;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read == -1) break;
                offset += read;
            }
            return new String(data, 0, offset, "UTF-8");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Returns the ProjectStorage instance used by this serializer.
     *
     * @return the storage instance
     */
    public ProjectStorage getStorage() {
        return storage;
    }

    // =========================================================================
    // Inner class: ProjectData
    // =========================================================================

    /**
     * Container class for all data associated with a loaded project.
     */
    public static class ProjectData {
        private Scene scene;
        private ProjectMetadata metadata;
        private Map<String, Object> settings;

        /**
         * Default constructor. All fields are null/empty.
         */
        public ProjectData() {
            this.scene = new Scene();
            this.metadata = null;
            this.settings = new HashMap<>();
        }

        /**
         * Full constructor.
         */
        public ProjectData(Scene scene, ProjectMetadata metadata, Map<String, Object> settings) {
            this.scene = scene;
            this.metadata = metadata;
            this.settings = settings != null ? settings : new HashMap<String, Object>();
        }

        public Scene getScene() {
            return scene;
        }

        public void setScene(Scene scene) {
            this.scene = scene;
        }

        public ProjectMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(ProjectMetadata metadata) {
            this.metadata = metadata;
        }

        public Map<String, Object> getSettings() {
            return settings;
        }

        public void setSettings(Map<String, Object> settings) {
            this.settings = settings;
        }

        /**
         * Returns true if this ProjectData has valid scene and metadata.
         */
        public boolean isValid() {
            return scene != null && metadata != null && metadata.isValid();
        }

        @Override
        public String toString() {
            return "ProjectData{scene=" + scene
                    + ", metadata=" + metadata
                    + ", settings=" + (settings != null ? settings.size() + " entries" : "null")
                    + "}";
        }
    }
}
