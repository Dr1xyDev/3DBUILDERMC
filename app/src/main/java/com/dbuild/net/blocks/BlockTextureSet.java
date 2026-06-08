package com.dbuild.net.blocks;

import com.dbuild.net.uv.UVData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages texture paths and UV mappings for all six faces of a block.
 * Each face can have its own texture and UV configuration.
 */
public class BlockTextureSet {

    private static final String JSON_KEY_TEXTURES = "texturePaths";
    private static final String JSON_KEY_UVS = "uvMappings";

    private final EnumMap<CustomBlock.BlockFace, String> texturePaths;
    private final EnumMap<CustomBlock.BlockFace, UVData> uvMappings;

    /**
     * Constructs an empty BlockTextureSet with no textures or UV mappings assigned.
     */
    public BlockTextureSet() {
        texturePaths = new EnumMap<>(CustomBlock.BlockFace.class);
        uvMappings = new EnumMap<>(CustomBlock.BlockFace.class);

        for (CustomBlock.BlockFace face : CustomBlock.BlockFace.values()) {
            texturePaths.put(face, null);
            uvMappings.put(face, new UVData());
        }
    }

    // ========== Texture Path Methods ==========

    /**
     * Gets the texture file path for the specified face.
     *
     * @param face the block face to query
     * @return the texture path string, or null if no texture is assigned
     */
    public String getTexturePath(CustomBlock.BlockFace face) {
        if (face == null) {
            return null;
        }
        return texturePaths.get(face);
    }

    /**
     * Sets the texture file path for the specified face.
     *
     * @param face  the block face to assign the texture to
     * @param path  the file path of the texture, or null to clear
     */
    public void setTexturePath(CustomBlock.BlockFace face, String path) {
        if (face == null) {
            throw new IllegalArgumentException("BlockFace cannot be null");
        }
        texturePaths.put(face, path);
    }

    // ========== UV Mapping Methods ==========

    /**
     * Gets the UV data for the specified face.
     *
     * @param face the block face to query
     * @return the UVData for the face, or a default UVData if not set
     */
    public UVData getUV(CustomBlock.BlockFace face) {
        if (face == null) {
            return new UVData();
        }
        UVData uv = uvMappings.get(face);
        return uv != null ? uv : new UVData();
    }

    /**
     * Sets the UV mapping data for the specified face.
     *
     * @param face the block face to assign UV data to
     * @param uv   the UV data to assign
     */
    public void setUV(CustomBlock.BlockFace face, UVData uv) {
        if (face == null) {
            throw new IllegalArgumentException("BlockFace cannot be null");
        }
        uvMappings.put(face, uv != null ? uv : new UVData());
    }

    // ========== Query Methods ==========

    /**
     * Checks whether the specified face has a custom texture assigned.
     *
     * @param face the block face to check
     * @return true if a non-null, non-empty texture path is assigned
     */
    public boolean hasCustomTexture(CustomBlock.BlockFace face) {
        if (face == null) {
            return false;
        }
        String path = texturePaths.get(face);
        return path != null && !path.trim().isEmpty();
    }

    /**
     * Checks whether any face has a custom texture assigned.
     *
     * @return true if at least one face has a custom texture
     */
    public boolean hasAnyCustomTexture() {
        for (CustomBlock.BlockFace face : CustomBlock.BlockFace.values()) {
            if (hasCustomTexture(face)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the number of faces that have custom textures assigned.
     *
     * @return count of faces with textures
     */
    public int getCustomTextureCount() {
        int count = 0;
        for (CustomBlock.BlockFace face : CustomBlock.BlockFace.values()) {
            if (hasCustomTexture(face)) {
                count++;
            }
        }
        return count;
    }

    // ========== Bulk Operations ==========

    /**
     * Sets the same texture path to all six faces.
     *
     * @param texturePath the texture path to apply to all faces
     */
    public void setAllFaces(String texturePath) {
        for (CustomBlock.BlockFace face : CustomBlock.BlockFace.values()) {
            texturePaths.put(face, texturePath);
        }
    }

    /**
     * Clears all texture paths and resets UV mappings to default.
     */
    public void clearAllTextures() {
        for (CustomBlock.BlockFace face : CustomBlock.BlockFace.values()) {
            texturePaths.put(face, null);
            uvMappings.put(face, new UVData());
        }
    }

    /**
     * Copies texture and UV settings from another BlockTextureSet.
     *
     * @param other the source BlockTextureSet to copy from
     */
    public void copyFrom(BlockTextureSet other) {
        if (other == null) {
            return;
        }
        for (CustomBlock.BlockFace face : CustomBlock.BlockFace.values()) {
            String path = other.getTexturePath(face);
            texturePaths.put(face, path);

            UVData uv = other.getUV(face);
            uvMappings.put(face, new UVData(uv.getUOffset(), uv.getVOffset(),
                    uv.getUScale(), uv.getVScale()));
        }
    }

    /**
     * Creates a deep copy of this BlockTextureSet.
     *
     * @return a new BlockTextureSet with the same values
     */
    public BlockTextureSet deepCopy() {
        BlockTextureSet copy = new BlockTextureSet();
        copy.copyFrom(this);
        return copy;
    }

    // ========== Serialization ==========

    /**
     * Converts this BlockTextureSet to a JSONObject.
     *
     * @return JSONObject with texture paths and UV mappings
     */
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();

        JSONObject texturesObj = new JSONObject();
        for (Map.Entry<CustomBlock.BlockFace, String> entry : texturePaths.entrySet()) {
            if (entry.getValue() != null) {
                texturesObj.put(entry.getKey().name(), entry.getValue());
            }
        }
        json.put(JSON_KEY_TEXTURES, texturesObj);

        JSONObject uvsObj = new JSONObject();
        for (Map.Entry<CustomBlock.BlockFace, UVData> entry : uvMappings.entrySet()) {
            if (entry.getValue() != null) {
                uvsObj.put(entry.getKey().name(), entry.getValue().toJSON());
            }
        }
        json.put(JSON_KEY_UVS, uvsObj);

        return json;
    }

    /**
     * Creates a BlockTextureSet from a JSONObject.
     *
     * @param json the JSONObject to parse
     * @return a new BlockTextureSet instance
     * @throws JSONException if the JSON structure is invalid
     */
    public static BlockTextureSet fromJSON(JSONObject json) throws JSONException {
        if (json == null) {
            return new BlockTextureSet();
        }

        BlockTextureSet set = new BlockTextureSet();

        JSONObject texturesObj = json.optJSONObject(JSON_KEY_TEXTURES);
        if (texturesObj != null) {
            Iterator<String> keys = texturesObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    CustomBlock.BlockFace face = CustomBlock.BlockFace.fromString(key);
                    String path = texturesObj.optString(key, null);
                    set.texturePaths.put(face, path);
                } catch (IllegalArgumentException e) {
                    // Skip unknown face names
                }
            }
        }

        JSONObject uvsObj = json.optJSONObject(JSON_KEY_UVS);
        if (uvsObj != null) {
            Iterator<String> keys = uvsObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    CustomBlock.BlockFace face = CustomBlock.BlockFace.fromString(key);
                    JSONObject uvJson = uvsObj.optJSONObject(key);
                    if (uvJson != null) {
                        set.uvMappings.put(face, UVData.fromJSON(uvJson));
                    }
                } catch (IllegalArgumentException e) {
                    // Skip unknown face names
                }
            }
        }

        return set;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockTextureSet that = (BlockTextureSet) o;
        return texturePaths.equals(that.texturePaths) && uvMappings.equals(that.uvMappings);
    }

    @Override
    public int hashCode() {
        int result = texturePaths.hashCode();
        result = 31 * result + uvMappings.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BlockTextureSet{");
        for (CustomBlock.BlockFace face : CustomBlock.BlockFace.values()) {
            String path = texturePaths.get(face);
            if (path != null && !path.isEmpty()) {
                sb.append(face.name()).append("='").append(path).append("', ");
            }
        }
        if (sb.charAt(sb.length() - 1) == ' ') {
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append("}");
        return sb.toString();
    }
}
