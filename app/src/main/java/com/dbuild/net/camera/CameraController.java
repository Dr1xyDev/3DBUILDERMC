package com.dbuild.net.camera;

import android.opengl.Matrix;
import android.view.MotionEvent;

/**
 * Abstract base class for camera controllers.
 * Provides common camera state and matrix computation,
 * with abstract methods for camera-type-specific behavior.
 */
public abstract class CameraController {

    protected float[] eye = new float[]{0.0f, 5.0f, 10.0f};
    protected float[] target = new float[]{0.0f, 0.0f, 0.0f};
    protected float[] up = new float[]{0.0f, 1.0f, 0.0f};

    protected float fieldOfView = 45.0f;
    protected float nearPlane = 0.1f;
    protected float farPlane = 1000.0f;
    protected float aspectRatio = 1.0f;

    protected int viewportWidth = 1;
    protected int viewportHeight = 1;

    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private boolean viewMatrixDirty = true;
    private boolean projectionMatrixDirty = true;

    /**
     * Called each frame to update camera state.
     * @param deltaTime Time elapsed since last frame in seconds
     */
    public abstract void onUpdate(float deltaTime);

    /**
     * Handles touch input for camera control.
     * @param event The motion event
     * @return true if the event was consumed
     */
    public abstract boolean onTouchEvent(MotionEvent event);

    /**
     * Returns the type identifier for this camera.
     * @return Camera type string (e.g. "Orbit", "FPS", "Orthographic")
     */
    public abstract String getCameraType();

    /**
     * Resets camera to its default position and orientation.
     */
    public abstract void reset();

    /**
     * Computes the view matrix using eye, target, and up vectors.
     * @return 4x4 view matrix as float array
     */
    public float[] getViewMatrix() {
        if (viewMatrixDirty) {
            Matrix.setLookAtM(viewMatrix, 0,
                    eye[0], eye[1], eye[2],
                    target[0], target[1], target[2],
                    up[0], up[1], up[2]);
            viewMatrixDirty = false;
        }
        return viewMatrix;
    }

    /**
     * Computes the projection matrix.
     * Default implementation uses perspective projection.
     * Subclasses may override for orthographic or other projections.
     * @return 4x4 projection matrix as float array
     */
    public float[] getProjectionMatrix() {
        if (projectionMatrixDirty) {
            Matrix.perspectiveM(projectionMatrix, 0, fieldOfView, aspectRatio, nearPlane, farPlane);
            projectionMatrixDirty = false;
        }
        return projectionMatrix;
    }

    /**
     * Sets the camera to look at a specific target point.
     * @param targetPos Target position as float[3]
     */
    public void lookAt(float[] targetPos) {
        if (targetPos == null || targetPos.length < 3) return;
        this.target[0] = targetPos[0];
        this.target[1] = targetPos[1];
        this.target[2] = targetPos[2];
        markDirty();
    }

    /**
     * Sets the camera eye position.
     */
    public void setPosition(float x, float y, float z) {
        this.eye[0] = x;
        this.eye[1] = y;
        this.eye[2] = z;
        markDirty();
    }

    /**
     * Sets the camera target (look-at) position.
     */
    public void setTarget(float x, float y, float z) {
        this.target[0] = x;
        this.target[1] = y;
        this.target[2] = z;
        markDirty();
    }

    /**
     * Sets the field of view in degrees.
     */
    public void setFOV(float fov) {
        this.fieldOfView = Math.max(1.0f, Math.min(179.0f, fov));
        projectionMatrixDirty = true;
    }

    /**
     * Sets the aspect ratio (width/height).
     */
    public void setAspectRatio(float ratio) {
        if (ratio <= 0.0f) return;
        this.aspectRatio = ratio;
        projectionMatrixDirty = true;
    }

    /**
     * Sets the viewport dimensions for screen/world conversions.
     */
    public void setViewport(int width, int height) {
        this.viewportWidth = Math.max(1, width);
        this.viewportHeight = Math.max(1, height);
        this.aspectRatio = (float) viewportWidth / (float) viewportHeight;
        projectionMatrixDirty = true;
    }

    /**
     * Calculates the distance from the eye to the target.
     * @return Distance in world units
     */
    public float getDistanceToTarget() {
        float dx = eye[0] - target[0];
        float dy = eye[1] - target[1];
        float dz = eye[2] - target[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Unprojects a screen point into a world-space ray.
     * @param screenX Screen X coordinate (pixels)
     * @param screenY Screen Y coordinate (pixels, top-left origin)
     * @param screenWidth Viewport width in pixels
     * @param screenHeight Viewport height in pixels
     * @return float[6] containing ray origin (x,y,z) and direction (dx,dy,dz)
     */
    public float[] screenToWorld(float screenX, float screenY, int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return new float[]{0, 0, 0, 0, 0, -1};
        }

        float[] view = getViewMatrix();
        float[] proj = getProjectionMatrix();

        // Combine view and projection
        float[] vpMatrix = new float[16];
        Matrix.multiplyMM(vpMatrix, 0, proj, 0, view, 0);

        float[] invVP = new float[16];
        if (!Matrix.invertM(invVP, 0, vpMatrix, 0)) {
            return new float[]{eye[0], eye[1], eye[2], 0, 0, -1};
        }

        // Convert screen to normalized device coords
        float ndcX = (2.0f * screenX) / screenWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * screenY) / screenHeight;

        // Near point (z=0 in NDC)
        float[] nearPoint = new float[4];
        Matrix.multiplyMV(nearPoint, 0, invVP, 0, new float[]{ndcX, ndcY, -1.0f, 1.0f}, 0);
        if (nearPoint[3] != 0.0f) {
            nearPoint[0] /= nearPoint[3];
            nearPoint[1] /= nearPoint[3];
            nearPoint[2] /= nearPoint[3];
        }

        // Far point (z=1 in NDC)
        float[] farPoint = new float[4];
        Matrix.multiplyMV(farPoint, 0, invVP, 0, new float[]{ndcX, ndcY, 1.0f, 1.0f}, 0);
        if (farPoint[3] != 0.0f) {
            farPoint[0] /= farPoint[3];
            farPoint[1] /= farPoint[3];
            farPoint[2] /= farPoint[3];
        }

        // Ray direction = normalize(far - near)
        float dx = farPoint[0] - nearPoint[0];
        float dy = farPoint[1] - nearPoint[1];
        float dz = farPoint[2] - nearPoint[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.0001f) {
            dx /= len;
            dy /= len;
            dz /= len;
        }

        return new float[]{nearPoint[0], nearPoint[1], nearPoint[2], dx, dy, dz};
    }

    /**
     * Projects a world position to screen coordinates.
     * @param worldPos World position as float[3]
     * @param screenWidth Viewport width in pixels
     * @param screenHeight Viewport height in pixels
     * @return float[2] containing screen (x, y) in pixels, or null if behind camera
     */
    public float[] worldToScreen(float[] worldPos, int screenWidth, int screenHeight) {
        if (worldPos == null || worldPos.length < 3) return null;

        float[] view = getViewMatrix();
        float[] proj = getProjectionMatrix();
        float[] vpMatrix = new float[16];
        Matrix.multiplyMM(vpMatrix, 0, proj, 0, view, 0);

        float[] clipPos = new float[4];
        Matrix.multiplyMV(clipPos, 0, vpMatrix, 0, new float[]{worldPos[0], worldPos[1], worldPos[2], 1.0f}, 0);

        if (clipPos[3] == 0.0f) return null;
        // Behind camera check
        if (clipPos[3] < 0.0f) return null;

        float ndcX = clipPos[0] / clipPos[3];
        float ndcY = clipPos[1] / clipPos[3];

        float screenX = (ndcX + 1.0f) * 0.5f * screenWidth;
        float screenY = (1.0f - ndcY) * 0.5f * screenHeight;

        return new float[]{screenX, screenY};
    }

    /**
     * Marks both matrices as needing recalculation.
     */
    protected void markDirty() {
        viewMatrixDirty = true;
        projectionMatrixDirty = true;
    }

    // Getters
    public float[] getEye() {
        return new float[]{eye[0], eye[1], eye[2]};
    }

    public float[] getTarget() {
        return new float[]{target[0], target[1], target[2]};
    }

    public float[] getUp() {
        return new float[]{up[0], up[1], up[2]};
    }

    public float getFieldOfView() {
        return fieldOfView;
    }

    public float getNearPlane() {
        return nearPlane;
    }

    public float getFarPlane() {
        return farPlane;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public void setNearPlane(float near) {
        this.nearPlane = Math.max(0.001f, near);
        projectionMatrixDirty = true;
    }

    public void setFarPlane(float far) {
        this.farPlane = Math.max(nearPlane + 0.1f, far);
        projectionMatrixDirty = true;
    }
}
