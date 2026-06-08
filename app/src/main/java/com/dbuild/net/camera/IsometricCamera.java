package com.dbuild.net.camera;

import android.opengl.Matrix;
import android.view.MotionEvent;

/**
 * Isometric camera with fixed angle and orthographic projection.
 * Standard isometric angle: azimuth=45°, elevation=35.264° (arctan(1/√2)).
 * Supports pan and zoom, with optional slight angle adjustments.
 */
public class IsometricCamera extends CameraController {

    // Standard isometric angle: arctan(1/sqrt(2)) ≈ 35.264 degrees
    private static final float DEFAULT_AZIMUTH = 45.0f;
    private static final float DEFAULT_ELEVATION = 35.264f;

    private float azimuth = DEFAULT_AZIMUTH;
    private float elevation = DEFAULT_ELEVATION;
    private float zoomLevel = 1.0f;
    private float viewSize = 15.0f;
    private float minZoom = 0.1f;
    private float maxZoom = 20.0f;
    private float panSpeed = 0.015f;

    private int activePointerId = -1;
    private float lastTouchX;
    private float lastTouchY;
    private float lastSpan;

    public IsometricCamera() {
        super();
        recalculateEyePosition();
    }

    /**
     * Recalculates the eye position based on azimuth, elevation, and zoom.
     */
    private void recalculateEyePosition() {
        double azimuthRad = Math.toRadians(azimuth);
        double elevationRad = Math.toRadians(elevation);

        // Use a large distance for effective orthographic view
        float distance = 100.0f;

        float x = distance * (float) (Math.cos(elevationRad) * Math.sin(azimuthRad));
        float y = distance * (float) (Math.sin(elevationRad));
        float z = distance * (float) (Math.cos(elevationRad) * Math.cos(azimuthRad));

        eye[0] = target[0] + x;
        eye[1] = target[1] + y;
        eye[2] = target[2] + z;

        markDirty();
    }

    @Override
    public float[] getProjectionMatrix() {
        float effectiveSize = viewSize / zoomLevel;
        float left = -effectiveSize * aspectRatio;
        float right = effectiveSize * aspectRatio;
        float bottom = -effectiveSize;
        float top = effectiveSize;

        float[] proj = new float[16];
        Matrix.orthoM(proj, 0, left, right, bottom, top, nearPlane, farPlane);
        return proj;
    }

    @Override
    public void onUpdate(float deltaTime) {
        recalculateEyePosition();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(event.getActionIndex());
                lastTouchX = event.getX(event.getActionIndex());
                lastTouchY = event.getY(event.getActionIndex());
                lastSpan = 0;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2) {
                    lastSpan = getSpan(event);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && activePointerId >= 0) {
                    int index = event.findPointerIndex(activePointerId);
                    if (index < 0) return false;

                    float x = event.getX(index);
                    float y = event.getY(index);
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;

                    pan(-dx * panSpeed, dy * panSpeed);

                    lastTouchX = x;
                    lastTouchY = y;
                    return true;

                } else if (event.getPointerCount() == 2) {
                    float currentSpan = getSpan(event);
                    if (lastSpan > 0) {
                        float spanRatio = currentSpan / lastSpan;
                        zoom(spanRatio);
                    }
                    lastSpan = currentSpan;
                    return true;
                }
                return false;

            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() <= 2) {
                    int remainingIndex = (event.getActionIndex() == 0) ? 1 : 0;
                    if (remainingIndex < event.getPointerCount()) {
                        activePointerId = event.getPointerId(remainingIndex);
                        lastTouchX = event.getX(remainingIndex);
                        lastTouchY = event.getY(remainingIndex);
                    }
                    lastSpan = 0;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = -1;
                lastSpan = 0;
                return true;
        }

        return false;
    }

    @Override
    public String getCameraType() {
        return "Isometric";
    }

    @Override
    public void reset() {
        azimuth = DEFAULT_AZIMUTH;
        elevation = DEFAULT_ELEVATION;
        zoomLevel = 1.0f;
        viewSize = 15.0f;
        target[0] = 0.0f;
        target[1] = 0.0f;
        target[2] = 0.0f;
        up[0] = 0.0f;
        up[1] = 1.0f;
        up[2] = 0.0f;
        recalculateEyePosition();
    }

    /**
     * Pans the camera target position.
     */
    public void pan(float dx, float dy) {
        float effectiveSize = viewSize / zoomLevel;
        float worldDx = dx * effectiveSize * 2.0f * aspectRatio;
        float worldDy = dy * effectiveSize * 2.0f;

        // Calculate pan in isometric-aligned axes
        // Right direction in isometric view (rotated by azimuth)
        double azimuthRad = Math.toRadians(azimuth);
        float rightX = (float) Math.cos(azimuthRad);
        float rightZ = (float) -Math.sin(azimuthRad);

        // Forward direction perpendicular to right (on ground plane)
        float forwardX = (float) Math.sin(azimuthRad);
        float forwardZ = (float) Math.cos(azimuthRad);

        float panX = rightX * worldDx + forwardX * worldDy;
        float panZ = rightZ * worldDx + forwardZ * worldDy;

        eye[0] += panX;
        eye[2] += panZ;
        target[0] += panX;
        target[2] += panZ;

        markDirty();
    }

    /**
     * Zooms the camera by the given factor.
     */
    public void zoom(float factor) {
        if (factor <= 0.0f) return;
        zoomLevel *= factor;
        zoomLevel = Math.max(minZoom, Math.min(maxZoom, zoomLevel));
        markDirty();
    }

    /**
     * Sets the viewing angle, allowing slight adjustments from true isometric.
     * @param newAzimuth Horizontal angle in degrees
     * @param newElevation Vertical angle in degrees
     */
    public void setAngle(float newAzimuth, float newElevation) {
        this.azimuth = newAzimuth;
        this.elevation = Math.max(5.0f, Math.min(85.0f, newElevation));
        recalculateEyePosition();
    }

    public float getAzimuth() {
        return azimuth;
    }

    public float getElevation() {
        return elevation;
    }

    public float getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(float level) {
        this.zoomLevel = Math.max(minZoom, Math.min(maxZoom, level));
        markDirty();
    }

    public float getViewSize() {
        return viewSize;
    }

    public void setViewSize(float size) {
        this.viewSize = Math.max(1.0f, size);
        markDirty();
    }

    public float getMinZoom() {
        return minZoom;
    }

    public void setMinZoom(float min) {
        this.minZoom = Math.max(0.01f, min);
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    public void setMaxZoom(float max) {
        this.maxZoom = Math.max(minZoom, max);
    }

    private float getSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
