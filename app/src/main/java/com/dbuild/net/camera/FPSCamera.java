package com.dbuild.net.camera;

import android.view.MotionEvent;

/**
 * First-person style camera controller.
 * Uses yaw and pitch for orientation, with WASD-like movement support.
 * Touch drag controls look direction; additional gestures handle movement.
 */
public class FPSCamera extends CameraController {

    private float yaw = 0.0f;
    private float pitch = 0.0f;
    private float moveSpeed = 5.0f;
    private float lookSpeed = 0.2f;

    // Key states: index 0=W, 1=A, 2=S, 3=D, 4=Q(up), 5=E(down)
    private boolean[] keyStates = new boolean[6];

    private int activePointerId = -1;
    private float lastTouchX;
    private float lastTouchY;
    private boolean isLooking = false;

    // Movement gesture support
    private int movePointerId = -1;
    private float moveStartX;
    private float moveStartY;
    private static final float MOVE_GESTURE_THRESHOLD = 50.0f;

    public FPSCamera() {
        super();
        eye[0] = 0.0f;
        eye[1] = 5.0f;
        eye[2] = 10.0f;
        target[0] = 0.0f;
        target[1] = 5.0f;
        target[2] = 9.0f;
        recalculateTarget();
    }

    /**
     * Recalculates the target position based on yaw, pitch, and eye position.
     */
    private void recalculateTarget() {
        float[] forward = getForwardVector();
        target[0] = eye[0] + forward[0];
        target[1] = eye[1] + forward[1];
        target[2] = eye[2] + forward[2];
        markDirty();
    }

    /**
     * Computes the forward direction vector from yaw and pitch.
     * @return float[3] normalized forward vector
     */
    public float[] getForwardVector() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        float x = (float) (Math.cos(pitchRad) * Math.sin(yawRad));
        float y = (float) Math.sin(pitchRad);
        float z = (float) (-Math.cos(pitchRad) * Math.cos(yawRad));

        return new float[]{x, y, z};
    }

    /**
     * Computes the right direction vector (perpendicular to forward and up).
     * @return float[3] normalized right vector
     */
    public float[] getRightVector() {
        float[] forward = getForwardVector();
        // Right = forward x up (0,1,0)
        float rx = forward[1] * up[2] - forward[2] * up[1];
        float ry = forward[2] * up[0] - forward[0] * up[2];
        float rz = forward[0] * up[1] - forward[1] * up[0];

        float len = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (len > 0.0001f) {
            rx /= len;
            ry /= len;
            rz /= len;
        }

        return new float[]{rx, ry, rz};
    }

    @Override
    public void onUpdate(float deltaTime) {
        float[] forward = getForwardVector();
        float[] right = getRightVector();

        float moveX = 0.0f;
        float moveY = 0.0f;
        float moveZ = 0.0f;

        // W - move forward
        if (keyStates[0]) {
            moveX += forward[0];
            moveY += forward[1];
            moveZ += forward[2];
        }
        // A - move left
        if (keyStates[1]) {
            moveX -= right[0];
            moveY -= right[1];
            moveZ -= right[2];
        }
        // S - move backward
        if (keyStates[2]) {
            moveX -= forward[0];
            moveY -= forward[1];
            moveZ -= forward[2];
        }
        // D - move right
        if (keyStates[3]) {
            moveX += right[0];
            moveY += right[1];
            moveZ += right[2];
        }
        // Q - move up
        if (keyStates[4]) {
            moveY += 1.0f;
        }
        // E - move down
        if (keyStates[5]) {
            moveY -= 1.0f;
        }

        // Normalize horizontal movement to prevent speed boost at angles
        float horizontalLen = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (horizontalLen > 1.0f) {
            moveX /= horizontalLen;
            moveZ /= horizontalLen;
        }

        float speed = moveSpeed * deltaTime;
        eye[0] += moveX * speed;
        eye[1] += moveY * speed;
        eye[2] += moveZ * speed;

        // Also handle touch-based movement from virtual joystick
        if (movePointerId >= 0) {
            float dx = 0;
            float dy = 0;
            // Use stored move deltas
            float[] fwd = getForwardVector();
            float[] rgt = getRightVector();
            // Forward/backward movement
            eye[0] += fwd[0] * moveStartY * speed * 0.1f;
            eye[1] += fwd[1] * moveStartY * speed * 0.1f;
            eye[2] += fwd[2] * moveStartY * speed * 0.1f;
            // Left/right movement
            eye[0] += rgt[0] * moveStartX * speed * 0.1f;
            eye[2] += rgt[2] * moveStartX * speed * 0.1f;
        }

        recalculateTarget();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(event.getActionIndex());
                lastTouchX = event.getX(event.getActionIndex());
                lastTouchY = event.getY(event.getActionIndex());
                isLooking = true;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Second finger for movement
                if (event.getPointerCount() == 2) {
                    int moveIndex = event.getActionIndex();
                    movePointerId = event.getPointerId(moveIndex);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isLooking && activePointerId >= 0) {
                    int lookIndex = event.findPointerIndex(activePointerId);
                    if (lookIndex >= 0) {
                        float x = event.getX(lookIndex);
                        float y = event.getY(lookIndex);

                        float dx = x - lastTouchX;
                        float dy = y - lastTouchY;

                        yaw += dx * lookSpeed;
                        pitch -= dy * lookSpeed;
                        pitch = Math.max(-89.0f, Math.min(89.0f, pitch));

                        // Keep yaw in range
                        yaw = yaw % 360.0f;
                        if (yaw < 0) yaw += 360.0f;

                        lastTouchX = x;
                        lastTouchY = y;

                        recalculateTarget();
                    }
                }

                // Handle movement from second pointer
                if (movePointerId >= 0) {
                    int moveIdx = event.findPointerIndex(movePointerId);
                    if (moveIdx >= 0) {
                        float mx = event.getX(moveIdx);
                        float my = event.getY(moveIdx);
                        moveStartX = (mx - viewportWidth * 0.5f) / (viewportWidth * 0.25f);
                        moveStartY = (viewportHeight * 0.5f - my) / (viewportHeight * 0.25f);
                        moveStartX = Math.max(-1, Math.min(1, moveStartX));
                        moveStartY = Math.max(-1, Math.min(1, moveStartY));
                    }
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                int pointerId = event.getPointerId(event.getActionIndex());
                if (pointerId == movePointerId) {
                    movePointerId = -1;
                    moveStartX = 0;
                    moveStartY = 0;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = -1;
                isLooking = false;
                movePointerId = -1;
                moveStartX = 0;
                moveStartY = 0;
                return true;
        }

        return false;
    }

    @Override
    public String getCameraType() {
        return "FPS";
    }

    @Override
    public void reset() {
        eye[0] = 0.0f;
        eye[1] = 5.0f;
        eye[2] = 10.0f;
        yaw = 0.0f;
        pitch = 0.0f;
        up[0] = 0.0f;
        up[1] = 1.0f;
        up[2] = 0.0f;
        fieldOfView = 45.0f;
        for (int i = 0; i < keyStates.length; i++) {
            keyStates[i] = false;
        }
        recalculateTarget();
    }

    /**
     * Moves the camera forward by the given distance.
     */
    public void moveForward(float distance) {
        float[] forward = getForwardVector();
        eye[0] += forward[0] * distance;
        eye[1] += forward[1] * distance;
        eye[2] += forward[2] * distance;
        recalculateTarget();
    }

    /**
     * Moves the camera right by the given distance.
     */
    public void moveRight(float distance) {
        float[] right = getRightVector();
        eye[0] += right[0] * distance;
        eye[1] += right[1] * distance;
        eye[2] += right[2] * distance;
        recalculateTarget();
    }

    /**
     * Moves the camera up by the given distance.
     */
    public void moveUp(float distance) {
        eye[1] += distance;
        recalculateTarget();
    }

    public void setYaw(float degrees) {
        this.yaw = degrees % 360.0f;
        if (this.yaw < 0) this.yaw += 360.0f;
        recalculateTarget();
    }

    public void setPitch(float degrees) {
        this.pitch = Math.max(-89.0f, Math.min(89.0f, degrees));
        recalculateTarget();
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getMoveSpeed() {
        return moveSpeed;
    }

    public void setMoveSpeed(float speed) {
        this.moveSpeed = Math.max(0.1f, speed);
    }

    public float getLookSpeed() {
        return lookSpeed;
    }

    public void setLookSpeed(float speed) {
        this.lookSpeed = Math.max(0.01f, speed);
    }

    /**
     * Sets the state of a movement key.
     * @param index 0=W, 1=A, 2=S, 3=D, 4=Q(up), 5=E(down)
     * @param pressed true if the key is pressed
     */
    public void setKeyState(int index, boolean pressed) {
        if (index >= 0 && index < keyStates.length) {
            keyStates[index] = pressed;
        }
    }

    public boolean getKeyState(int index) {
        if (index >= 0 && index < keyStates.length) {
            return keyStates[index];
        }
        return false;
    }
}
