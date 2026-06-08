package com.dbuild.net.camera;

import android.opengl.Matrix;
import android.view.MotionEvent;

/**
 * Orthographic camera with no perspective distortion.
 * Supports pan and zoom via touch gestures.
 */
public class OrthographicCamera extends CameraController {

    private float orthoSize = 10.0f;
    private float zoomLevel = 1.0f;
    private float minZoom = 0.1f;
    private float maxZoom = 50.0f;
    private float panSpeed = 0.02f;

    private int activePointerId = -1;
    private float lastTouchX;
    private float lastTouchY;
    private float lastSpan;

    public OrthographicCamera() {
        super();
        eye[0] = 0.0f;
        eye[1] = 20.0f;
        eye[2] = 0.0f;
        target[0] = 0.0f;
        target[1] = 0.0f;
        target[2] = 0.0f;
        up[0] = 0.0f;
        up[1] = 0.0f;
        up[2] = -1.0f;
        fieldOfView = 0.0f; // Not used for orthographic
        markDirty();
    }

    @Override
    public void onUpdate(float deltaTime) {
        // Update orthographic projection based on zoom
        // No per-frame changes needed unless animating
    }

    @Override
    public float[] getProjectionMatrix() {
        float effectiveSize = orthoSize / zoomLevel;
        float left = -effectiveSize * aspectRatio;
        float right = effectiveSize * aspectRatio;
        float bottom = -effectiveSize;
        float top = effectiveSize;

        float[] proj = new float[16];
        Matrix.orthoM(proj, 0, left, right, bottom, top, nearPlane, farPlane);
        return proj;
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
                    // Single finger: pan
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
                    // Two finger: pinch zoom
                    float currentSpan = getSpan(event);
                    if (lastSpan > 0) {
                        float spanDelta = currentSpan / lastSpan;
                        zoom(spanDelta);
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
        return "Orthographic";
    }

    @Override
    public void reset() {
        eye[0] = 0.0f;
        eye[1] = 20.0f;
        eye[2] = 0.0f;
        target[0] = 0.0f;
        target[1] = 0.0f;
        target[2] = 0.0f;
        up[0] = 0.0f;
        up[1] = 0.0f;
        up[2] = -1.0f;
        orthoSize = 10.0f;
        zoomLevel = 1.0f;
        markDirty();
    }

    /**
     * Sets the orthographic view half-height.
     */
    public void setOrthoSize(float size) {
        this.orthoSize = Math.max(0.1f, size);
        markDirty();
    }

    public float getOrthoSize() {
        return orthoSize;
    }

    /**
     * Pans the camera by the given delta in screen-space proportions.
     */
    public void pan(float dx, float dy) {
        float effectiveSize = orthoSize / zoomLevel;
        float worldDx = dx * effectiveSize * 2.0f * aspectRatio;
        float worldDy = dy * effectiveSize * 2.0f;

        // Pan in the camera's local right and up directions
        float[] forward = new float[]{
                target[0] - eye[0],
                target[1] - eye[1],
                target[2] - eye[2]
        };
        float fwdLen = (float) Math.sqrt(forward[0] * forward[0] + forward[1] * forward[1] + forward[2] * forward[2]);
        if (fwdLen > 0.0001f) {
            forward[0] /= fwdLen;
            forward[1] /= fwdLen;
            forward[2] /= fwdLen;
        }

        // Right vector
        float[] right = new float[]{
                forward[1] * up[2] - forward[2] * up[1],
                forward[2] * up[0] - forward[0] * up[2],
                forward[0] * up[1] - forward[1] * up[0]
        };
        float rightLen = (float) Math.sqrt(right[0] * right[0] + right[1] * right[1] + right[2] * right[2]);
        if (rightLen > 0.0001f) {
            right[0] /= rightLen;
            right[1] /= rightLen;
            right[2] /= rightLen;
        }

        // True up vector (right x forward)
        float[] trueUp = new float[]{
                right[1] * forward[2] - right[2] * forward[1],
                right[2] * forward[0] - right[0] * forward[2],
                right[0] * forward[1] - right[1] * forward[0]
        };

        float panX = right[0] * worldDx + trueUp[0] * worldDy;
        float panY = right[1] * worldDx + trueUp[1] * worldDy;
        float panZ = right[2] * worldDx + trueUp[2] * worldDy;

        eye[0] += panX;
        eye[1] += panY;
        eye[2] += panZ;
        target[0] += panX;
        target[1] += panY;
        target[2] += panZ;

        markDirty();
    }

    /**
     * Zooms the camera by the given factor (1.0 = no change, >1 = zoom in, <1 = zoom out).
     */
    public void zoom(float factor) {
        if (factor <= 0.0f) return;
        zoomLevel *= factor;
        zoomLevel = Math.max(minZoom, Math.min(maxZoom, zoomLevel));
        markDirty();
    }

    public float getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(float level) {
        this.zoomLevel = Math.max(minZoom, Math.min(maxZoom, level));
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

    public float getPanSpeed() {
        return panSpeed;
    }

    public void setPanSpeed(float speed) {
        this.panSpeed = Math.max(0.001f, speed);
    }

    private float getSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
