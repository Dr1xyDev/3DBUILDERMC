package com.dbuild.net.uv;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Objects;

/**
 * Model class representing UV mapping data for a single block face.
 * Contains offset, scale, and rotation parameters for texture mapping.
 */
public class UVData {

    private float u;
    private float v;
    private float uScale;
    private float vScale;
    private float rotation;

    public UVData() {
        this.u = 0.0f;
        this.v = 0.0f;
        this.uScale = 1.0f;
        this.vScale = 1.0f;
        this.rotation = 0.0f;
    }

    public UVData(float u, float v, float uScale, float vScale, float rotation) {
        this.u = u;
        this.v = v;
        this.uScale = uScale;
        this.vScale = vScale;
        this.rotation = rotation;
        clampValues();
    }

    public UVData(UVData other) {
        if (other == null) {
            this.u = 0.0f;
            this.v = 0.0f;
            this.uScale = 1.0f;
            this.vScale = 1.0f;
            this.rotation = 0.0f;
        } else {
            this.u = other.u;
            this.v = other.v;
            this.uScale = other.uScale;
            this.vScale = other.vScale;
            this.rotation = other.rotation;
        }
    }

    public float getU() {
        return u;
    }

    public void setU(float u) {
        this.u = u;
    }

    public float getV() {
        return v;
    }

    public void setV(float v) {
        this.v = v;
    }

    public float getUScale() {
        return uScale;
    }

    public void setUScale(float uScale) {
        this.uScale = uScale;
    }

    public float getVScale() {
        return vScale;
    }

    public void setVScale(float vScale) {
        this.vScale = vScale;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    /**
     * Clamps all values to their valid ranges.
     * u, v: 0.0 - 1.0
     * uScale, vScale: 0.001 - 16.0 (must be positive, minimum meaningful scale)
     * rotation: 0 - 360
     */
    public void clampValues() {
        this.u = Math.max(0.0f, Math.min(1.0f, this.u));
        this.v = Math.max(0.0f, Math.min(1.0f, this.v));
        this.uScale = Math.max(0.001f, Math.min(16.0f, this.uScale));
        this.vScale = Math.max(0.001f, Math.min(16.0f, this.vScale));
        this.rotation = this.rotation % 360.0f;
        if (this.rotation < 0.0f) {
            this.rotation += 360.0f;
        }
    }

    /**
     * Resets all values to defaults: u=0, v=0, uScale=1, vScale=1, rotation=0
     */
    public void reset() {
        this.u = 0.0f;
        this.v = 0.0f;
        this.uScale = 1.0f;
        this.vScale = 1.0f;
        this.rotation = 0.0f;
    }

    /**
     * Serializes this UVData to a JSONObject.
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("u", (double) this.u);
            json.put("v", (double) this.v);
            json.put("uScale", (double) this.uScale);
            json.put("vScale", (double) this.vScale);
            json.put("rotation", (double) this.rotation);
        } catch (JSONException e) {
            // Should never happen with non-null keys
            throw new RuntimeException("Failed to serialize UVData to JSON", e);
        }
        return json;
    }

    /**
     * Deserializes a UVData instance from a JSONObject.
     */
    public static UVData fromJSON(JSONObject json) {
        if (json == null) {
            return new UVData();
        }
        UVData data = new UVData();
        try {
            data.u = (float) json.optDouble("u", 0.0);
            data.v = (float) json.optDouble("v", 0.0);
            data.uScale = (float) json.optDouble("uScale", 1.0);
            data.vScale = (float) json.optDouble("vScale", 1.0);
            data.rotation = (float) json.optDouble("rotation", 0.0);
        } catch (Exception e) {
            // Return defaults on any parse error
            return new UVData();
        }
        data.clampValues();
        return data;
    }

    /**
     * Converts this UVData to a float array.
     * @return float[5] = {u, v, uScale, vScale, rotation}
     */
    public float[] toArray() {
        return new float[]{u, v, uScale, vScale, rotation};
    }

    /**
     * Creates a UVData instance from a float array.
     * @param arr float array of length 5: {u, v, uScale, vScale, rotation}
     * @return new UVData instance
     * @throws IllegalArgumentException if array is null or wrong length
     */
    public static UVData fromArray(float[] arr) {
        if (arr == null) {
            throw new IllegalArgumentException("Array must not be null");
        }
        if (arr.length != 5) {
            throw new IllegalArgumentException("Array must have length 5, got " + arr.length);
        }
        return new UVData(arr[0], arr[1], arr[2], arr[3], arr[4]);
    }

    /**
     * Computes the right UV coordinate (u + uScale, clamped).
     */
    public float getURight() {
        return Math.min(1.0f, u + uScale);
    }

    /**
     * Computes the bottom UV coordinate (v + vScale, clamped).
     */
    public float getVBottom() {
        return Math.min(1.0f, v + vScale);
    }

    /**
     * Checks if this UVData represents the default mapping (0,0,1,1,0).
     */
    public boolean isDefault() {
        return Float.compare(u, 0.0f) == 0
                && Float.compare(v, 0.0f) == 0
                && Float.compare(uScale, 1.0f) == 0
                && Float.compare(vScale, 1.0f) == 0
                && Float.compare(rotation, 0.0f) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UVData uvData = (UVData) o;
        return Float.compare(uvData.u, u) == 0
                && Float.compare(uvData.v, v) == 0
                && Float.compare(uvData.uScale, uScale) == 0
                && Float.compare(uvData.vScale, vScale) == 0
                && Float.compare(uvData.rotation, rotation) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(u, v, uScale, vScale, rotation);
    }

    @Override
    public String toString() {
        return "UVData{" +
                "u=" + String.format("%.4f", u) +
                ", v=" + String.format("%.4f", v) +
                ", uScale=" + String.format("%.4f", uScale) +
                ", vScale=" + String.format("%.4f", vScale) +
                ", rotation=" + String.format("%.1f", rotation) +
                '}';
    }
}
