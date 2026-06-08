package com.dbuild.net.model;

import com.dbuild.net.uv.UVData;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a single face of a 3D block with texture, UV, and color properties.
 * Each face can have its own texture, UV mapping, and color independent of other faces.
 */
public class Face {

    private Block3D.Face faceType;
    private String texturePath;
    private UVData uvData;
    private float[] color;
    private boolean hasTexture;

    /**
     * Creates a face of the given type with default white color and full UV mapping.
     *
     * @param faceType the direction this face represents
     */
    public Face(Block3D.Face faceType) {
        this.faceType = faceType;
        this.texturePath = null;
        this.uvData = new UVData();
        this.color = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.hasTexture = false;
    }

    /**
     * Creates a face with the given type and texture.
     *
     * @param faceType    the direction this face represents
     * @param texturePath path to the texture file
     */
    public Face(Block3D.Face faceType, String texturePath) {
        this.faceType = faceType;
        this.texturePath = texturePath;
        this.uvData = new UVData();
        this.color = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.hasTexture = texturePath != null && !texturePath.isEmpty();
    }

    /**
     * Creates a fully specified face.
     *
     * @param faceType    the direction this face represents
     * @param texturePath path to texture
     * @param uvData      UV mapping data
     * @param color       RGBA color (0-1 range)
     */
    public Face(Block3D.Face faceType, String texturePath, UVData uvData, float[] color) {
        this.faceType = faceType;
        this.texturePath = texturePath;
        this.uvData = uvData != null ? uvData : new UVData();
        this.color = color != null ? color.clone() : new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.hasTexture = texturePath != null && !texturePath.isEmpty();
    }

    // --- Getters and Setters ---

    public Block3D.Face getFaceType() {
        return faceType;
    }

    public void setFaceType(Block3D.Face faceType) {
        this.faceType = faceType;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public UVData getUvData() {
        return uvData;
    }

    public void setUvData(UVData uvData) {
        this.uvData = uvData != null ? uvData : new UVData();
    }

    public float[] getColor() {
        return color.clone();
    }

    /**
     * Sets the face color.
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

    public boolean isHasTexture() {
        return hasTexture;
    }

    // --- Texture management ---

    /**
     * Sets the texture for this face, marking it as textured.
     *
     * @param path path to the texture file
     */
    public void setTexture(String path) {
        this.texturePath = path;
        this.hasTexture = path != null && !path.isEmpty();
    }

    /**
     * Removes the texture from this face.
     */
    public void clearTexture() {
        this.texturePath = null;
        this.hasTexture = false;
    }

    // --- Geometry helpers ---

    /**
     * Gets the UV coordinates for this face as a flat array.
     * Returns 8 floats (4 UV pairs for a quad).
     * Default UV maps the full texture (0,0) to (1,1).
     *
     * @return float[8] UV coordinates: {u0,v0, u1,v1, u2,v2, u3,v3}
     */
    public float[] getUVCoordinates() {
        if (uvData != null) {
            float[] uv = uvData.getUVCoordinates();
            if (uv != null && uv.length == 8) {
                return uv;
            }
        }
        // Default full-texture UV mapping for a quad
        return new float[]{
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        };
    }

    /**
     * Gets the vertex positions for this face in model space.
     * The block is centered on its grid position, with each face
     * offset by half the block size.
     *
     * @param blockSize size of the block (typically 1.0)
     * @return float[12] vertex positions: 4 vertices x 3 coordinates
     */
    public float[] getVertexPositions(float blockSize) {
        float half = blockSize / 2.0f;
        switch (faceType) {
            case TOP:
                // Y+ face
                return new float[]{
                        -half, half, -half,
                        half, half, -half,
                        half, half, half,
                        -half, half, half
                };
            case BOTTOM:
                // Y- face
                return new float[]{
                        -half, -half, half,
                        half, -half, half,
                        half, -half, -half,
                        -half, -half, -half
                };
            case NORTH:
                // Z- face
                return new float[]{
                        half, -half, -half,
                        -half, -half, -half,
                        -half, half, -half,
                        half, half, -half
                };
            case SOUTH:
                // Z+ face
                return new float[]{
                        -half, -half, half,
                        half, -half, half,
                        half, half, half,
                        -half, half, half
                };
            case EAST:
                // X+ face
                return new float[]{
                        half, -half, half,
                        half, -half, -half,
                        half, half, -half,
                        half, half, half
                };
            case WEST:
                // X- face
                return new float[]{
                        -half, -half, -half,
                        -half, -half, half,
                        -half, half, half,
                        -half, half, -half
                };
            default:
                return new float[12];
        }
    }

    /**
     * Gets the normal vector for this face.
     *
     * @return float[3] normal direction
     */
    public float[] getNormal() {
        switch (faceType) {
            case TOP:
                return new float[]{0.0f, 1.0f, 0.0f};
            case BOTTOM:
                return new float[]{0.0f, -1.0f, 0.0f};
            case NORTH:
                return new float[]{0.0f, 0.0f, -1.0f};
            case SOUTH:
                return new float[]{0.0f, 0.0f, 1.0f};
            case EAST:
                return new float[]{1.0f, 0.0f, 0.0f};
            case WEST:
                return new float[]{-1.0f, 0.0f, 0.0f};
            default:
                return new float[]{0.0f, 1.0f, 0.0f};
        }
    }

    /**
     * Gets the draw order indices for a quad (two triangles).
     *
     * @return short[6] indices: {0,1,2, 0,2,3}
     */
    public static short[] getQuadIndices() {
        return new short[]{0, 1, 2, 0, 2, 3};
    }

    // --- Serialization ---

    /**
     * Serializes this face to a JSONObject.
     *
     * @return JSONObject representation
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("faceType", faceType.name());
            json.put("hasTexture", hasTexture);

            if (texturePath != null) {
                json.put("texturePath", texturePath);
            }

            JSONObject colorObj = new JSONObject();
            colorObj.put("r", color[0]);
            colorObj.put("g", color[1]);
            colorObj.put("b", color[2]);
            colorObj.put("a", color[3]);
            json.put("color", colorObj);

            if (uvData != null) {
                json.put("uvData", uvData.toJSON());
            }

        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize Face", e);
        }
        return json;
    }

    /**
     * Deserializes a Face from a JSONObject.
     *
     * @param json     JSONObject to deserialize
     * @param faceType the face type to create
     * @return deserialized Face
     * @throws JSONException if required fields are missing
     */
    public static Face fromJSON(JSONObject json, Block3D.Face faceType) throws JSONException {
        Face face = new Face(faceType);

        face.hasTexture = json.optBoolean("hasTexture", false);
        face.texturePath = json.optString("texturePath", null);
        if (face.texturePath != null && face.texturePath.isEmpty()) {
            face.texturePath = null;
        }

        if (json.has("color")) {
            JSONObject colorObj = json.getJSONObject("color");
            face.color[0] = (float) colorObj.optDouble("r", 1.0);
            face.color[1] = (float) colorObj.optDouble("g", 1.0);
            face.color[2] = (float) colorObj.optDouble("b", 1.0);
            face.color[3] = (float) colorObj.optDouble("a", 1.0);
        }

        if (json.has("uvData")) {
            face.uvData = UVData.fromJSON(json.getJSONObject("uvData"));
        }

        return face;
    }

    @Override
    public String toString() {
        return "Face{" +
                "faceType=" + faceType +
                ", hasTexture=" + hasTexture +
                ", texturePath='" + texturePath + '\'' +
                '}';
    }
}
