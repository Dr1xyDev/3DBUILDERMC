package com.dbuild.net.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Represents material properties for rendering, including diffuse color,
 * specular highlights, transparency, and texture mapping.
 */
public class Material {

    private String materialId;
    private String materialName;
    private String diffuseTexturePath;
    private float[] diffuseColor;
    private float[] specularColor;
    private float shininess;
    private float opacity;
    private boolean isTransparent;

    /**
     * Default constructor. Creates a white opaque material.
     */
    public Material() {
        this.materialId = UUID.randomUUID().toString();
        this.materialName = "Default Material";
        this.diffuseTexturePath = null;
        this.diffuseColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.specularColor = new float[]{0.5f, 0.5f, 0.5f};
        this.shininess = 32.0f;
        this.opacity = 1.0f;
        this.isTransparent = false;
    }

    /**
     * Creates a material with a given name.
     *
     * @param materialName display name of the material
     */
    public Material(String materialName) {
        this.materialId = UUID.randomUUID().toString();
        this.materialName = materialName != null ? materialName : "Unnamed Material";
        this.diffuseTexturePath = null;
        this.diffuseColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.specularColor = new float[]{0.5f, 0.5f, 0.5f};
        this.shininess = 32.0f;
        this.opacity = 1.0f;
        this.isTransparent = false;
    }

    /**
     * Full constructor.
     *
     * @param materialId         unique identifier
     * @param materialName       display name
     * @param diffuseTexturePath path to diffuse texture
     * @param diffuseColor       RGBA diffuse color (0-1 range)
     * @param specularColor      RGB specular color (0-1 range)
     * @param shininess          specular shininess exponent
     * @param opacity            opacity (0-1, 1 = fully opaque)
     * @param isTransparent      whether alpha blending is enabled
     */
    public Material(String materialId, String materialName, String diffuseTexturePath,
                    float[] diffuseColor, float[] specularColor, float shininess,
                    float opacity, boolean isTransparent) {
        this.materialId = materialId != null ? materialId : UUID.randomUUID().toString();
        this.materialName = materialName != null ? materialName : "Unnamed Material";
        this.diffuseTexturePath = diffuseTexturePath;
        this.diffuseColor = diffuseColor != null ? diffuseColor.clone() : new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        this.specularColor = specularColor != null ? specularColor.clone() : new float[]{0.5f, 0.5f, 0.5f};
        this.shininess = shininess;
        this.opacity = opacity;
        this.isTransparent = isTransparent;
    }

    // --- Getters and Setters ---

    public String getMaterialId() {
        return materialId;
    }

    public void setMaterialId(String materialId) {
        this.materialId = materialId;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public String getDiffuseTexturePath() {
        return diffuseTexturePath;
    }

    public void setDiffuseTexturePath(String diffuseTexturePath) {
        this.diffuseTexturePath = diffuseTexturePath;
    }

    public float[] getDiffuseColor() {
        return diffuseColor.clone();
    }

    /**
     * Sets the diffuse color.
     *
     * @param diffuseColor RGBA array (0-1 range), must be length 4
     * @throws IllegalArgumentException if color is null or wrong length
     */
    public void setDiffuseColor(float[] diffuseColor) {
        if (diffuseColor == null || diffuseColor.length != 4) {
            throw new IllegalArgumentException("Diffuse color must be a float[4] (RGBA)");
        }
        this.diffuseColor = diffuseColor.clone();
    }

    public float[] getSpecularColor() {
        return specularColor.clone();
    }

    /**
     * Sets the specular color.
     *
     * @param specularColor RGB array (0-1 range), must be length 3
     * @throws IllegalArgumentException if color is null or wrong length
     */
    public void setSpecularColor(float[] specularColor) {
        if (specularColor == null || specularColor.length != 3) {
            throw new IllegalArgumentException("Specular color must be a float[3] (RGB)");
        }
        this.specularColor = specularColor.clone();
    }

    public float getShininess() {
        return shininess;
    }

    public void setShininess(float shininess) {
        this.shininess = Math.max(0.0f, shininess);
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        if (this.opacity < 1.0f) {
            this.isTransparent = true;
        }
    }

    public boolean isTransparent() {
        return isTransparent;
    }

    public void setTransparent(boolean transparent) {
        isTransparent = transparent;
    }

    // --- Serialization ---

    /**
     * Serializes this material to a JSONObject.
     *
     * @return JSONObject representation
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("materialId", materialId);
            json.put("materialName", materialName);
            json.put("shininess", shininess);
            json.put("opacity", opacity);
            json.put("isTransparent", isTransparent);

            if (diffuseTexturePath != null) {
                json.put("diffuseTexturePath", diffuseTexturePath);
            }

            JSONObject diffColor = new JSONObject();
            diffColor.put("r", diffuseColor[0]);
            diffColor.put("g", diffuseColor[1]);
            diffColor.put("b", diffuseColor[2]);
            diffColor.put("a", diffuseColor[3]);
            json.put("diffuseColor", diffColor);

            JSONObject specColor = new JSONObject();
            specColor.put("r", specularColor[0]);
            specColor.put("g", specularColor[1]);
            specColor.put("b", specularColor[2]);
            json.put("specularColor", specColor);

        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize Material", e);
        }
        return json;
    }

    /**
     * Deserializes a Material from a JSONObject.
     *
     * @param json JSONObject to deserialize
     * @return deserialized Material
     * @throws JSONException if required fields are missing
     */
    public static Material fromJSON(JSONObject json) throws JSONException {
        Material mat = new Material();

        mat.materialId = json.optString("materialId", UUID.randomUUID().toString());
        mat.materialName = json.optString("materialName", "Unnamed Material");
        mat.diffuseTexturePath = json.optString("diffuseTexturePath", null);
        if (mat.diffuseTexturePath != null && mat.diffuseTexturePath.isEmpty()) {
            mat.diffuseTexturePath = null;
        }
        mat.shininess = (float) json.optDouble("shininess", 32.0);
        mat.opacity = (float) json.optDouble("opacity", 1.0);
        mat.isTransparent = json.optBoolean("isTransparent", false);

        if (json.has("diffuseColor")) {
            JSONObject dc = json.getJSONObject("diffuseColor");
            mat.diffuseColor[0] = (float) dc.optDouble("r", 1.0);
            mat.diffuseColor[1] = (float) dc.optDouble("g", 1.0);
            mat.diffuseColor[2] = (float) dc.optDouble("b", 1.0);
            mat.diffuseColor[3] = (float) dc.optDouble("a", 1.0);
        }

        if (json.has("specularColor")) {
            JSONObject sc = json.getJSONObject("specularColor");
            mat.specularColor[0] = (float) sc.optDouble("r", 0.5);
            mat.specularColor[1] = (float) sc.optDouble("g", 0.5);
            mat.specularColor[2] = (float) sc.optDouble("b", 0.5);
        }

        return mat;
    }

    /**
     * Creates a new Material with a generated UUID.
     *
     * @param name display name for the material
     * @return new Material instance
     */
    public static Material createNew(String name) {
        return new Material(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Material material = (Material) o;
        return materialId.equals(material.materialId);
    }

    @Override
    public int hashCode() {
        return materialId.hashCode();
    }

    @Override
    public String toString() {
        return "Material{" +
                "materialId='" + materialId + '\'' +
                ", materialName='" + materialName + '\'' +
                ", opacity=" + opacity +
                ", isTransparent=" + isTransparent +
                '}';
    }
}
