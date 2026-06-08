package com.dbuild.net.renderer;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.dbuild.net.camera.CameraController;
import com.dbuild.net.model.Scene;

/**
 * Custom GLSurfaceView for 3D scene rendering with integrated gesture handling.
 * Supports pinch-to-zoom, tap-to-select, and long-press interactions.
 * Delegates rendering to SceneRenderer and camera control to CameraController.
 */
public class GLSurfaceView3D extends GLSurfaceView {

    private SceneRenderer renderer;
    private CameraController camera;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnBlockTapListener blockTapListener;
    private boolean gridVisible = true;

    /**
     * Callback interface for block tap events in the 3D view.
     */
    public interface OnBlockTapListener {
        /**
         * Called when the user taps on a block in the 3D scene.
         *
         * @param x grid x coordinate of the tapped block
         * @param y grid y coordinate of the tapped block
         * @param z grid z coordinate of the tapped block
         */
        void onBlockTapped(int x, int y, int z);

        /**
         * Called when the user taps on empty space in the 3D scene.
         *
         * @param worldPos float[3] world position of the tap intersection
         *                 with the ground plane, or null if no intersection
         */
        void onEmptySpaceTapped(float[] worldPos);
    }

    /**
     * Creates a new GLSurfaceView3D.
     *
     * @param context activity context
     */
    public GLSurfaceView3D(Context context) {
        super(context);
        init();
    }

    /**
     * Initializes the GL surface view with OpenGL ES 2.0 context.
     */
    private void init() {
        // Request OpenGL ES 2.0 context
        setEGLContextClientVersion(2);

        // Set depth buffer to 16 bits for proper depth testing
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        // Create and set the renderer
        renderer = new SceneRenderer();
        setRenderer(renderer);

        // Start in DIRTY mode — only render when something changes
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        // Initialize gesture detectors
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());

        // Enable long press detection
        gestureDetector.setIsLongpressEnabled(true);
    }

    /**
     * Sets the scene to be rendered.
     *
     * @param scene the scene to render
     */
    public void setScene(Scene scene) {
        if (renderer != null) {
            renderer.setScene(scene);
        }
        requestRender();
    }

    /**
     * Sets the camera controller for the view.
     *
     * @param camera the camera controller
     */
    public void setCamera(CameraController camera) {
        this.camera = camera;
        if (renderer != null) {
            renderer.setCamera(camera);
        }
    }

    /**
     * Gets the scene renderer.
     *
     * @return the SceneRenderer
     */
    public SceneRenderer getRenderer() {
        return renderer;
    }

    /**
     * Gets the camera controller.
     *
     * @return the CameraController
     */
    public CameraController getCamera() {
        return camera;
    }

    /**
     * Sets the visibility of the ground grid.
     *
     * @param visible true to show the grid, false to hide
     */
    public void setGridVisible(boolean visible) {
        this.gridVisible = visible;
        if (renderer != null) {
            renderer.setGridVisible(visible);
        }
        requestRender();
    }

    /**
     * Sets the listener for block tap events.
     *
     * @param listener the listener
     */
    public void setOnBlockTapListener(OnBlockTapListener listener) {
        this.blockTapListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Let both gesture detectors process the event
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean gestureHandled = gestureDetector.onTouchEvent(event);

        // Pass touch events to camera controller for orbit/pan/rotate
        if (camera != null && !scaleDetector.isInProgress()) {
            boolean cameraHandled = camera.onTouchEvent(event);
            if (cameraHandled) {
                // Switch to continuous rendering while camera is moving
                setRenderMode(RENDERMODE_CONTINUOUSLY);
                requestRender();
                return true;
            }
        }

        if (scaleHandled || gestureHandled) {
            return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * Notifies the view that camera movement has stopped,
     * allowing it to switch back to DIRTY rendering mode.
     */
    public void onCameraStopped() {
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    /**
     * Scale gesture listener for pinch-to-zoom.
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (camera != null) {
                float scaleFactor = detector.getScaleFactor();
                camera.onZoom(scaleFactor);
                requestRender();
                return true;
            }
            return false;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            // Switch back to dirty rendering after zoom ends
            onCameraStopped();
        }
    }

    /**
     * Gesture listener for tap and long press detection.
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            handleTap(e.getX(), e.getY());
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            handleTap(e.getX(), e.getY());
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Double tap could toggle block placement mode
            handleTap(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            // Must return true to receive subsequent events
            return true;
        }
    }

    /**
     * Handles a tap at the given screen coordinates by performing
     * ray casting to determine what was tapped.
     *
     * @param screenX screen x coordinate
     * @param screenY screen y coordinate
     */
    private void handleTap(float screenX, float screenY) {
        if (renderer == null || blockTapListener == null) return;

        int[] blockPos = renderer.getBlockAtScreenPos(screenX, screenY);
        if (blockPos != null) {
            // Tapped on a block
            blockTapListener.onBlockTapped(blockPos[0], blockPos[1], blockPos[2]);
        } else {
            // Tapped on empty space — check for ground plane intersection
            float[] worldPos = renderer.getWorldPosAtScreenPos(screenX, screenY);
            blockTapListener.onEmptySpaceTapped(worldPos);
        }
    }
}
