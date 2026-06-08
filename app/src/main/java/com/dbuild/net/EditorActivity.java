package com.dbuild.net;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.dbuild.net.camera.CameraController;
import com.dbuild.net.camera.OrbitCamera;
import com.dbuild.net.engine.RustBridge;
import com.dbuild.net.model.Scene;
import com.dbuild.net.project.ProjectAutoSave;
import com.dbuild.net.project.ProjectManager;
import com.dbuild.net.project.ProjectInfo;
import com.dbuild.net.renderer.GLSurfaceView3D;
import com.dbuild.net.renderer.SceneRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main 3D editor screen for 3D Builder MC.
 * Provides a Blender-like interface with a 3D viewport, block palette,
 * properties panel, and multiple editing modes.
 */
public class EditorActivity extends AppCompatActivity {

    // Intent extra keys
    public static final String EXTRA_PROJECT_ID = "project_id";

    // Auto-save interval in milliseconds
    private static final long AUTO_SAVE_INTERVAL_MS = 60_000L;

    // Editing modes
    private static final int MODE_PLACE = 0;
    private static final int MODE_REMOVE = 1;
    private static final int MODE_SELECT = 2;
    private static final int MODE_PAINT = 3;
    private static final int MODE_UV_EDIT = 4;

    // Camera types
    private static final int CAMERA_ORBIT = 0;
    private static final int CAMERA_FLY = 1;
    private static final int CAMERA_ORTHO = 2;

    // Block types for the palette
    private static final String[] BLOCK_TYPES = {
            "Grass", "Dirt", "Stone", "Cobblestone", "Oak Planks",
            "Spruce Planks", "Birch Planks", "Sand", "Gravel",
            "Gold Ore", "Iron Ore", "Coal Ore", "Oak Log",
            "Spruce Log", "Birch Log", "Oak Leaves", "Spruce Leaves",
            "Glass", "Lapis Ore", "Sandstone", "Wool White",
            "Wool Orange", "Wool Magenta", "Wool Light Blue",
            "Wool Yellow", "Wool Lime", "Wool Pink", "Wool Gray",
            "Brick", "TNT", "Bookshelf", "Obsidian", "Diamond Ore",
            "Redstone Ore", "Snow", "Clay", "Netherrack", "Soul Sand",
            "Glowstone", "Carved Pumpkin", "Melon Block", "Mycelium"
    };

    // Top-level views
    private Toolbar toolbar;
    private GLSurfaceView3D glSurfaceView;
    private DrawerLayout drawerLayout;
    private LinearLayout leftPanel;
    private ScrollView rightPanel;
    private LinearLayout rightPanelContent;
    private LinearLayout bottomBar;

    // Toolbar action buttons
    private View buttonUndo;
    private View buttonRedo;
    private View buttonSave;
    private TextView textProjectName;

    // Left panel - block palette
    private ListView blockPaletteList;
    private BlockPaletteAdapter blockPaletteAdapter;
    private String selectedBlockType = BLOCK_TYPES[0];

    // Right panel - properties
    private TextView textSelectedBlockName;
    private EditText editPosX;
    private EditText editPosY;
    private EditText editPosZ;
    private EditText editScaleX;
    private EditText editScaleY;
    private EditText editScaleZ;
    private EditText editRotX;
    private EditText editRotY;
    private EditText editRotZ;
    private TextView textTextureName;
    private TextView textUVCoords;
    private Spinner spinnerTexture;
    private View buttonEditUV;
    private View buttonBrowseTexture;

    // Bottom bar
    private LinearLayout modeButtonsContainer;
    private TextView textCoordinateDisplay;
    private TextView textModeLabel;
    private View[] modeButtons;
    private int currentMode = MODE_PLACE;
    private int currentCameraType = CAMERA_ORBIT;

    // Core components
    private ProjectManager projectManager;
    private ProjectAutoSave autoSave;
    private SceneRenderer sceneRenderer;
    private CameraController cameraController;
    private Scene scene;
    private RustBridge rustBridge;

    // Project state
    private String projectId;
    private String projectName;
    private boolean hasUnsavedChanges = false;
    private boolean isProjectNew = false;

    // Auto-save handler
    private Handler autoSaveHandler;
    private Runnable autoSaveRunnable;

    // Touch tracking for distinguishing taps from drags
    private PointF touchDownPoint = new PointF();
    private boolean isTouchDragging = false;
    private static final float TAP_THRESHOLD = 10f; // pixels

    // Undo/Redo stacks (simplified; real implementation would use a command pattern)
    private final List<SceneSnapshot> undoStack = new ArrayList<>();
    private final List<SceneSnapshot> redoStack = new ArrayList<>();
    private static final int MAX_UNDO_STACK = 50;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        projectManager = ProjectManager.getInstance(this);
        autoSaveHandler = new Handler(Looper.getMainLooper());

        extractProjectInfo();
        initViews();
        setupToolbar();
        setupLeftPanel();
        setupRightPanel();
        setupBottomBar();
        initRustBridge();
        initGLSurfaceView();
        initCameraController();
        loadProject();
        startAutoSave();

        if (projectName != null) {
            setTitle(projectName);
        }
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    private void extractProjectInfo() {
        projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        if (projectId == null || projectId.isEmpty()) {
            isProjectNew = true;
            ProjectInfo newProject = projectManager.createNewProject();
            projectId = newProject.getId();
            projectName = newProject.getName();
        } else {
            isProjectNew = false;
            ProjectInfo existing = projectManager.getProjectById(projectId);
            if (existing != null) {
                projectName = existing.getName();
            } else {
                projectName = getString(R.string.untitled_project);
            }
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_editor);
        glSurfaceView = findViewById(R.id.gl_surface_view);
        drawerLayout = findViewById(R.id.drawer_layout_editor);
        leftPanel = findViewById(R.id.left_panel);
        rightPanel = findViewById(R.id.right_panel);
        rightPanelContent = findViewById(R.id.right_panel_content);
        bottomBar = findViewById(R.id.bottom_bar);

        buttonUndo = findViewById(R.id.button_undo);
        buttonRedo = findViewById(R.id.button_redo);
        buttonSave = findViewById(R.id.button_save);
        textProjectName = findViewById(R.id.text_project_name);

        blockPaletteList = findViewById(R.id.list_block_palette);

        textSelectedBlockName = findViewById(R.id.text_selected_block_name);
        editPosX = findViewById(R.id.edit_pos_x);
        editPosY = findViewById(R.id.edit_pos_y);
        editPosZ = findViewById(R.id.edit_pos_z);
        editScaleX = findViewById(R.id.edit_scale_x);
        editScaleY = findViewById(R.id.edit_scale_y);
        editScaleZ = findViewById(R.id.edit_scale_z);
        editRotX = findViewById(R.id.edit_rot_x);
        editRotY = findViewById(R.id.edit_rot_y);
        editRotZ = findViewById(R.id.edit_rot_z);
        textTextureName = findViewById(R.id.text_texture_name);
        textUVCoords = findViewById(R.id.text_uv_coords);
        spinnerTexture = findViewById(R.id.spinner_texture);
        buttonEditUV = findViewById(R.id.button_edit_uv);
        buttonBrowseTexture = findViewById(R.id.button_browse_texture);

        modeButtonsContainer = findViewById(R.id.mode_buttons_container);
        textCoordinateDisplay = findViewById(R.id.text_coordinate_display);
        textModeLabel = findViewById(R.id.text_mode_label);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        textProjectName.setText(projectName);

        buttonUndo.setOnClickListener(v -> performUndo());
        buttonRedo.setOnClickListener(v -> performRedo());
        buttonSave.setOnClickListener(v -> saveProject());
    }

    private void setupLeftPanel() {
        blockPaletteAdapter = new BlockPaletteAdapter(this, BLOCK_TYPES);
        blockPaletteList.setAdapter(blockPaletteAdapter);
        blockPaletteList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        blockPaletteList.setItemChecked(0, true);

        blockPaletteList.setOnItemClickListener((parent, view, position, id) -> {
            selectedBlockType = BLOCK_TYPES[position];
            blockPaletteAdapter.setSelectedPosition(position);
        });
    }

    private void setupRightPanel() {
        // Texture spinner setup
        List<String> textureNames = new ArrayList<>();
        textureNames.add("Default");
        textureNames.add("Grass Top");
        textureNames.add("Grass Side");
        textureNames.add("Dirt");
        textureNames.add("Stone");
        textureNames.add("Oak Planks");
        textureNames.add("Sand");
        textureNames.add("Glass");
        textureNames.add("Brick");
        textureNames.add("Snow");
        textureNames.add("Custom...");

        ArrayAdapter<String> textureAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, textureNames);
        textureAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTexture.setAdapter(textureAdapter);

        spinnerTexture.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String texture = textureNames.get(position);
                if (scene != null && scene.hasSelectedBlock()) {
                    scene.setSelectedBlockTexture(texture);
                    textTextureName.setText(texture);
                    markUnsaved();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action required
            }
        });

        // Edit UV button
        buttonEditUV.setOnClickListener(v -> openUVEditor());

        // Browse texture button
        buttonBrowseTexture.setOnClickListener(v -> openTextureLibrary());

        // Property change listeners for position fields
        setupPropertyField(editPosX, "posX");
        setupPropertyField(editPosY, "posY");
        setupPropertyField(editPosZ, "posZ");
        setupPropertyField(editScaleX, "scaleX");
        setupPropertyField(editScaleY, "scaleY");
        setupPropertyField(editScaleZ, "scaleZ");
        setupPropertyField(editRotX, "rotX");
        setupPropertyField(editRotY, "rotY");
        setupPropertyField(editRotZ, "rotZ");

        // Initially hide the right panel details since no block is selected
        updateRightPanelForSelection(false);
    }

    private void setupPropertyField(EditText field, String property) {
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                applyPropertyChange(property, field.getText().toString());
            }
        });

        field.setOnEditorActionListener((v, actionId, event) -> {
            applyPropertyChange(property, field.getText().toString());
            field.clearFocus();
            return true;
        });
    }

    private void applyPropertyChange(String property, String valueStr) {
        if (scene == null || !scene.hasSelectedBlock()) {
            return;
        }

        float value;
        try {
            value = Float.parseFloat(valueStr);
        } catch (NumberFormatException e) {
            return;
        }

        switch (property) {
            case "posX":
                scene.setSelectedBlockPosition(value, null, null);
                break;
            case "posY":
                scene.setSelectedBlockPosition(null, value, null);
                break;
            case "posZ":
                scene.setSelectedBlockPosition(null, null, value);
                break;
            case "scaleX":
                scene.setSelectedBlockScale(value, null, null);
                break;
            case "scaleY":
                scene.setSelectedBlockScale(null, value, null);
                break;
            case "scaleZ":
                scene.setSelectedBlockScale(null, null, value);
                break;
            case "rotX":
                scene.setSelectedBlockRotation(value, null, null);
                break;
            case "rotY":
                scene.setSelectedBlockRotation(null, value, null);
                break;
            case "rotZ":
                scene.setSelectedBlockRotation(null, null, value);
                break;
        }

        markUnsaved();
        pushUndoSnapshot();
    }

    private void setupBottomBar() {
        String[] modeNames = {"Place", "Remove", "Select", "Paint", "UV Edit"};
        int[] modeIcons = {
                R.drawable.ic_mode_place,
                R.drawable.ic_mode_remove,
                R.drawable.ic_mode_select,
                R.drawable.ic_mode_paint,
                R.drawable.ic_mode_uv_edit
        };

        modeButtons = new View[modeNames.length];
        for (int i = 0; i < modeNames.length; i++) {
            TextView modeBtn = new TextView(this);
            modeBtn.setText(modeNames[i]);
            modeBtn.setCompoundDrawablesWithIntrinsicBounds(modeIcons[i], 0, 0, 0);
            modeBtn.setGravity(Gravity.CENTER_VERTICAL);
            modeBtn.setPadding(24, 12, 24, 12);
            modeBtn.setTextSize(12);
            modeBtn.setTextColor(getResources().getColorStateList(R.color.mode_button_text));

            final int mode = i;
            modeBtn.setOnClickListener(v -> setEditMode(mode));

            modeButtons[i] = modeBtn;
            modeButtonsContainer.addView(modeBtn,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Default mode
        setEditMode(MODE_PLACE);

        // Coordinate display
        textCoordinateDisplay.setText("X: 0  Y: 0  Z: 0");
    }

    // -------------------------------------------------------------------------
    // Core engine initialization
    // -------------------------------------------------------------------------

    private void initRustBridge() {
        rustBridge = RustBridge.getInstance();
        rustBridge.initialize(this);
        rustBridge.setLogLevel(RustBridge.LOG_INFO);
    }

    private void initGLSurfaceView() {
        sceneRenderer = new SceneRenderer(this);
        glSurfaceView.setRenderer(sceneRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView3D.RENDERMODE_CONTINUOUS);
        glSurfaceView.setPreserveEGLContextOnPause(true);
    }

    private void initCameraController() {
        cameraController = new OrbitCamera();
        cameraController.setViewport(glSurfaceView);
        cameraController.setSceneRenderer(sceneRenderer);
        cameraController.reset();
    }

    // -------------------------------------------------------------------------
    // Project loading / saving
    // -------------------------------------------------------------------------

    private void loadProject() {
        scene = projectManager.loadScene(projectId);
        if (scene == null) {
            scene = new Scene();
        }

        sceneRenderer.setScene(scene);
        sceneRenderer.setCameraController(cameraController);

        // Set a listener so the UI can react to scene changes
        scene.setOnSceneChangedListener(new Scene.OnSceneChangedListener() {
            @Override
            public void onBlockAdded(String blockId) {
                markUnsaved();
                pushUndoSnapshot();
                refreshRightPanel();
            }

            @Override
            public void onBlockRemoved(String blockId) {
                markUnsaved();
                pushUndoSnapshot();
                refreshRightPanel();
            }

            @Override
            public void onSelectionChanged(@Nullable String blockId) {
                runOnUiThread(() -> refreshRightPanel());
            }

            @Override
            public void onSceneModified() {
                markUnsaved();
            }
        });
    }

    private void saveProject() {
        if (scene == null) {
            return;
        }

        boolean success = projectManager.saveScene(projectId, scene);
        if (success) {
            hasUnsavedChanges = false;
            Toast.makeText(this, R.string.project_saved, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.project_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    // -------------------------------------------------------------------------
    // Auto-save
    // -------------------------------------------------------------------------

    private void startAutoSave() {
        autoSave = new ProjectAutoSave(projectManager, projectId, scene);

        autoSaveRunnable = () -> {
            if (hasUnsavedChanges && scene != null) {
                autoSave.performAutoSave();
            }
            autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS);
        };

        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS);
    }

    private void stopAutoSave() {
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
    }

    // -------------------------------------------------------------------------
    // Edit mode
    // -------------------------------------------------------------------------

    private void setEditMode(int mode) {
        currentMode = mode;

        // Update button highlights
        for (int i = 0; i < modeButtons.length; i++) {
            if (i == mode) {
                modeButtons[i].setBackgroundColor(
                        getResources().getColor(R.color.mode_button_active));
            } else {
                modeButtons[i].setBackgroundColor(
                        getResources().getColor(R.color.mode_button_inactive));
            }
        }

        // Update mode label
        String[] modeLabels = {"Place", "Remove", "Select", "Paint", "UV Edit"};
        textModeLabel.setText(modeLabels[mode]);

        // Configure viewport touch handling based on mode
        if (sceneRenderer != null) {
            sceneRenderer.setEditMode(mode);
        }
    }

    // -------------------------------------------------------------------------
    // Camera mode switching
    // -------------------------------------------------------------------------

    private void switchCameraMode(int cameraType) {
        currentCameraType = cameraType;

        switch (cameraType) {
            case CAMERA_ORBIT:
                cameraController = new OrbitCamera();
                break;
            case CAMERA_FLY:
                cameraController = new CameraController(CameraController.TYPE_FLY);
                break;
            case CAMERA_ORTHO:
                cameraController = new CameraController(CameraController.TYPE_ORTHOGRAPHIC);
                break;
        }

        cameraController.setViewport(glSurfaceView);
        cameraController.setSceneRenderer(sceneRenderer);
        cameraController.reset();

        if (sceneRenderer != null) {
            sceneRenderer.setCameraController(cameraController);
        }

        String[] cameraNames = {"Orbit", "Fly", "Orthographic"};
        Toast.makeText(this,
                getString(R.string.camera_switched, cameraNames[cameraType]),
                Toast.LENGTH_SHORT).show();
    }

    // -------------------------------------------------------------------------
    // Touch handling for block placement / removal / selection
    // -------------------------------------------------------------------------

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Let the drawer and panels handle their own touch events first
        if (isTouchOnPanel(ev)) {
            return super.dispatchTouchEvent(ev);
        }

        // Pass touch events to the camera controller for camera manipulation
        if (cameraController != null) {
            cameraController.handleTouchEvent(ev);
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownPoint.set(ev.getX(), ev.getY());
                isTouchDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - touchDownPoint.x;
                float dy = ev.getY() - touchDownPoint.y;
                if (Math.abs(dx) > TAP_THRESHOLD || Math.abs(dy) > TAP_THRESHOLD) {
                    isTouchDragging = true;
                }
                updateCoordinateDisplay(ev.getX(), ev.getY());
                break;

            case MotionEvent.ACTION_UP:
                if (!isTouchDragging) {
                    handleViewportTap(ev.getX(), ev.getY());
                }
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    private boolean isTouchOnPanel(MotionEvent ev) {
        int[] leftLocation = new int[2];
        int[] rightLocation = new int[2];
        leftPanel.getLocationOnScreen(leftLocation);
        rightPanel.getLocationOnScreen(rightLocation);

        float x = ev.getRawX();
        float y = ev.getRawY();

        // Check if touch is on left panel
        if (x >= leftLocation[0] && x <= leftLocation[0] + leftPanel.getWidth()
                && y >= leftLocation[1] && y <= leftLocation[1] + leftPanel.getHeight()) {
            return true;
        }

        // Check if touch is on right panel
        if (x >= rightLocation[0] && x <= rightLocation[0] + rightPanel.getWidth()
                && y >= rightLocation[1] && y <= rightLocation[1] + rightPanel.getHeight()) {
            return true;
        }

        return false;
    }

    /**
     * Handles a tap on the 3D viewport by raycasting from the touch position
     * and performing the action corresponding to the current edit mode.
     */
    private void handleViewportTap(float screenX, float screenY) {
        if (scene == null || sceneRenderer == null) {
            return;
        }

        // Convert screen coordinates to normalized device coordinates
        int viewportWidth = glSurfaceView.getWidth();
        int viewportHeight = glSurfaceView.getHeight();
        float ndcX = (2.0f * screenX) / viewportWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * screenY) / viewportHeight;

        // Use the Rust engine for raycasting
        float[] rayOrigin = cameraController.getRayOrigin(ndcX, ndcY);
        float[] rayDirection = cameraController.getRayDirection(ndcX, ndcY);

        String hitBlockId = rustBridge.raycast(scene, rayOrigin, rayDirection);
        float[] hitPoint = rustBridge.getLastRaycastHitPoint();
        float[] hitNormal = rustBridge.getLastRaycastHitNormal();

        switch (currentMode) {
            case MODE_PLACE:
                handlePlaceMode(hitBlockId, hitPoint, hitNormal);
                break;
            case MODE_REMOVE:
                handleRemoveMode(hitBlockId);
                break;
            case MODE_SELECT:
                handleSelectMode(hitBlockId);
                break;
            case MODE_PAINT:
                handlePaintMode(hitBlockId);
                break;
            case MODE_UV_EDIT:
                handleUVEditMode(hitBlockId, hitPoint, hitNormal);
                break;
        }
    }

    private void handlePlaceMode(String hitBlockId, float[] hitPoint, float[] hitNormal) {
        if (hitPoint != null && hitNormal != null) {
            // Place a new block adjacent to the hit face
            float newPosX = hitPoint[0] + hitNormal[0];
            float newPosY = hitPoint[1] + hitNormal[1];
            float newPosZ = hitPoint[2] + hitNormal[2];

            // Snap to grid (1-unit grid)
            int gridX = Math.round(newPosX);
            int gridY = Math.round(newPosY);
            int gridZ = Math.round(newPosZ);

            String newBlockId = scene.addBlock(selectedBlockType, gridX, gridY, gridZ);
            if (newBlockId == null) {
                Toast.makeText(this, R.string.block_already_exists, Toast.LENGTH_SHORT).show();
            }
        } else {
            // No hit — place at origin or at camera look-at point
            float[] lookAt = cameraController.getLookAtPoint();
            int gridX = Math.round(lookAt[0]);
            int gridY = Math.round(lookAt[1]);
            int gridZ = Math.round(lookAt[2]);

            scene.addBlock(selectedBlockType, gridX, gridY, gridZ);
        }
    }

    private void handleRemoveMode(String hitBlockId) {
        if (hitBlockId != null) {
            scene.removeBlock(hitBlockId);
        }
    }

    private void handleSelectMode(String hitBlockId) {
        if (hitBlockId != null) {
            scene.selectBlock(hitBlockId);
        } else {
            scene.clearSelection();
        }
    }

    private void handlePaintMode(String hitBlockId) {
        if (hitBlockId != null) {
            String textureName = (String) spinnerTexture.getSelectedItem();
            scene.setBlockTexture(hitBlockId, textureName);
            markUnsaved();
        }
    }

    private void handleUVEditMode(String hitBlockId, float[] hitPoint, float[] hitNormal) {
        if (hitBlockId != null) {
            scene.selectBlock(hitBlockId);
            openUVEditor();
        }
    }

    private void updateCoordinateDisplay(float screenX, float screenY) {
        if (sceneRenderer == null || cameraController == null) {
            return;
        }

        int viewportWidth = glSurfaceView.getWidth();
        int viewportHeight = glSurfaceView.getHeight();
        float ndcX = (2.0f * screenX) / viewportWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * screenY) / viewportHeight;

        float[] rayOrigin = cameraController.getRayOrigin(ndcX, ndcY);
        float[] rayDirection = cameraController.getRayDirection(ndcX, ndcY);
        float[] groundHit = rustBridge.raycastGroundPlane(rayOrigin, rayDirection);

        if (groundHit != null) {
            int gx = Math.round(groundHit[0]);
            int gy = Math.round(groundHit[1]);
            int gz = Math.round(groundHit[2]);
            textCoordinateDisplay.setText(
                    String.format(Locale.US, "X: %d  Y: %d  Z: %d", gx, gy, gz));
        }
    }

    // -------------------------------------------------------------------------
    // Right panel refresh
    // -------------------------------------------------------------------------

    private void refreshRightPanel() {
        if (scene == null) {
            updateRightPanelForSelection(false);
            return;
        }

        boolean hasSelection = scene.hasSelectedBlock();
        updateRightPanelForSelection(hasSelection);

        if (hasSelection) {
            textSelectedBlockName.setText(scene.getSelectedBlockName());

            float[] pos = scene.getSelectedBlockPosition();
            if (pos != null) {
                editPosX.setText(String.format(Locale.US, "%.1f", pos[0]));
                editPosY.setText(String.format(Locale.US, "%.1f", pos[1]));
                editPosZ.setText(String.format(Locale.US, "%.1f", pos[2]));
            }

            float[] scale = scene.getSelectedBlockScale();
            if (scale != null) {
                editScaleX.setText(String.format(Locale.US, "%.2f", scale[0]));
                editScaleY.setText(String.format(Locale.US, "%.2f", scale[1]));
                editScaleZ.setText(String.format(Locale.US, "%.2f", scale[2]));
            }

            float[] rot = scene.getSelectedBlockRotation();
            if (rot != null) {
                editRotX.setText(String.format(Locale.US, "%.1f", rot[0]));
                editRotY.setText(String.format(Locale.US, "%.1f", rot[1]));
                editRotZ.setText(String.format(Locale.US, "%.1f", rot[2]));
            }

            String texture = scene.getSelectedBlockTexture();
            textTextureName.setText(texture != null ? texture : "Default");

            float[] uv = scene.getSelectedBlockUV();
            if (uv != null) {
                textUVCoords.setText(String.format(Locale.US,
                        "U: %.2f, %.2f  V: %.2f, %.2f", uv[0], uv[1], uv[2], uv[3]));
            } else {
                textUVCoords.setText("U: 0, 1  V: 0, 1");
            }
        }
    }

    private void updateRightPanelForSelection(boolean hasSelection) {
        if (rightPanelContent != null) {
            for (int i = 0; i < rightPanelContent.getChildCount(); i++) {
                View child = rightPanelContent.getChildAt(i);
                // Keep the "No Selection" text visible when nothing is selected,
                // hide all other detail views
                if (child.getTag() != null && child.getTag().equals("no_selection_label")) {
                    child.setVisibility(hasSelection ? View.GONE : View.VISIBLE);
                } else if (child.getTag() != null && child.getTag().equals("selection_details")) {
                    child.setVisibility(hasSelection ? View.VISIBLE : View.GONE);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Undo / Redo
    // -------------------------------------------------------------------------

    private void pushUndoSnapshot() {
        if (scene == null) {
            return;
        }

        SceneSnapshot snapshot = scene.createSnapshot();
        undoStack.add(snapshot);
        if (undoStack.size() > MAX_UNDO_STACK) {
            undoStack.remove(0);
        }
        redoStack.clear();

        updateUndoRedoButtons();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_undo, Toast.LENGTH_SHORT).show();
            return;
        }

        // Save current state to redo stack
        SceneSnapshot current = scene.createSnapshot();
        redoStack.add(current);

        // Restore previous state
        SceneSnapshot previous = undoStack.remove(undoStack.size() - 1);
        scene.restoreFromSnapshot(previous);

        markUnsaved();
        refreshRightPanel();
        updateUndoRedoButtons();
    }

    private void performRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_redo, Toast.LENGTH_SHORT).show();
            return;
        }

        // Save current state to undo stack
        SceneSnapshot current = scene.createSnapshot();
        undoStack.add(current);

        // Restore redo state
        SceneSnapshot next = redoStack.remove(redoStack.size() - 1);
        scene.restoreFromSnapshot(next);

        markUnsaved();
        refreshRightPanel();
        updateUndoRedoButtons();
    }

    private void updateUndoRedoButtons() {
        buttonUndo.setAlpha(undoStack.isEmpty() ? 0.3f : 1.0f);
        buttonUndo.setEnabled(!undoStack.isEmpty());
        buttonRedo.setAlpha(redoStack.isEmpty() ? 0.3f : 1.0f);
        buttonRedo.setEnabled(!redoStack.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Unsaved changes tracking
    // -------------------------------------------------------------------------

    private void markUnsaved() {
        hasUnsavedChanges = true;
    }

    // -------------------------------------------------------------------------
    // Menu
    // -------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_editor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save) {
            saveProject();
            return true;
        } else if (id == R.id.action_export) {
            openExportActivity();
            return true;
        } else if (id == R.id.action_camera_type) {
            showCameraTypeMenu();
            return true;
        } else if (id == R.id.action_undo) {
            performUndo();
            return true;
        } else if (id == R.id.action_redo) {
            performRedo();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showCameraTypeMenu() {
        View anchor = toolbar.findViewById(R.id.action_camera_type);
        if (anchor == null) {
            anchor = toolbar;
        }

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_camera_type, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.camera_orbit) {
                switchCameraMode(CAMERA_ORBIT);
                return true;
            } else if (id == R.id.camera_fly) {
                switchCameraMode(CAMERA_FLY);
                return true;
            } else if (id == R.id.camera_ortho) {
                switchCameraMode(CAMERA_ORTHO);
                return true;
            }
            return false;
        });

        popup.show();
    }

    // -------------------------------------------------------------------------
    // Navigation to other activities
    // -------------------------------------------------------------------------

    private void openUVEditor() {
        if (scene == null || !scene.hasSelectedBlock()) {
            Toast.makeText(this, R.string.select_block_first, Toast.LENGTH_SHORT).show();
            return;
        }

        String blockId = scene.getSelectedBlockId();
        Intent intent = new Intent(this, UVEditorActivity.class);
        intent.putExtra(UVEditorActivity.EXTRA_PROJECT_ID, projectId);
        intent.putExtra(UVEditorActivity.EXTRA_BLOCK_ID, blockId);
        startActivity(intent);
    }

    private void openTextureLibrary() {
        if (scene == null || !scene.hasSelectedBlock()) {
            Toast.makeText(this, R.string.select_block_first, Toast.LENGTH_SHORT).show();
            return;
        }

        String blockId = scene.getSelectedBlockId();
        Intent intent = new Intent(this, TextureLibraryActivity.class);
        intent.putExtra(TextureLibraryActivity.EXTRA_PROJECT_ID, projectId);
        intent.putExtra(TextureLibraryActivity.EXTRA_BLOCK_ID, blockId);
        startActivity(intent);
    }

    private void openExportActivity() {
        if (projectId == null) {
            Toast.makeText(this, R.string.no_project_to_export, Toast.LENGTH_SHORT).show();
            return;
        }

        // Save before exporting
        if (hasUnsavedChanges) {
            saveProject();
        }

        Intent intent = new Intent(this, ExportActivity.class);
        intent.putExtra(ExportActivity.EXTRA_PROJECT_ID, projectId);
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Back press handling
    // -------------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        if (hasUnsavedChanges) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.unsaved_changes)
                    .setMessage(R.string.unsaved_changes_message)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        saveProject();
                        finish();
                    })
                    .setNegativeButton(R.string.discard, (dialog, which) -> {
                        // Discard auto-save recovery point
                        if (autoSave != null) {
                            autoSave.clearAutoSave();
                        }
                        finish();
                    })
                    .setNeutralButton(R.string.cancel, null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
        refreshRightPanel();
    }

    @Override
    protected void onDestroy() {
        stopAutoSave();

        if (rustBridge != null) {
            rustBridge.cleanup();
        }

        if (scene != null) {
            scene.setOnSceneChangedListener(null);
        }

        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Inner class: lightweight scene snapshot for undo/redo
    // -------------------------------------------------------------------------

    /**
     * Represents a lightweight snapshot of the scene state used
     * for undo and redo operations. Captures serialized scene data
     * via the Rust bridge for efficiency.
     */
    private static class SceneSnapshot {
        private final byte[] serializedData;
        private final long timestamp;

        SceneSnapshot(byte[] serializedData) {
            this.serializedData = serializedData;
            this.timestamp = System.currentTimeMillis();
        }

        byte[] getSerializedData() {
            return serializedData;
        }

        long getTimestamp() {
            return timestamp;
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: block palette adapter
    // -------------------------------------------------------------------------

    /**
     * Adapter for the block palette list in the left panel.
     * Displays block names with colored indicators.
     */
    private static class BlockPaletteAdapter extends ArrayAdapter<String> {

        private int selectedPosition = 0;

        BlockPaletteAdapter(EditorActivity context, String[] blocks) {
            super(context, R.layout.item_block_palette, blocks);
        }

        void setSelectedPosition(int position) {
            selectedPosition = position;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = View.inflate(getContext(), R.layout.item_block_palette, null);
            }

            TextView textBlockName = view.findViewById(R.id.text_block_name);
            View indicatorSelected = view.findViewById(R.id.indicator_selected);

            textBlockName.setText(getItem(position));

            if (position == selectedPosition) {
                indicatorSelected.setVisibility(View.VISIBLE);
                view.setBackgroundColor(
                        getContext().getResources().getColor(R.color.block_palette_selected));
            } else {
                indicatorSelected.setVisibility(View.GONE);
                view.setBackgroundColor(
                        getContext().getResources().getColor(R.color.block_palette_normal));
            }

            return view;
        }
    }
}
