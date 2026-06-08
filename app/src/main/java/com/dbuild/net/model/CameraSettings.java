package com.dbuild.net.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents camera settings for viewing a 3D scene.
 * Stores position, rotation, zoom, and projection parameters.
 */
public class CameraSettings {

    private static final String KEY_POS_X = "posX";
    private static final String KEY_POS_Y = "posY";
    private static final String KEY_POS_Z = "posZ";
    private static final String KEY_ROT_X = "rotX";
    private static final String KEY_ROT_Y = "rotY";
    private static final String KEY_ZOOM = "zoom";
    private static final String KEY_FOV = "fov";
    private static final String KEY_NEAR = "near";
    private static final String KEY_FAR = "far";
    private static final String KEY_ORTHOGRAPHIC = "orthographic";

    private float posX;
    private float posY;
    private float posZ;
    private float rotX;
    private float rotY;
    private float zoom;
    private float fov;
    private float near;
    private float far;
    private boolean orthographic;

    /**
     * Default constructor with sensible defaults for a 3D builder view.
     */
    public CameraSettings() {
        this.posX = 10.0f;
        this.posY = 10.0f;
        this.posZ = 10.0f;
        this.rotX = -30.0f;
        this.rotY = 45.0f;
        this.zoom = 1.0f;
        this.fov = 60.0f;
        this.near = 0.1f;
        this.far = 1000.0f;
        this.orthographic = false;
    }

    /**
     * Full constructor.
     */
    public CameraSettings(float posX, float posY, float posZ, float rotX, float rotY,
                          float zoom, float fov, float near, float far, boolean orthographic) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.zoom = zoom;
        this.fov = fov;
        this.near = near;
        this.far = far;
        this.orthographic = orthographic;
    }

    // --- Getters and Setters ---

    public float getPosX() { return posX; }
    public void setPosX(float posX) { this.posX = posX; }

    public float getPosY() { return posY; }
    public void setPosY(float posY) { this.posY = posY; }

    public float getPosZ() { return posZ; }
    public void setPosZ(float posZ) { this.posZ = posZ; }

    public float getRotX() { return rotX; }
    public void setRotX(float rotX) { this.rotX = rotX; }

    public float getRotY() { return rotY; }
    public void setRotY(float rotY) { this.rotY = rotY; }

    public float getZoom() { return zoom; }
    public void setZoom(float zoom) { this.zoom = zoom; }

    public float getFov() { return fov; }
    public void setFov(float fov) { this.fov = fov; }

    public float getNear() { return near; }
    public void setNear(float near) { this.near = near; }

    public float getFar() { return far; }
    public void setFar(float far) { this.far = far; }

    public boolean isOrthographic() { return orthographic; }
    public void setOrthographic(boolean orthographic) { this.orthographic = orthographic; }

    /**
     * Sets the camera position.
     */
    public void setPosition(float x, float y, float z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    /**
     * Sets the camera rotation (pitch and yaw in degrees).
     */
    public void setRotation(float rotX, float rotY) {
        this.rotX = rotX;
        this.rotY = rotY;
    }

    /**
     * Resets the camera to default view position.
     */
    public void reset() {
        this.posX = 10.0f;
        this.posY = 10.0f;
        this.posZ = 10.0f;
        this.rotX = -30.0f;
        this.rotY = 45.0f;
        this.zoom = 1.0f;
        this.orthographic = false;
    }

    /**
     * Serializes these camera settings to a JSONObject.
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put(KEY_POS_X, posX);
            json.put(KEY_POS_Y, posY);
            json.put(KEY_POS_Z, posZ);
            json.put(KEY_ROT_X, rotX);
            json.put(KEY_ROT_Y, rotY);
            json.put(KEY_ZOOM, zoom);
            json.put(KEY_FOV, fov);
            json.put(KEY_NEAR, near);
            json.put(KEY_FAR, far);
            json.put(KEY_ORTHOGRAPHIC, orthographic);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize CameraSettings", e);
        }
        return json;
    }

    /**
     * Deserializes CameraSettings from a JSONObject.
     */
    public static CameraSettings fromJSON(JSONObject json) throws JSONException {
        CameraSettings cam = new CameraSettings();
        cam.posX = (float) json.optDouble(KEY_POS_X, 10.0);
        cam.posY = (float) json.optDouble(KEY_POS_Y, 10.0);
        cam.posZ = (float) json.optDouble(KEY_POS_Z, 10.0);
        cam.rotX = (float) json.optDouble(KEY_ROT_X, -30.0);
        cam.rotY = (float) json.optDouble(KEY_ROT_Y, 45.0);
        cam.zoom = (float) json.optDouble(KEY_ZOOM, 1.0);
        cam.fov = (float) json.optDouble(KEY_FOV, 60.0);
        cam.near = (float) json.optDouble(KEY_NEAR, 0.1);
        cam.far = (float) json.optDouble(KEY_FAR, 1000.0);
        cam.orthographic = json.optBoolean(KEY_ORTHOGRAPHIC, false);
        return cam;
    }

    @Override
    public String toString() {
        return "CameraSettings{pos=(" + posX + "," + posY + "," + posZ
                + "), rot=(" + rotX + "," + rotY + "), zoom=" + zoom + "}";
    }
}
