package com.dbuild.net.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central scene model that contains all blocks, camera settings, and scene metadata.
 * Blocks are stored keyed by their position string "x,y,z" for fast lookup.
 */
public class Scene {

    private String sceneId;
    private String sceneName;
    private Map<String, Block3D> blocks;
    private CameraSettings cameraSettings;
    private Map<String, Object> settings;
    private long createdAt;
    private long modifiedAt;

    /**
     * Default constructor. Creates an empty scene with a generated UUID.
     */
    public Scene() {
        this.sceneId = UUID.randomUUID().toString();
        this.sceneName = "Untitled Scene";
        this.blocks = new HashMap<>();
        this.cameraSettings = CameraSettings.createDefault();
        this.settings = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
    }

    /**
     * Creates a scene with a given name.
     *
     * @param sceneName name of the scene
     */
    public Scene(String sceneName) {
        this();
        this.sceneName = sceneName != null ? sceneName : "Untitled Scene";
    }

    // --- Basic getters/setters ---

    public String getSceneId() {
        return sceneId;
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId;
    }

    public String getSceneName() {
        return sceneName;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName;
        touchModified();
    }

    public CameraSettings getCameraSettings() {
        return cameraSettings;
    }

    public void setCameraSettings(CameraSettings cameraSettings) {
        this.cameraSettings = cameraSettings != null ? cameraSettings : CameraSettings.createDefault();
        touchModified();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    // --- Settings map ---

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings != null ? settings : new HashMap<>();
    }

    public Object getSetting(String key) {
        return settings.get(key);
    }

    public void putSetting(String key, Object value) {
        settings.put(key, value);
        touchModified();
    }

    public boolean hasSetting(String key) {
        return settings.containsKey(key);
    }

    public Object removeSetting(String key) {
        return settings.remove(key);
    }

    // --- Block operations ---

    /**
     * Adds or replaces a block at its position in the scene.
     *
     * @param block the block to add
     * @throws IllegalArgumentException if block is null
     */
    public void addBlock(Block3D block) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }
        String key = block.getPositionKey();
        blocks.put(key, block);
        touchModified();
    }

    /**
     * Removes the block at the specified grid position.
     *
     * @param x grid x
     * @param y grid y
     * @param z grid z
     * @return the removed Block3D, or null if no block was at that position
     */
    public Block3D removeBlock(int x, int y, int z) {
        String key = Block3D.makePositionKey(x, y, z);
        Block3D removed = blocks.remove(key);
        if (removed != null) {
            touchModified();
        }
        return removed;
    }

    /**
     * Gets the block at the specified grid position.
     *
     * @param x grid x
     * @param y grid y
     * @param z grid z
     * @return Block3D at that position, or null if none exists
     */
    public Block3D getBlock(int x, int y, int z) {
        String key = Block3D.makePositionKey(x, y, z);
        return blocks.get(key);
    }

    /**
     * Checks if a block exists at the specified grid position.
     *
     * @param x grid x
     * @param y grid y
     * @param z grid z
     * @return true if a block exists at that position
     */
    public boolean hasBlock(int x, int y, int z) {
        String key = Block3D.makePositionKey(x, y, z);
        return blocks.containsKey(key);
    }

    /**
     * Gets all blocks in the scene as an unmodifiable list.
     *
     * @return list of all blocks
     */
    public List<Block3D> getAllBlocks() {
        return Collections.unmodifiableList(new ArrayList<>(blocks.values()));
    }

    /**
     * Gets the number of blocks in the scene.
     *
     * @return block count
     */
    public int getBlockCount() {
        return blocks.size();
    }

    /**
     * Removes all blocks from the scene.
     */
    public void clearBlocks() {
        if (!blocks.isEmpty()) {
            blocks.clear();
            touchModified();
        }
    }

    /**
     * Gets all blocks within an axis-aligned range (inclusive).
     *
     * @param x1 min x
     * @param y1 min y
     * @param z1 min z
     * @param x2 max x
     * @param y2 max y
     * @param z2 max z
     * @return list of blocks within the range
     */
    public List<Block3D> getBlocksInRange(int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        List<Block3D> result = new ArrayList<>();
        for (Block3D block : blocks.values()) {
            if (block.getX() >= minX && block.getX() <= maxX &&
                    block.getY() >= minY && block.getY() <= maxY &&
                    block.getZ() >= minZ && block.getZ() <= maxZ) {
                result.add(block);
            }
        }
        return result;
    }

    /**
     * Gets the axis-aligned bounding box of all blocks in the scene.
     *
     * @return int[6] {minX, minY, minZ, maxX, maxY, maxZ}, or {0,0,0,0,0,0} if empty
     */
    public int[] getBounds() {
        if (blocks.isEmpty()) {
            return new int[]{0, 0, 0, 0, 0, 0};
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Block3D block : blocks.values()) {
            minX = Math.min(minX, block.getX());
            minY = Math.min(minY, block.getY());
            minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX());
            maxY = Math.max(maxY, block.getY());
            maxZ = Math.max(maxZ, block.getZ());
        }

        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * Gets the set of all position keys in the scene.
     *
     * @return set of position key strings
     */
    public Set<String> getBlockPositions() {
        return Collections.unmodifiableSet(blocks.keySet());
    }

    /**
     * Updates the modified timestamp.
     */
    private void touchModified() {
        this.modifiedAt = System.currentTimeMillis();
    }

    // --- Serialization ---

    /**
     * Serializes this scene to a JSONObject.
     *
     * @return JSONObject representation
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("sceneId", sceneId);
            json.put("sceneName", sceneName);
            json.put("createdAt", createdAt);
            json.put("modifiedAt", modifiedAt);

            json.put("cameraSettings", cameraSettings.toJSON());

            JSONArray blocksArray = new JSONArray();
            for (Block3D block : blocks.values()) {
                blocksArray.put(block.toJSON());
            }
            json.put("blocks", blocksArray);

            JSONObject settingsObj = new JSONObject();
            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                settingsObj.put(entry.getKey(), entry.getValue());
            }
            json.put("settings", settingsObj);

        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize Scene", e);
        }
        return json;
    }

    /**
     * Deserializes a Scene from a JSONObject.
     *
     * @param json JSONObject to deserialize
     * @return deserialized Scene
     * @throws JSONException if required fields are missing
     */
    public static Scene fromJSON(JSONObject json) throws JSONException {
        Scene scene = new Scene();

        scene.sceneId = json.optString("sceneId", UUID.randomUUID().toString());
        scene.sceneName = json.optString("sceneName", "Untitled Scene");
        scene.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        scene.modifiedAt = json.optLong("modifiedAt", scene.createdAt);

        if (json.has("cameraSettings")) {
            scene.cameraSettings = CameraSettings.fromJSON(json.getJSONObject("cameraSettings"));
        }

        if (json.has("blocks")) {
            JSONArray blocksArray = json.getJSONArray("blocks");
            for (int i = 0; i < blocksArray.length(); i++) {
                Block3D block = Block3D.fromJSON(blocksArray.getJSONObject(i));
                scene.blocks.put(block.getPositionKey(), block);
            }
        }

        if (json.has("settings")) {
            JSONObject settingsObj = json.getJSONObject("settings");
            JSONArray keys = settingsObj.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.getString(i);
                    scene.settings.put(key, settingsObj.get(key));
                }
            }
        }

        return scene;
    }

    /**
     * Creates a new Scene with a generated UUID and the given name.
     *
     * @param name name for the new scene
     * @return new Scene instance
     */
    public static Scene createNew(String name) {
        Scene scene = new Scene(name);
        scene.createdAt = System.currentTimeMillis();
        scene.modifiedAt = scene.createdAt;
        return scene;
    }

    @Override
    public String toString() {
        return "Scene{" +
                "sceneId='" + sceneId + '\'' +
                ", sceneName='" + sceneName + '\'' +
                ", blockCount=" + blocks.size() +
                '}';
    }
}
