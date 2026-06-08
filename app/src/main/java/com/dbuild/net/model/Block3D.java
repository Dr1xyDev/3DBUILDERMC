package com.dbuild.net.model;

import com.dbuild.net.uv.UVData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a 3D block in the scene with grid position, visual properties,
 * and per-face texture/UV/color configuration.
 */
public class Block3D {

    /**
     * The six faces of a cube block.
     */
    public enum Face {
        TOP,
        BOTTOM,
        NORTH,
        SOUTH,
        EAST,
        WEST
    }

    /** Block size used for world position calculation. */
    public static final float BLOCK_SIZE = 1.0f;

    private int x;
    private int y;
    private int z;
    private String blockId;
    private String blockName;
    private float[] color;
    private boolean visible;
    private Map<Face, com.dbuild.net.model.Face> faces;

    /**
     * Default constructor. Creates a block at origin with default white color.
     */
    public Block3D() {
        this(0, 0, 0);
    }

    /**
     * Creates a block at the specified grid position with default properties.
     *
     * @param x grid x position
     * @param y grid y position
     * @param z grid z position
     */
    public Block3D(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = "";
        this.blockName = "Block";
        this.color = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.visible = true;
        this.faces = new EnumMap<>(Face.class);
        initDefaultFaces();
    }

    /**
     * Creates a block at the specified grid position with a block ID and name.
     *
     * @param x         grid x position
     * @param y         grid y position
     * @param z         grid z position
     * @param blockId   reference ID to a CustomBlock definition
     * @param blockName display name
     */
    public Block3D(int x, int y, int z, String blockId, String blockName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId != null ? blockId : "";
        this.blockName = blockName != null ? blockName : "Block";
        this.color = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.visible = true;
        this.faces = new EnumMap<>(Face.class);
        initDefaultFaces();
    }

    /**
     * Initializes all six faces with default color and UV data.
     */
    private void initDefaultFaces() {
        for (Face faceType : Face.values()) {
            Face face = new Face(faceType);
            face.setColor(new float[]{1.0f, 1.0f, 1.0f, 1.0f});
            face.setUvData(new UVData());
            faces.put(faceType, face);
        }
    }

    // --- Grid position getters/setters ---

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    // --- Block identity ---

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId != null ? blockId : "";
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName != null ? blockName : "Block";
    }

    // --- Color ---

    /**
     * Gets the block's overall color (RGBA, 0-1 range).
     *
     * @return copy of color array
     */
    public float[] getColor() {
        return color.clone();
    }

    /**
     * Sets the block's overall color.
     *
     * @param color RGBA array (0-1 range), must be length 4
     * @throws IllegalArgumentException if color is null or wrong length
     */
    public void setColor(float[] color) {
        if (color == null || color.length != 4) {
            throw new IllegalArgumentException("Color must be a float[4] (RGBA)");
        }
        this.color = color.clone();
    }

    // --- Visibility ---

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    // --- Position helpers ---

    /**
     * Gets the actual world position derived from grid coordinates.
     * World position = grid position * BLOCK_SIZE.
     *
     * @return float[3] {worldX, worldY, worldZ}
     */
    public float[] getWorldPosition() {
        return new float[]{
                x * BLOCK_SIZE,
                y * BLOCK_SIZE,
                z * BLOCK_SIZE
        };
    }

    /**
     * Gets the position as a derived array (same as getWorldPosition).
     *
     * @return float[3] world position
     */
    public float[] getPosition() {
        return getWorldPosition();
    }

    // --- Face operations ---

    /**
     * Sets the texture for a specific face.
     *
     * @param face        which face to set
     * @param texturePath path to the texture file
     */
    public void setFaceTexture(Face face, String texturePath) {
        com.dbuild.net.model.Face faceObj = faces.get(face);
        if (faceObj != null) {
            faceObj.setTexture(texturePath);
        }
    }

    /**
     * Sets the UV data for a specific face.
     *
     * @param face which face to set
     * @param uv   UVData to apply
     */
    public void setFaceUV(Face face, UVData uv) {
        com.dbuild.net.model.Face faceObj = faces.get(face);
        if (faceObj != null) {
            faceObj.setUvData(uv);
        }
    }

    /**
     * Gets the Face object for the given face direction.
     *
     * @param face which face direction
     * @return Face object, or null if not found
     */
    public com.dbuild.net.model.Face getFace(Face face) {
        return faces.get(face);
    }

    /**
     * Sets the color for a specific face.
     *
     * @param face  which face to set
     * @param color RGBA color array (0-1 range)
     */
    public void setFaceColor(Face face, float[] color) {
        com.dbuild.net.model.Face faceObj = faces.get(face);
        if (faceObj != null) {
            faceObj.setColor(color);
        }
    }

    /**
     * Gets the map of all faces.
     *
     * @return unmodifiable view of face map
     */
    public Map<Face, com.dbuild.net.model.Face> getFaces() {
        return faces;
    }

    // --- Position key ---

    /**
     * Gets the position key string "x,y,z" for this block.
     *
     * @return position key
     */
    public String getPositionKey() {
        return makePositionKey(x, y, z);
    }

    /**
     * Creates a position key string from grid coordinates.
     *
     * @param x grid x
     * @param y grid y
     * @param z grid z
     * @return position key "x,y,z"
     */
    public static String makePositionKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    // --- Serialization ---

    /**
     * Serializes this block to a JSONObject.
     *
     * @return JSONObject representation
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("x", x);
            json.put("y", y);
            json.put("z", z);
            json.put("blockId", blockId);
            json.put("blockName", blockName);
            json.put("visible", visible);

            JSONObject colorObj = new JSONObject();
            colorObj.put("r", color[0]);
            colorObj.put("g", color[1]);
            colorObj.put("b", color[2]);
            colorObj.put("a", color[3]);
            json.put("color", colorObj);

            JSONObject facesObj = new JSONObject();
            for (Map.Entry<Face, com.dbuild.net.model.Face> entry : faces.entrySet()) {
                facesObj.put(entry.getKey().name(), entry.getValue().toJSON());
            }
            json.put("faces", facesObj);

        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize Block3D", e);
        }
        return json;
    }

    /**
     * Deserializes a Block3D from a JSONObject.
     *
     * @param json JSONObject to deserialize
     * @return deserialized Block3D
     * @throws JSONException if required fields are missing
     */
    public static Block3D fromJSON(JSONObject json) throws JSONException {
        int x = json.getInt("x");
        int y = json.getInt("y");
        int z = json.getInt("z");
        String blockId = json.optString("blockId", "");
        String blockName = json.optString("blockName", "Block");

        Block3D block = new Block3D(x, y, z, blockId, blockName);
        block.visible = json.optBoolean("visible", true);

        if (json.has("color")) {
            JSONObject colorObj = json.getJSONObject("color");
            block.color[0] = (float) colorObj.optDouble("r", 1.0);
            block.color[1] = (float) colorObj.optDouble("g", 1.0);
            block.color[2] = (float) colorObj.optDouble("b", 1.0);
            block.color[3] = (float) colorObj.optDouble("a", 1.0);
        }

        if (json.has("faces")) {
            JSONObject facesObj = json.getJSONObject("faces");
            for (Face faceType : Face.values()) {
                if (facesObj.has(faceType.name())) {
                    JSONObject faceJson = facesObj.getJSONObject(faceType.name());
                    com.dbuild.net.model.Face face = com.dbuild.net.model.Face.fromJSON(faceJson, faceType);
                    block.faces.put(faceType, face);
                }
            }
        }

        return block;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block3D block3D = (Block3D) o;
        return x == block3D.x && y == block3D.y && z == block3D.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "Block3D{" +
                "x=" + x + ", y=" + y + ", z=" + z +
                ", blockName='" + blockName + '\'' +
                ", visible=" + visible +
                '}';
    }
}
