package com.dbuild.net.blocks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Model class representing a custom block in the 3D Builder MC application.
 * Each block has unique ID, name, type, per-face texture references,
 * transparency/emission properties, and a custom RGBA color.
 */
public class CustomBlock {

    /**
     * Enum representing the six faces of a cubic block.
     * Maps to standard Minecraft face naming conventions.
     */
    public enum BlockFace {
        TOP,
        BOTTOM,
        NORTH,
        SOUTH,
        EAST,
        WEST;

        /**
         * Convert from ordinal index back to BlockFace.
         */
        public static BlockFace fromOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal >= values().length) {
                throw new IllegalArgumentException("Invalid ordinal: " + ordinal);
            }
            return values()[ordinal];
        }

        /**
         * Parse a BlockFace from its name string (case-insensitive).
         */
        public static BlockFace fromString(String name) {
            if (name == null) {
                throw new IllegalArgumentException("BlockFace name cannot be null");
            }
            return valueOf(name.toUpperCase());
        }
    }

    private String blockId;
    private String blockName;
    private String blockType;
    private BlockTextureSet textureSet;
    private boolean isTransparent;
    private boolean isEmissive;
    private float emissiveStrength;
    private float[] customColor;
    private String category;
    private long createdAt;
    private long modifiedAt;

    /**
     * Default constructor. Creates an empty block with default values.
     */
    public CustomBlock() {
        this.blockId = UUID.randomUUID().toString();
        this.blockName = "Unnamed Block";
        this.blockType = "custom";
        this.textureSet = new BlockTextureSet();
        this.isTransparent = false;
        this.isEmissive = false;
        this.emissiveStrength = 0.0f;
        this.customColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.category = "misc";
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.modifiedAt = now;
    }

    /**
     * Constructor with a specified block name.
     *
     * @param blockName the display name of the block
     */
    public CustomBlock(String blockName) {
        this();
        this.blockName = blockName;
    }

    /**
     * Full constructor with all properties.
     */
    public CustomBlock(String blockId, String blockName, String blockType,
                       BlockTextureSet textureSet, boolean isTransparent,
                       boolean isEmissive, float emissiveStrength,
                       float[] customColor, String category,
                       long createdAt, long modifiedAt) {
        this.blockId = blockId;
        this.blockName = blockName;
        this.blockType = blockType;
        this.textureSet = textureSet;
        this.isTransparent = isTransparent;
        this.isEmissive = isEmissive;
        this.emissiveStrength = emissiveStrength;
        this.customColor = customColor;
        this.category = category;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    // ========== Getters and Setters ==========

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
        this.modifiedAt = System.currentTimeMillis();
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName;
        this.modifiedAt = System.currentTimeMillis();
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(String blockType) {
        this.blockType = blockType;
        this.modifiedAt = System.currentTimeMillis();
    }

    public BlockTextureSet getTextureSet() {
        return textureSet;
    }

    public void setTextureSet(BlockTextureSet textureSet) {
        this.textureSet = textureSet;
        this.modifiedAt = System.currentTimeMillis();
    }

    public boolean isTransparent() {
        return isTransparent;
    }

    public void setTransparent(boolean transparent) {
        isTransparent = transparent;
        this.modifiedAt = System.currentTimeMillis();
    }

    public boolean isEmissive() {
        return isEmissive;
    }

    public void setEmissive(boolean emissive) {
        isEmissive = emissive;
        this.modifiedAt = System.currentTimeMillis();
    }

    public float getEmissiveStrength() {
        return emissiveStrength;
    }

    public void setEmissiveStrength(float emissiveStrength) {
        this.emissiveStrength = Math.max(0.0f, emissiveStrength);
        this.modifiedAt = System.currentTimeMillis();
    }

    public float[] getCustomColor() {
        return customColor;
    }

    public void setCustomColor(float[] customColor) {
        if (customColor == null || customColor.length != 4) {
            throw new IllegalArgumentException("Custom color must be a 4-element RGBA array");
        }
        this.customColor = customColor;
        this.modifiedAt = System.currentTimeMillis();
    }

    public void setCustomColor(float r, float g, float b, float a) {
        this.customColor = new float[]{r, g, b, a};
        this.modifiedAt = System.currentTimeMillis();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        this.modifiedAt = System.currentTimeMillis();
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

    // ========== Face Texture Helpers ==========

    /**
     * Gets the texture file path for the specified face.
     *
     * @param face the block face
     * @return the texture path, or null if no texture is assigned
     */
    public String getFaceTexturePath(BlockFace face) {
        if (textureSet == null) {
            return null;
        }
        return textureSet.getTexturePath(face);
    }

    /**
     * Sets the texture file path for the specified face.
     *
     * @param face         the block face
     * @param texturePath  the path to the texture file
     */
    public void setFaceTexture(BlockFace face, String texturePath) {
        if (textureSet == null) {
            textureSet = new BlockTextureSet();
        }
        textureSet.setTexturePath(face, texturePath);
        this.modifiedAt = System.currentTimeMillis();
    }

    // ========== Serialization ==========

    /**
     * Converts this CustomBlock to a JSONObject for persistence.
     *
     * @return JSONObject representation of this block
     */
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("blockId", blockId);
        json.put("blockName", blockName);
        json.put("blockType", blockType);
        json.put("isTransparent", isTransparent);
        json.put("isEmissive", isEmissive);
        json.put("emissiveStrength", emissiveStrength);

        JSONArray colorArray = new JSONArray();
        for (float c : customColor) {
            colorArray.put(c);
        }
        json.put("customColor", colorArray);

        json.put("category", category);
        json.put("createdAt", createdAt);
        json.put("modifiedAt", modifiedAt);

        if (textureSet != null) {
            json.put("textureSet", textureSet.toJSON());
        }

        return json;
    }

    /**
     * Creates a CustomBlock from a JSONObject.
     *
     * @param json the JSONObject to parse
     * @return a new CustomBlock instance
     * @throws JSONException if the JSON structure is invalid
     */
    public static CustomBlock fromJSON(JSONObject json) throws JSONException {
        if (json == null) {
            throw new JSONException("Cannot parse null JSON");
        }

        CustomBlock block = new CustomBlock();
        block.blockId = json.optString("blockId", UUID.randomUUID().toString());
        block.blockName = json.optString("blockName", "Unnamed Block");
        block.blockType = json.optString("blockType", "custom");
        block.isTransparent = json.optBoolean("isTransparent", false);
        block.isEmissive = json.optBoolean("isEmissive", false);
        block.emissiveStrength = (float) json.optDouble("emissiveStrength", 0.0);
        block.category = json.optString("category", "misc");
        block.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        block.modifiedAt = json.optLong("modifiedAt", System.currentTimeMillis());

        JSONArray colorArray = json.optJSONArray("customColor");
        if (colorArray != null && colorArray.length() == 4) {
            block.customColor = new float[4];
            for (int i = 0; i < 4; i++) {
                block.customColor[i] = (float) colorArray.optDouble(i, 1.0);
            }
        } else {
            block.customColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }

        JSONObject textureSetJson = json.optJSONObject("textureSet");
        if (textureSetJson != null) {
            block.textureSet = BlockTextureSet.fromJSON(textureSetJson);
        } else {
            block.textureSet = new BlockTextureSet();
        }

        return block;
    }

    // ========== Factory Method ==========

    /**
     * Factory method to create a new CustomBlock with a generated UUID.
     *
     * @param name the display name for the new block
     * @return a new CustomBlock instance
     */
    public static CustomBlock createNew(String name) {
        CustomBlock block = new CustomBlock(name);
        return block;
    }

    // ========== Utility Methods ==========

    /**
     * Gets the RGB color as an Android-compatible int color.
     *
     * @return int representation of the RGBA color
     */
    public int getColorAsInt() {
        int a = Math.round(customColor[3] * 255);
        int r = Math.round(customColor[0] * 255);
        int g = Math.round(customColor[1] * 255);
        int b = Math.round(customColor[2] * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Checks if this block has any custom textures assigned.
     *
     * @return true if at least one face has a custom texture
     */
    public boolean hasAnyCustomTexture() {
        if (textureSet == null) {
            return false;
        }
        for (BlockFace face : BlockFace.values()) {
            if (textureSet.hasCustomTexture(face)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the same texture to all faces.
     *
     * @param texturePath the path to apply to all faces
     */
    public void setAllFacesTexture(String texturePath) {
        if (textureSet == null) {
            textureSet = new BlockTextureSet();
        }
        textureSet.setAllFaces(texturePath);
        this.modifiedAt = System.currentTimeMillis();
    }

    /**
     * Clears all face textures.
     */
    public void clearAllTextures() {
        if (textureSet != null) {
            textureSet.clearAllTextures();
        }
        this.modifiedAt = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomBlock that = (CustomBlock) o;
        return blockId != null ? blockId.equals(that.blockId) : that.blockId == null;
    }

    @Override
    public int hashCode() {
        return blockId != null ? blockId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "CustomBlock{" +
                "blockId='" + blockId + '\'' +
                ", blockName='" + blockName + '\'' +
                ", blockType='" + blockType + '\'' +
                ", category='" + category + '\'' +
                ", isTransparent=" + isTransparent +
                ", isEmissive=" + isEmissive +
                '}';
    }
}
