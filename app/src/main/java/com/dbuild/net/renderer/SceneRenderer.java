package com.dbuild.net.renderer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.dbuild.net.camera.CameraController;
import com.dbuild.net.model.Block3D;
import com.dbuild.net.model.Face;
import com.dbuild.net.model.Scene;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ES 2.0 renderer for the 3D scene.
 * Renders blocks with per-face textures and colors, a ground grid,
 * and handles camera matrix setup, shader compilation, and ray casting for block picking.
 */
public class SceneRenderer implements GLSurfaceView.Renderer {

    // --- Shader source strings ---

    private static final String VERTEX_SHADER_SOURCE =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uModelMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aColor;\n" +
            "attribute vec2 aTexCoord;\n" +
            "attribute vec3 aNormal;\n" +
            "varying vec4 vColor;\n" +
            "varying vec2 vTexCoord;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vWorldPos;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 worldPos = uModelMatrix * aPosition;\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vColor = aColor;\n" +
            "    vTexCoord = aTexCoord;\n" +
            "    vNormal = mat3(uModelMatrix) * aNormal;\n" +
            "    vWorldPos = worldPos.xyz;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_SOURCE =
            "precision mediump float;\n" +
            "varying vec4 vColor;\n" +
            "varying vec2 vTexCoord;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vWorldPos;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform bool uUseTexture;\n" +
            "uniform vec3 uLightDir;\n" +
            "uniform float uAmbientStrength;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 baseColor;\n" +
            "    if (uUseTexture) {\n" +
            "        baseColor = texture2D(uTexture, vTexCoord);\n" +
            "        baseColor.rgb *= vColor.rgb;\n" +
            "        baseColor.a *= vColor.a;\n" +
            "    } else {\n" +
            "        baseColor = vColor;\n" +
            "    }\n" +
            "    vec3 normal = normalize(vNormal);\n" +
            "    float diff = max(dot(normal, normalize(uLightDir)), 0.0);\n" +
            "    float ambient = uAmbientStrength;\n" +
            "    float light = ambient + diff * (1.0 - ambient);\n" +
            "    gl_FragColor = vec4(baseColor.rgb * light, baseColor.a);\n" +
            "}\n";

    // --- GL handles ---

    private int program;
    private int mvpMatrixHandle;
    private int modelMatrixHandle;
    private int positionHandle;
    private int colorHandle;
    private int texCoordHandle;
    private int normalHandle;
    private int textureHandle;
    private int useTextureHandle;
    private int lightDirHandle;
    private int ambientStrengthHandle;

    // --- Matrices ---

    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private float[] mvpMatrix = new float[16];
    private float[] modelMatrix = new float[16];

    // --- Scene data ---

    private Scene scene;
    private CameraController camera;
    private boolean gridVisible = true;
    private GridRenderer gridRenderer;

    // --- Buffers for block rendering ---

    private FloatBuffer vertexBuffer;
    private FloatBuffer colorBuffer;
    private FloatBuffer uvBuffer;
    private FloatBuffer normalBuffer;
    private ShortBuffer indexBuffer;
    private boolean meshDirty = true;

    // --- Texture management ---

    private Map<String, Integer> textureCache;
    private int[] textures;

    // --- Viewport dimensions ---

    private int viewportWidth;
    private int viewportHeight;

    /**
     * Creates a new SceneRenderer.
     */
    public SceneRenderer() {
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.setIdentityM(viewMatrix, 0);
        Matrix.setIdentityM(projectionMatrix, 0);
        Matrix.setIdentityM(mvpMatrix, 0);
        textureCache = new HashMap<>();
        gridRenderer = new GridRenderer();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Set background color (dark gray)
        GLES20.glClearColor(0.15f, 0.15f, 0.17f, 1.0f);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        // Enable back-face culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        // Enable blending for transparent blocks
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Compile shaders and link program
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE);
        program = linkProgram(vertexShader, fragmentShader);

        if (program == 0) {
            throw new RuntimeException("Failed to create shader program");
        }

        // Get handle locations
        GLES20.glUseProgram(program);
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        modelMatrixHandle = GLES20.glGetUniformLocation(program, "uModelMatrix");
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        colorHandle = GLES20.glGetAttribLocation(program, "aColor");
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord");
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal");
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
        useTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture");
        lightDirHandle = GLES20.glGetUniformLocation(program, "uLightDir");
        ambientStrengthHandle = GLES20.glGetUniformLocation(program, "uAmbientStrength");

        // Set light direction (from upper-right)
        GLES20.glUniform3f(lightDirHandle, 0.5f, 0.8f, 0.3f);
        GLES20.glUniform1f(ambientStrengthHandle, 0.35f);

        // Initialize grid renderer
        gridRenderer.init(program);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewportWidth = width;
        viewportHeight = height;

        // Set viewport
        GLES20.glViewport(0, 0, width, height);

        // Update camera aspect ratio
        float aspect = (float) width / (float) height;
        if (camera != null) {
            camera.setAspect(aspect);
        }

        // Recalculate projection matrix
        float fov = camera != null ? camera.getFov() : 60.0f;
        float near = camera != null ? camera.getNearPlane() : 0.1f;
        float far = camera != null ? camera.getFarPlane() : 1000.0f;
        Matrix.perspectiveM(projectionMatrix, 0, fov, aspect, near, far);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear color and depth buffers
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Update view matrix from camera
        if (camera != null) {
            camera.getViewMatrix(viewMatrix);
        } else {
            Matrix.setLookAtM(viewMatrix, 0,
                    0.0f, 10.0f, 10.0f,
                    0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f);
        }

        // Update projection matrix in case camera settings changed
        if (camera != null) {
            float fov = camera.getFov();
            float near = camera.getNearPlane();
            float far = camera.getFarPlane();
            float aspect = viewportWidth > 0 && viewportHeight > 0
                    ? (float) viewportWidth / (float) viewportHeight : 1.0f;
            Matrix.perspectiveM(projectionMatrix, 0, fov, aspect, near, far);
        }

        // Draw grid
        if (gridVisible && gridRenderer != null) {
            gridRenderer.draw(viewMatrix, projectionMatrix);
        }

        // Draw blocks
        if (scene != null) {
            drawScene();
        }
    }

    /**
     * Draws all blocks in the scene.
     */
    private void drawScene() {
        List<Block3D> blocks = scene.getAllBlocks();
        if (blocks.isEmpty()) return;

        // First pass: draw opaque blocks
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glDepthMask(true);
        for (Block3D block : blocks) {
            if (block.isVisible() && !isTransparent(block)) {
                drawBlock(block);
            }
        }

        // Second pass: draw transparent blocks (depth write off)
        GLES20.glDepthMask(false);
        for (Block3D block : blocks) {
            if (block.isVisible() && isTransparent(block)) {
                drawBlock(block);
            }
        }
        GLES20.glDepthMask(true);
    }

    /**
     * Checks if a block is transparent.
     */
    private boolean isTransparent(Block3D block) {
        float[] color = block.getColor();
        return color[3] < 1.0f;
    }

    /**
     * Draws a single block as a cube with per-face properties.
     *
     * @param block the block to draw
     */
    private void drawBlock(Block3D block) {
        float[] worldPos = block.getWorldPosition();
        float[] blockColor = block.getColor();

        // Set model matrix to translate block to its world position
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, worldPos[0], worldPos[1], worldPos[2]);

        // Calculate MVP matrix
        float[] tempMatrix = new float[16];
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0);

        // Draw each face
        for (Block3D.Face faceType : Block3D.Face.values()) {
            Face face = block.getFace(faceType);
            if (face == null) continue;

            drawFace(face, blockColor);
        }
    }

    /**
     * Draws a single face of a block.
     *
     * @param face       the face to draw
     * @param blockColor the block's overall color (tint)
     */
    private void drawFace(Face face, float[] blockColor) {
        float[] vertices = face.getVertexPositions(Block3D.BLOCK_SIZE);
        float[] faceColor = face.getColor();
        float[] normals = face.getNormal();
        float[] uvCoords = face.getUVCoordinates();
        short[] indices = Face.getQuadIndices();

        // Combine face color with block tint
        float[] finalColor = new float[]{
                faceColor[0] * blockColor[0],
                faceColor[1] * blockColor[1],
                faceColor[2] * blockColor[2],
                faceColor[3] * blockColor[3]
        };

        // Create vertex buffer
        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuffer = vbb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        // Create color buffer (4 vertices, each with RGBA)
        float[] colors = new float[]{
                finalColor[0], finalColor[1], finalColor[2], finalColor[3],
                finalColor[0], finalColor[1], finalColor[2], finalColor[3],
                finalColor[0], finalColor[1], finalColor[2], finalColor[3],
                finalColor[0], finalColor[1], finalColor[2], finalColor[3]
        };
        ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length * 4);
        cbb.order(ByteOrder.nativeOrder());
        colorBuffer = cbb.asFloatBuffer();
        colorBuffer.put(colors);
        colorBuffer.position(0);

        // Create UV buffer
        ByteBuffer uvbb = ByteBuffer.allocateDirect(uvCoords.length * 4);
        uvbb.order(ByteOrder.nativeOrder());
        uvBuffer = uvbb.asFloatBuffer();
        uvBuffer.put(uvCoords);
        uvBuffer.position(0);

        // Create normal buffer (4 vertices, each with the same normal)
        float[] normalArray = new float[]{
                normals[0], normals[1], normals[2],
                normals[0], normals[1], normals[2],
                normals[0], normals[1], normals[2],
                normals[0], normals[1], normals[2]
        };
        ByteBuffer nbb = ByteBuffer.allocateDirect(normalArray.length * 4);
        nbb.order(ByteOrder.nativeOrder());
        normalBuffer = nbb.asFloatBuffer();
        normalBuffer.put(normalArray);
        normalBuffer.position(0);

        // Create index buffer
        ByteBuffer ibb = ByteBuffer.allocateDirect(indices.length * 2);
        ibb.order(ByteOrder.nativeOrder());
        indexBuffer = ibb.asShortBuffer();
        indexBuffer.put(indices);
        indexBuffer.position(0);

        // Bind vertex attributes
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(colorHandle);
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);

        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, uvBuffer);

        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer);

        // Texture handling
        boolean useTexture = face.isHasTexture();
        GLES20.glUniform1i(useTextureHandle, useTexture ? 1 : 0);

        if (useTexture) {
            String texturePath = face.getTexturePath();
            int textureId = getOrLoadTexture(texturePath);
            if (textureId > 0) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
                GLES20.glUniform1i(textureHandle, 0);
            } else {
                GLES20.glUniform1i(useTextureHandle, 0);
            }
        }

        // Draw the face
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        // Disable vertex attributes
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
    }

    /**
     * Gets or loads a GL texture for the given path.
     *
     * @param path texture file path
     * @return GL texture ID, or 0 if failed
     */
    private int getOrLoadTexture(String path) {
        if (path == null || path.isEmpty()) return 0;

        Integer cachedId = textureCache.get(path);
        if (cachedId != null) {
            return cachedId;
        }

        int textureId = loadTexture(path);
        if (textureId > 0) {
            textureCache.put(path, textureId);
        }
        return textureId;
    }

    /**
     * Loads a texture from a file path into OpenGL.
     *
     * @param path file path to the texture
     * @return GL texture ID, or 0 if failed
     */
    public int loadTexture(String path) {
        if (path == null || path.isEmpty()) return 0;

        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        int textureId = textureIds[0];

        if (textureId == 0) return 0;

        try {
            // Load bitmap from file
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
            options.inPremultiplied = false;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path, options);

            if (bitmap == null) return 0;

            // Bind and configure texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

            // Upload bitmap to GL
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            // Generate mipmaps
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

            bitmap.recycle();

            // Unbind
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

            return textureId;

        } catch (Exception e) {
            GLES20.glDeleteTextures(1, textureIds, 0);
            return 0;
        }
    }

    /**
     * Compiles a shader from source code.
     *
     * @param type   shader type (GL_VERTEX_SHADER or GL_FRAGMENT_SHADER)
     * @param source GLSL source code
     * @return compiled shader handle, or 0 if failed
     */
    public int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) return 0;

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compilation failed: " + log);
        }

        return shader;
    }

    /**
     * Links a vertex shader and fragment shader into a program.
     *
     * @param vertexShader   compiled vertex shader handle
     * @param fragmentShader compiled fragment shader handle
     * @return linked program handle, or 0 if failed
     */
    public int linkProgram(int vertexShader, int fragmentShader) {
        int prog = GLES20.glCreateProgram();
        if (prog == 0) return 0;

        GLES20.glAttachShader(prog, vertexShader);
        GLES20.glAttachShader(prog, fragmentShader);
        GLES20.glLinkProgram(prog);

        int[] linked = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(prog);
            GLES20.glDeleteProgram(prog);
            throw new RuntimeException("Program linking failed: " + log);
        }

        return prog;
    }

    /**
     * Rebuilds the vertex buffers from the scene data.
     * Called when the scene changes.
     */
    public void updateMesh() {
        meshDirty = true;
    }

    /**
     * Sets the scene to render.
     *
     * @param scene the scene
     */
    public void setScene(Scene scene) {
        this.scene = scene;
        meshDirty = true;
        // Invalidate texture cache since scene changed
        textureCache.clear();
    }

    /**
     * Sets the camera controller.
     *
     * @param camera the camera controller
     */
    public void setCamera(CameraController camera) {
        this.camera = camera;
    }

    /**
     * Sets grid visibility.
     *
     * @param visible true to show grid
     */
    public void setGridVisible(boolean visible) {
        this.gridVisible = visible;
    }

    /**
     * Performs ray casting from screen coordinates to find a block.
     *
     * @param screenX screen x coordinate
     * @param screenY screen y coordinate
     * @return int[3] {x, y, z} grid coordinates, or null if no block hit
     */
    public int[] getBlockAtScreenPos(float screenX, float screenY) {
        if (scene == null) return null;

        float[] rayStart = new float[4];
        float[] rayEnd = new float[4];

        // Convert screen coordinates to normalized device coordinates
        float[] ndc = screenToNDC(screenX, screenY);

        // Create inverse MVP matrix for unprojection
        float[] invMVP = new float[16];
        float[] mvp = new float[16];
        float[] temp = new float[16];
        Matrix.multiplyMM(temp, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, temp, 0);
        Matrix.invertM(invMVP, 0, mvp, 0);

        // Near plane point
        float[] nearPoint = new float[4];
        Matrix.multiplyMV(nearPoint, 0, invMVP, 0, new float[]{ndc[0], ndc[1], -1.0f, 1.0f}, 0);
        nearPoint[0] /= nearPoint[3];
        nearPoint[1] /= nearPoint[3];
        nearPoint[2] /= nearPoint[3];

        // Far plane point
        float[] farPoint = new float[4];
        Matrix.multiplyMV(farPoint, 0, invMVP, 0, new float[]{ndc[0], ndc[1], 1.0f, 1.0f}, 0);
        farPoint[0] /= farPoint[3];
        farPoint[1] /= farPoint[3];
        farPoint[2] /= farPoint[3];

        // Ray direction
        float[] rayDir = new float[]{
                farPoint[0] - nearPoint[0],
                farPoint[1] - nearPoint[1],
                farPoint[2] - nearPoint[2]
        };
        normalize(rayDir);

        // Test intersection with each block
        float closestT = Float.MAX_VALUE;
        int[] closestBlock = null;

        for (Block3D block : scene.getAllBlocks()) {
            if (!block.isVisible()) continue;

            float[] worldPos = block.getWorldPosition();
            float t = rayBoxIntersect(nearPoint, rayDir, worldPos, Block3D.BLOCK_SIZE);
            if (t > 0 && t < closestT) {
                closestT = t;
                closestBlock = new int[]{block.getX(), block.getY(), block.getZ()};
            }
        }

        return closestBlock;
    }

    /**
     * Gets the world position at the given screen coordinates by intersecting
     * the ray with the ground plane (y=0).
     *
     * @param screenX screen x coordinate
     * @param screenY screen y coordinate
     * @return float[3] world position on ground plane, or null
     */
    public float[] getWorldPosAtScreenPos(float screenX, float screenY) {
        float[] ndc = screenToNDC(screenX, screenY);

        float[] invMVP = new float[16];
        float[] mvp = new float[16];
        float[] temp = new float[16];
        Matrix.multiplyMM(temp, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, temp, 0);
        Matrix.invertM(invMVP, 0, mvp, 0);

        float[] nearPoint = new float[4];
        Matrix.multiplyMV(nearPoint, 0, invMVP, 0, new float[]{ndc[0], ndc[1], -1.0f, 1.0f}, 0);
        nearPoint[0] /= nearPoint[3];
        nearPoint[1] /= nearPoint[3];
        nearPoint[2] /= nearPoint[3];

        float[] farPoint = new float[4];
        Matrix.multiplyMV(farPoint, 0, invMVP, 0, new float[]{ndc[0], ndc[1], 1.0f, 1.0f}, 0);
        farPoint[0] /= farPoint[3];
        farPoint[1] /= farPoint[3];
        farPoint[2] /= farPoint[3];

        float[] rayDir = new float[]{
                farPoint[0] - nearPoint[0],
                farPoint[1] - nearPoint[1],
                farPoint[2] - nearPoint[2]
        };

        // Intersect with y=0 plane
        if (Math.abs(rayDir[1]) < 0.0001f) return null;

        float t = -nearPoint[1] / rayDir[1];
        if (t < 0) return null;

        return new float[]{
                nearPoint[0] + t * rayDir[0],
                0.0f,
                nearPoint[2] + t * rayDir[2]
        };
    }

    /**
     * Converts screen coordinates to normalized device coordinates.
     */
    private float[] screenToNDC(float screenX, float screenY) {
        float x = (2.0f * screenX) / viewportWidth - 1.0f;
        float y = 1.0f - (2.0f * screenY) / viewportHeight;
        return new float[]{x, y};
    }

    /**
     * Tests ray-AABB intersection.
     *
     * @param rayOrigin ray origin point
     * @param rayDir    ray direction (normalized)
     * @param center    box center
     * @param size      box size (cube)
     * @return intersection distance t, or -1 if no hit
     */
    private float rayBoxIntersect(float[] rayOrigin, float[] rayDir, float[] center, float size) {
        float half = size / 2.0f;
        float tMin = Float.NEGATIVE_INFINITY;
        float tMax = Float.POSITIVE_INFINITY;

        // Test X slab
        if (Math.abs(rayDir[0]) > 0.0001f) {
            float t1 = (center[0] - half - rayOrigin[0]) / rayDir[0];
            float t2 = (center[0] + half - rayOrigin[0]) / rayDir[0];
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        } else if (rayOrigin[0] < center[0] - half || rayOrigin[0] > center[0] + half) {
            return -1;
        }

        // Test Y slab
        if (Math.abs(rayDir[1]) > 0.0001f) {
            float t1 = (center[1] - half - rayOrigin[1]) / rayDir[1];
            float t2 = (center[1] + half - rayOrigin[1]) / rayDir[1];
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        } else if (rayOrigin[1] < center[1] - half || rayOrigin[1] > center[1] + half) {
            return -1;
        }

        // Test Z slab
        if (Math.abs(rayDir[2]) > 0.0001f) {
            float t1 = (center[2] - half - rayOrigin[2]) / rayDir[2];
            float t2 = (center[2] + half - rayOrigin[2]) / rayDir[2];
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        } else if (rayOrigin[2] < center[2] - half || rayOrigin[2] > center[2] + half) {
            return -1;
        }

        if (tMax < 0 || tMin > tMax) return -1;
        return tMin > 0 ? tMin : tMax;
    }

    /**
     * Normalizes a 3D vector in place.
     */
    private void normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > 0.0001f) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
    }
}
