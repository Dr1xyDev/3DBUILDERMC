package com.dbuild.net.camera;

import android.view.MotionEvent;

/**
 * 360-degree panoramic camera controller.
 * Allows viewing in all directions from a fixed or orbiting position.
 * Drag rotates the view, pinch zooms, and supports auto-rotation.
 */
public class PanoramicCamera extends CameraController {

    private float horizontalAngle = 0.0f;  // 0-360 degrees
    private float verticalAngle = 0.0f;    // -90 to 90 degrees
    private float panFOV = 90.0f;          // Field of view for panoramic
    private float rotateSpeed = 0.3f;
    private float zoomSpeed = 0.05f;

    // Auto-rotation
    private boolean autoRotating = false;
    private float autoRotateSpeed = 10.0f; // Degrees per second

    // Touch state
    private int activePointerId = -1;
    private float lastTouchX;
    private float lastTouchY;
    private float lastSpan;

    public PanoramicCamera() {
        super();
        eye[0] = 0.0f;
        eye[1] = 5.0f;
        eye[2] = 0.0f;
        fieldOfView = 90.0f;
        recalculateTarget();
    }

    /**
     * Recalculates the target (look-at point) from the current angles.
     */
    private void recalculateTarget() {
        float hRad = (float) Math.toRadians(horizontalAngle);
        float vRad = (float) Math.toRadians(verticalAngle);

        float dirX = (float) (Math.cos(vRad) * Math.sin(hRad));
        float dirY = (float) Math.sin(vRad);
        float dirZ = (float) (-Math.cos(vRad) * Math.cos(hRad));

        target[0] = eye[0] + dirX;
        target[1] = eye[1] + dirY;
        target[2] = eye[2] + dirZ;

        // Recalculate up vector to handle vertical angle properly
        // When looking straight up/down, need to adjust up to avoid gimbal lock
        if (Math.abs(verticalAngle) > 89.0f) {
            up[0] = 0.0f;
            up[1] = 0.0f;
            up[2] = (verticalAngle > 0) ? 1.0f : -1.0f;
        } else {
            up[0] = 0.0f;
            up[1] = 1.0f;
            up[2] = 0.0f;
        }

        markDirty();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (autoRotating) {
            horizontalAngle += autoRotateSpeed * deltaTime;
            horizontalAngle = horizontalAngle % 360.0f;
            if (horizontalAngle < 0) horizontalAngle += 360.0f;
            recalculateTarget();
        }
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
                autoRotating = false; // Stop auto-rotate on touch
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2) {
                    lastSpan = getSpan(event);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && activePointerId >= 0) {
                    // Single finger: rotate view
                    int index = event.findPointerIndex(activePointerId);
                    if (index < 0) return false;

                    float x = event.getX(index);
                    float y = event.getY(index);
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;

                    horizontalAngle += dx * rotateSpeed;
                    verticalAngle += dy * rotateSpeed;

                    // Clamp vertical angle
                    verticalAngle = Math.max(-90.0f, Math.min(90.0f, verticalAngle));

                    // Wrap horizontal angle
                    horizontalAngle = horizontalAngle % 360.0f;
                    if (horizontalAngle < 0) horizontalAngle += 360.0f;

                    lastTouchX = x;
                    lastTouchY = y;

                    recalculateTarget();
                    return true;

                } else if (event.getPointerCount() == 2) {
                    // Two finger: pinch zoom (adjust FOV)
                    float currentSpan = getSpan(event);
                    if (lastSpan > 0) {
                        float spanDelta = currentSpan - lastSpan;
                        panFOV -= spanDelta * zoomSpeed;
                        panFOV = Math.max(30.0f, Math.min(120.0f, panFOV));
                        fieldOfView = panFOV;
                        markDirty();
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
        return "Panoramic";
    }

    @Override
    public void reset() {
        eye[0] = 0.0f;
        eye[1] = 5.0f;
        eye[2] = 0.0f;
        horizontalAngle = 0.0f;
        verticalAngle = 0.0f;
        panFOV = 90.0f;
        fieldOfView = 90.0f;
        autoRotating = false;
        up[0] = 0.0f;
        up[1] = 1.0f;
        up[2] = 0.0f;
        recalculateTarget();
    }

    /**
     * Smoothly rotates to the specified angles.
     */
    public void rotateTo(float horizontal, float vertical) {
        horizontalAngle = horizontal % 360.0f;
        if (horizontalAngle < 0) horizontalAngle += 360.0f;
        verticalAngle = Math.max(-90.0f, Math.min(90.0f, vertical));
        recalculateTarget();
    }

    /**
     * Sets the panoramic field of view.
     * @param fov Field of view in degrees (30-120)
     */
    @Override
    public void setFOV(float fov) {
        this.panFOV = Math.max(30.0f, Math.min(120.0f, fov));
        super.setFOV(this.panFOV);
    }

    /**
     * Enables or disables auto-rotation around the target.
     * @param speed Rotation speed in degrees per second
     */
    public void autoRotate(float speed) {
        this.autoRotateSpeed = speed;
        this.autoRotating = (speed != 0.0f);
    }

    /**
     * Enables auto-rotation at the current speed.
     */
    public void startAutoRotate() {
        autoRotating = true;
    }

    /**
     * Disables auto-rotation.
     */
    public void stopAutoRotate() {
        autoRotating = false;
    }

    public boolean isAutoRotating() {
        return autoRotating;
    }

    public float getHorizontalAngle() {
        return horizontalAngle;
    }

    public float getVerticalAngle() {
        return verticalAngle;
    }

    public float getPanFOV() {
        return panFOV;
    }

    public float getAutoRotateSpeed() {
        return autoRotateSpeed;
    }

    public void setAutoRotateSpeed(float speed) {
        this.autoRotateSpeed = speed;
    }

    public float getRotateSpeed() {
        return rotateSpeed;
    }

    public void setRotateSpeed(float speed) {
        this.rotateSpeed = Math.max(0.01f, speed);
    }

    /**
     * Sets the camera position (the observation point for the panorama).
     */
    public void setObservationPoint(float x, float y, float z) {
        eye[0] = x;
        eye[1] = y;
        eye[2] = z;
        recalculateTarget();
    }

    private float getSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
