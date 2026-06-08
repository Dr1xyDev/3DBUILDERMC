package com.dbuild.net.renderer;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Helper class for rendering a ground grid with axis lines in OpenGL ES 2.0.
 * The grid is drawn on the XZ plane at Y=0 and includes colored axis indicators
 * for X (red), Y (green), and Z (blue).
 */
public class GridRenderer {

    // --- Grid configuration ---

    private int gridSize = 32;
    private float gridSpacing = 1.0f;
    private float[] gridColor = {0.5f, 0.5f, 0.5f, 0.5f};
    private float[] axisColorX = {1.0f, 0.0f, 0.0f, 1.0f};
    private float[] axisColorY = {0.0f, 1.0f, 0.0f, 1.0f};
    private float[] axisColorZ = {0.0f, 0.0f, 1.0f, 1.0f};

    // --- GL buffers ---

    private FloatBuffer gridVertexBuffer;
    private FloatBuffer gridColorBuffer;
    private FloatBuffer axisVertexBuffer;
    private FloatBuffer axisColorBuffer;

    // --- GL handles ---

    private int gridProgram;
    private int mvpMatrixHandle;
    private int positionHandle;
    private int colorHandle;

    // --- State ---

    private boolean initialized = false;
    private int gridVertexCount;
    private int axisVertexCount;

    // --- Shader source for grid (simple colored lines) ---

    private static final String GRID_VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aColor;\n" +
            "varying vec4 vColor;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vColor = aColor;\n" +
            "}\n";

    private static final String GRID_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec4 vColor;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_FragColor = vColor;\n" +
            "}\n";

    /**
     * Creates a new GridRenderer with default settings.
     */
    public GridRenderer() {
    }

    /**
     * Initializes the grid renderer: compiles shaders, creates buffers.
     * Must be called on the GL thread.
     *
     * @param parentProgram the parent SceneRenderer's shader program (unused; grid has its own)
     */
    public void init(int parentProgram) {
        if (initialized) return;

        // Compile and link grid shaders
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, GRID_VERTEX_SHADER);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, GRID_FRAGMENT_SHADER);
        gridProgram = linkProgram(vertexShader, fragmentShader);

        if (gridProgram == 0) {
            throw new RuntimeException("Failed to create grid shader program");
        }

        // Get attribute/uniform locations
        mvpMatrixHandle = GLES20.glGetUniformLocation(gridProgram, "uMVPMatrix");
        positionHandle = GLES20.glGetAttribLocation(gridProgram, "aPosition");
        colorHandle = GLES20.glGetAttribLocation(gridProgram, "aColor");

        // Build grid geometry
        buildGridBuffers();

        initialized = true;
    }

    /**
     * Draws the grid and axis lines.
     *
     * @param viewMatrix       the camera view matrix
     * @param projectionMatrix the projection matrix
     */
    public void draw(float[] viewMatrix, float[] projectionMatrix) {
        if (!initialized) return;

        // Calculate MVP matrix
        float[] mvpMatrix = new float[16];
        float[] modelMatrix = new float[16];
        android.opengl.Matrix.setIdentityM(modelMatrix, 0);
        float[] tempMatrix = new float[16];
        android.opengl.Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        android.opengl.Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);

        GLES20.glUseProgram(gridProgram);
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        // Draw grid lines
        drawGridLines();

        // Draw axis lines
        drawAxisLines();
    }

    /**
     * Draws the grid lines on the XZ plane.
     */
    private void drawGridLines() {
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, gridVertexBuffer);

        GLES20.glEnableVertexAttribArray(colorHandle);
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, gridColorBuffer);

        GLES20.glLineWidth(1.0f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
    }

    /**
     * Draws the colored X, Y, Z axis lines.
     */
    private void drawAxisLines() {
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, axisVertexBuffer);

        GLES20.glEnableVertexAttribArray(colorHandle);
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, axisColorBuffer);

        GLES20.glLineWidth(2.0f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, axisVertexCount);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
    }

    /**
     * Builds the vertex and color buffers for the grid and axis lines.
     */
    private void buildGridBuffers() {
        // --- Grid lines ---

        float half = (gridSize * gridSpacing) / 2.0f;
        int lineCount = gridSize + 1;
        gridVertexCount = lineCount * 4; // 2 vertices per line * 2 directions (X and Z)

        float[] vertices = new float[gridVertexCount * 3];
        float[] colors = new float[gridVertexCount * 4];

        int vIdx = 0;
        int cIdx = 0;

        // Lines parallel to X axis (varying Z)
        for (int i = 0; i <= gridSize; i++) {
            float z = -half + i * gridSpacing;

            // Vertex 1
            vertices[vIdx++] = -half;
            vertices[vIdx++] = 0.0f;
            vertices[vIdx++] = z;
            colors[cIdx++] = gridColor[0];
            colors[cIdx++] = gridColor[1];
            colors[cIdx++] = gridColor[2];
            colors[cIdx++] = gridColor[3];

            // Vertex 2
            vertices[vIdx++] = half;
            vertices[vIdx++] = 0.0f;
            vertices[vIdx++] = z;
            colors[cIdx++] = gridColor[0];
            colors[cIdx++] = gridColor[1];
            colors[cIdx++] = gridColor[2];
            colors[cIdx++] = gridColor[3];
        }

        // Lines parallel to Z axis (varying X)
        for (int i = 0; i <= gridSize; i++) {
            float x = -half + i * gridSpacing;

            // Vertex 1
            vertices[vIdx++] = x;
            vertices[vIdx++] = 0.0f;
            vertices[vIdx++] = -half;
            colors[cIdx++] = gridColor[0];
            colors[cIdx++] = gridColor[1];
            colors[cIdx++] = gridColor[2];
            colors[cIdx++] = gridColor[3];

            // Vertex 2
            vertices[vIdx++] = x;
            vertices[vIdx++] = 0.0f;
            vertices[vIdx++] = half;
            colors[cIdx++] = gridColor[0];
            colors[cIdx++] = gridColor[1];
            colors[cIdx++] = gridColor[2];
            colors[cIdx++] = gridColor[3];
        }

        // Update actual vertex count
        gridVertexCount = vIdx / 3;

        // Create grid vertex buffer
        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        gridVertexBuffer = vbb.asFloatBuffer();
        gridVertexBuffer.put(vertices, 0, vIdx);
        gridVertexBuffer.position(0);

        // Create grid color buffer
        ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length * 4);
        cbb.order(ByteOrder.nativeOrder());
        gridColorBuffer = cbb.asFloatBuffer();
        gridColorBuffer.put(colors, 0, cIdx);
        gridColorBuffer.position(0);

        // --- Axis lines ---

        axisVertexCount = 6; // 3 axes * 2 vertices each
        float[] axisVerts = new float[]{
                // X axis (red)
                0.0f, 0.001f, 0.0f,
                half, 0.001f, 0.0f,
                // Y axis (green)
                0.0f, 0.0f, 0.0f,
                0.0f, half, 0.0f,
                // Z axis (blue)
                0.0f, 0.001f, 0.0f,
                0.0f, 0.001f, half
        };
        float[] axisColors = new float[]{
                // X axis colors
                axisColorX[0], axisColorX[1], axisColorX[2], axisColorX[3],
                axisColorX[0], axisColorX[1], axisColorX[2], axisColorX[3],
                // Y axis colors
                axisColorY[0], axisColorY[1], axisColorY[2], axisColorY[3],
                axisColorY[0], axisColorY[1], axisColorY[2], axisColorY[3],
                // Z axis colors
                axisColorZ[0], axisColorZ[1], axisColorZ[2], axisColorZ[3],
                axisColorZ[0], axisColorZ[1], axisColorZ[2], axisColorZ[3]
        };

        // Create axis vertex buffer
        ByteBuffer avbb = ByteBuffer.allocateDirect(axisVerts.length * 4);
        avbb.order(ByteOrder.nativeOrder());
        axisVertexBuffer = avbb.asFloatBuffer();
        axisVertexBuffer.put(axisVerts);
        axisVertexBuffer.position(0);

        // Create axis color buffer
        ByteBuffer acbb = ByteBuffer.allocateDirect(axisColors.length * 4);
        acbb.order(ByteOrder.nativeOrder());
        axisColorBuffer = acbb.asFloatBuffer();
        axisColorBuffer.put(axisColors);
        axisColorBuffer.position(0);
    }

    /**
     * Sets the grid size (number of grid divisions).
     *
     * @param size grid size (must be positive)
     */
    public void setGridSize(int size) {
        if (size < 1) size = 1;
        this.gridSize = size;
        if (initialized) {
            buildGridBuffers();
        }
    }

    /**
     * Sets the spacing between grid lines.
     *
     * @param spacing spacing in world units (must be positive)
     */
    public void setGridSpacing(float spacing) {
        if (spacing <= 0.0f) spacing = 1.0f;
        this.gridSpacing = spacing;
        if (initialized) {
            buildGridBuffers();
        }
    }

    /**
     * Gets the current grid size.
     *
     * @return grid size
     */
    public int getGridSize() {
        return gridSize;
    }

    /**
     * Gets the current grid spacing.
     *
     * @return grid spacing
     */
    public float getGridSpacing() {
        return gridSpacing;
    }

    /**
     * Compiles a shader from source code.
     *
     * @param type   shader type
     * @param source GLSL source
     * @return shader handle
     */
    private int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) return 0;

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Grid shader compilation failed: " + log);
        }

        return shader;
    }

    /**
     * Links vertex and fragment shaders into a program.
     *
     * @param vertexShader   vertex shader handle
     * @param fragmentShader fragment shader handle
     * @return program handle
     */
    private int linkProgram(int vertexShader, int fragmentShader) {
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
            throw new RuntimeException("Grid program linking failed: " + log);
        }

        return prog;
    }

    /**
     * Releases all GL resources.
     */
    public void release() {
        if (gridProgram != 0) {
            GLES20.glDeleteProgram(gridProgram);
            gridProgram = 0;
        }
        initialized = false;
    }
}
