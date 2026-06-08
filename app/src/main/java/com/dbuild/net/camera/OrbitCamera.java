package com.dbuild.net.camera;

import android.animation.ValueAnimator;
import android.view.MotionEvent;

/**
 * Orbit camera that rotates around a target point.
 * Supports one-finger drag to orbit, two-finger pinch to zoom,
 * and two-finger drag to pan.
 */
public class OrbitCamera extends CameraController {

    private float orbitDistance = 10.0f;
    private float orbitAzimuth = 45.0f;
    private float orbitElevation = 30.0f;

    private float minDistance = 1.0f;
    private float maxDistance = 100.0f;
    private float rotateSpeed = 0.3f;
    private float zoomSpeed = 0.01f;
    private float panSpeed = 0.01f;

    private int activePointerId = -1;
    private float lastTouchX;
    private float lastTouchY;
    private float lastSpan;
    private float lastPanX;
    private float lastPanY;
    private boolean isPanning = false;

    private ValueAnimator orbitAnimator;
    private ValueAnimator zoomAnimator;

    public OrbitCamera() {
        super();
        recalculateEyePosition();
    }

    public OrbitCamera(float azimuth, float elevation, float distance) {
        super();
        this.orbitAzimuth = azimuth;
        this.orbitElevation = elevation;
        this.orbitDistance = Math.max(minDistance, Math.min(maxDistance, distance));
        recalculateEyePosition();
    }

    /**
     * Recalculates the eye position from azimuth, elevation, and distance.
     */
    private void recalculateEyePosition() {
        double azimuthRad = Math.toRadians(orbitAzimuth);
        double elevationRad = Math.toRadians(orbitElevation);

        float x = orbitDistance * (float) (Math.cos(elevationRad) * Math.sin(azimuthRad));
        float y = orbitDistance * (float) (Math.sin(elevationRad));
        float z = orbitDistance * (float) (Math.cos(elevationRad) * Math.cos(azimuthRad));

        eye[0] = target[0] + x;
        eye[1] = target[1] + y;
        eye[2] = target[2] + z;

        markDirty();
    }

    @Override
    public void onUpdate(float deltaTime) {
        // Orbit camera doesn't need per-frame updates unless animating
        recalculateEyePosition();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return handleActionDown(event);

            case MotionEvent.ACTION_POINTER_DOWN:
                return handlePointerDown(event);

            case MotionEvent.ACTION_MOVE:
                return handleMove(event);

            case MotionEvent.ACTION_POINTER_UP:
                return handlePointerUp(event);

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = -1;
                isPanning = false;
                return true;
        }

        return false;
    }

    private boolean handleActionDown(MotionEvent event) {
        int index = event.getActionIndex();
        activePointerId = event.getPointerId(index);
        lastTouchX = event.getX(index);
        lastTouchY = event.getY(index);
        isPanning = false;
        lastSpan = 0;
        return true;
    }

    private boolean handlePointerDown(MotionEvent event) {
        if (event.getPointerCount() == 2) {
            isPanning = false;
            lastSpan = getSpan(event);
            lastPanX = (event.getX(0) + event.getX(1)) / 2f;
            lastPanY = (event.getY(0) + event.getY(1)) / 2f;
        }
        return true;
    }

    private boolean handleMove(MotionEvent event) {
        if (event.getPointerCount() == 1 && activePointerId >= 0) {
            // Single finger: orbit
            int index = event.findPointerIndex(activePointerId);
            if (index < 0) return false;

            float x = event.getX(index);
            float y = event.getY(index);
            float dx = x - lastTouchX;
            float dy = y - lastTouchY;

            orbitAzimuth -= dx * rotateSpeed;
            orbitElevation += dy * rotateSpeed;
            orbitElevation = Math.max(-89.0f, Math.min(89.0f, orbitElevation));

            // Keep azimuth in 0-360 range
            orbitAzimuth = orbitAzimuth % 360.0f;
            if (orbitAzimuth < 0) orbitAzimuth += 360.0f;

            lastTouchX = x;
            lastTouchY = y;

            recalculateEyePosition();
            return true;

        } else if (event.getPointerCount() == 2) {
            float currentSpan = getSpan(event);

            if (lastSpan > 0) {
                float spanDelta = currentSpan - lastSpan;

                // Determine if this is a pinch (zoom) or drag (pan)
                float midX = (event.getX(0) + event.getX(1)) / 2f;
                float midY = (event.getY(0) + event.getY(1)) / 2f;
                float panDx = midX - lastPanX;
                float panDy = midY - lastPanY;

                // Zoom based on span change
                orbitDistance -= spanDelta * zoomSpeed;
                orbitDistance = Math.max(minDistance, Math.min(maxDistance, orbitDistance));

                // Pan based on midpoint movement
                if (Math.abs(panDx) > 1f || Math.abs(panDy) > 1f) {
                    panCamera(panDx * panSpeed, panDy * panSpeed);
                    lastPanX = midX;
                    lastPanY = midY;
                }

                recalculateEyePosition();
            }

            lastSpan = currentSpan;
            return true;
        }

        return false;
    }

    private boolean handlePointerUp(MotionEvent event) {
        if (event.getPointerCount() <= 2) {
            // One finger remaining, switch to orbit mode
            int remainingIndex = (event.getActionIndex() == 0) ? 1 : 0;
            if (remainingIndex < event.getPointerCount()) {
                activePointerId = event.getPointerId(remainingIndex);
                lastTouchX = event.getX(remainingIndex);
                lastTouchY = event.getY(remainingIndex);
            }
            isPanning = false;
            lastSpan = 0;
        }
        return true;
    }

    /**
     * Pans the camera by moving both eye and target.
     */
    private void panCamera(float dx, float dy) {
        // Calculate right and up vectors in world space
        float[] forward = new float[]{
                target[0] - eye[0],
                target[1] - eye[1],
                target[2] - eye[2]
        };
        float forwardLen = (float) Math.sqrt(forward[0] * forward[0] + forward[1] * forward[1] + forward[2] * forward[2]);
        if (forwardLen > 0.0001f) {
            forward[0] /= forwardLen;
            forward[1] /= forwardLen;
            forward[2] /= forwardLen;
        }

        // Right = forward x up
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

        float panScale = orbitDistance * 0.5f;

        float panX = (right[0] * dx + up[0] * dy) * panScale;
        float panY = (right[1] * dx + up[1] * dy) * panScale;
        float panZ = (right[2] * dx + up[2] * dy) * panScale;

        eye[0] += panX;
        eye[1] += panY;
        eye[2] += panZ;
        target[0] += panX;
        target[1] += panY;
        target[2] += panZ;
    }

    /**
     * Calculates the span (distance) between two pointers.
     */
    private float getSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String getCameraType() {
        return "Orbit";
    }

    @Override
    public void reset() {
        orbitAzimuth = 45.0f;
        orbitElevation = 30.0f;
        orbitDistance = 10.0f;
        target[0] = 0.0f;
        target[1] = 0.0f;
        target[2] = 0.0f;
        up[0] = 0.0f;
        up[1] = 1.0f;
        up[2] = 0.0f;
        fieldOfView = 45.0f;
        recalculateEyePosition();
    }

    public void setAzimuth(float degrees) {
        this.orbitAzimuth = degrees % 360.0f;
        if (this.orbitAzimuth < 0) this.orbitAzimuth += 360.0f;
        recalculateEyePosition();
    }

    public void setElevation(float degrees) {
        this.orbitElevation = Math.max(-89.0f, Math.min(89.0f, degrees));
        recalculateEyePosition();
    }

    public void setDistance(float distance) {
        this.orbitDistance = Math.max(minDistance, Math.min(maxDistance, distance));
        recalculateEyePosition();
    }

    public float getAzimuth() {
        return orbitAzimuth;
    }

    public float getElevation() {
        return orbitElevation;
    }

    public float getOrbitDistance() {
        return orbitDistance;
    }

    public float getMinDistance() {
        return minDistance;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public void setMinDistance(float min) {
        this.minDistance = Math.max(0.1f, min);
        if (orbitDistance < minDistance) {
            orbitDistance = minDistance;
            recalculateEyePosition();
        }
    }

    public void setMaxDistance(float max) {
        this.maxDistance = Math.max(minDistance, max);
        if (orbitDistance > maxDistance) {
            orbitDistance = maxDistance;
            recalculateEyePosition();
        }
    }

    public void setRotateSpeed(float speed) {
        this.rotateSpeed = Math.max(0.01f, speed);
    }

    public void setZoomSpeed(float speed) {
        this.zoomSpeed = Math.max(0.001f, speed);
    }

    /**
     * Smoothly animates the camera to a new azimuth and elevation.
     */
    public void orbitTo(float targetAzimuth, float targetElevation) {
        if (orbitAnimator != null && orbitAnimator.isRunning()) {
            orbitAnimator.cancel();
        }

        final float startAzimuth = orbitAzimuth;
        final float startElevation = orbitElevation;
        final float endAzimuth = targetAzimuth % 360.0f;
        final float endElevation = Math.max(-89.0f, Math.min(89.0f, targetElevation));

        orbitAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        orbitAnimator.setDuration(400);
        orbitAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (Float) animation.getAnimatedValue();
                // Smooth step interpolation
                t = t * t * (3.0f - 2.0f * t);
                orbitAzimuth = startAzimuth + (endAzimuth - startAzimuth) * t;
                orbitElevation = startElevation + (endElevation - startElevation) * t;
                recalculateEyePosition();
            }
        });
        orbitAnimator.start();
    }

    /**
     * Smoothly animates the camera to a new distance.
     */
    public void zoomTo(float targetDistance) {
        if (zoomAnimator != null && zoomAnimator.isRunning()) {
            zoomAnimator.cancel();
        }

        final float startDistance = orbitDistance;
        final float endDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance));

        zoomAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        zoomAnimator.setDuration(300);
        zoomAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (Float) animation.getAnimatedValue();
                t = t * t * (3.0f - 2.0f * t);
                orbitDistance = startDistance + (endDistance - startDistance) * t;
                recalculateEyePosition();
            }
        });
        zoomAnimator.start();
    }
}
