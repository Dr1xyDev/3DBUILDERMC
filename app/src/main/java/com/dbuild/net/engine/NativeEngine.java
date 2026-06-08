package com.dbuild.net.engine;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level engine wrapper that manages the native Rust engine lifecycle.
 * Uses RustBridge for all native calls and provides error handling
 * for native crashes.
 */
public class NativeEngine {

    private static final String TAG = "NativeEngine";

    private long enginePtr = 0;
    private boolean initialized = false;
    private final RustBridge rustBridge;
    private final List<NativeModel> activeModels;

    /**
     * Creates a new NativeEngine instance.
     * The engine is not initialized until initialize() is called.
     */
    public NativeEngine() {
        rustBridge = new RustBridge();
        activeModels = new ArrayList<>();
    }

    /**
     * Initializes the native engine.
     * Must be called before creating models.
     * @return true if initialization succeeded
     */
    public boolean initialize() {
        if (initialized) {
            Log.w(TAG, "Engine already initialized");
            return true;
        }

        try {
            enginePtr = rustBridge.createEngine();
            if (enginePtr == 0) {
                Log.e(TAG, "Failed to create native engine (returned null pointer)");
                return false;
            }
            initialized = true;
            Log.i(TAG, "Native engine initialized successfully (ptr=" + enginePtr + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Exception during engine initialization", e);
            enginePtr = 0;
            initialized = false;
            return false;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error during engine initialization", e);
            enginePtr = 0;
            initialized = false;
            return false;
        }
    }

    /**
     * Destroys the native engine and all associated models.
     * After calling this, the engine cannot be reused.
     */
    public void destroy() {
        if (!initialized) return;

        // Destroy all active models first
        for (NativeModel model : activeModels) {
            try {
                model.destroy();
            } catch (Exception e) {
                Log.w(TAG, "Error destroying model during engine shutdown", e);
            }
        }
        activeModels.clear();

        try {
            if (enginePtr != 0) {
                rustBridge.destroyEngine(enginePtr);
                Log.i(TAG, "Native engine destroyed (ptr=" + enginePtr + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during engine destruction", e);
        }

        enginePtr = 0;
        initialized = false;
    }

    /**
     * Creates a new native model managed by this engine.
     * @return A new NativeModel instance, or null if creation failed
     */
    public NativeModel createModel() {
        if (!initialized || enginePtr == 0) {
            Log.e(TAG, "Cannot create model: engine not initialized");
            return null;
        }

        try {
            long modelPtr = rustBridge.createModel(enginePtr);
            if (modelPtr == 0) {
                Log.e(TAG, "Failed to create native model (returned null pointer)");
                return null;
            }

            NativeModel model = new NativeModel(modelPtr, rustBridge, this);
            activeModels.add(model);
            Log.d(TAG, "Created native model (ptr=" + modelPtr + "), total models: " + activeModels.size());
            return model;
        } catch (Exception e) {
            Log.e(TAG, "Exception creating native model", e);
            return null;
        }
    }

    /**
     * Returns whether the engine is initialized and ready to use.
     */
    public boolean isInitialized() {
        return initialized && enginePtr != 0;
    }

    /**
     * Returns the native engine pointer.
     */
    public long getEnginePtr() {
        return enginePtr;
    }

    /**
     * Returns the RustBridge instance used by this engine.
     */
    public RustBridge getRustBridge() {
        return rustBridge;
    }

    /**
     * Returns whether the native Rust library is available.
     */
    public boolean isNativeAvailable() {
        return RustBridge.isNativeAvailable();
    }

    /**
     * Returns the number of active models managed by this engine.
     */
    public int getActiveModelCount() {
        return activeModels.size();
    }

    /**
     * Returns a list of active models (copies to prevent external modification).
     */
    public List<NativeModel> getActiveModels() {
        return new ArrayList<>(activeModels);
    }

    /**
     * Removes a model from the active models list.
     * Called internally by NativeModel.destroy().
     */
    void removeModel(NativeModel model) {
        activeModels.remove(model);
    }

    /**
     * Performs a complete cleanup: destroys all models and the engine.
     * Equivalent to calling destroy().
     */
    public void cleanup() {
        destroy();
    }

    /**
     * Checks the health of the native engine.
     * @return true if the engine is responsive
     */
    public boolean healthCheck() {
        if (!initialized || enginePtr == 0) return false;

        try {
            // Attempt a simple operation to verify the engine is responsive
            // Creating and immediately destroying a model is a reasonable health check
            long testModelPtr = rustBridge.createModel(enginePtr);
            if (testModelPtr == 0) return false;
            int blockCount = rustBridge.getBlockCount(testModelPtr);
            rustBridge.destroyModel(testModelPtr);
            return blockCount == 0; // A fresh model should have 0 blocks
        } catch (Exception e) {
            Log.e(TAG, "Engine health check failed", e);
            return false;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (initialized) {
                Log.w(TAG, "NativeEngine finalized without explicit destroy() call");
                destroy();
            }
        } finally {
            super.finalize();
        }
    }
}
