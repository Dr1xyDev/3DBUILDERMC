package com.dbuild.net.export;

import android.content.Context;

import com.dbuild.net.model.Block3D;
import com.dbuild.net.model.Scene;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public class GLBExporter {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION = 2;
    private static final int JSON_CHUNK_TYPE = 0x4E4F534A;
    private static final int BIN_CHUNK_TYPE = 0x004E4942;

    private final Context context;
    private ProgressCallback progressCallback;

    public GLBExporter(Context context) {
        this.context = context;
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public interface ProgressCallback {
        void onProgress(int percent, String message);
        void onCompleted(String outputPath);
        void onError(String error);
    }

    public static class ExportOptions {
        public boolean includeTextures = true;
        public boolean includeMaterials = true;
        public float scale = 1.0f;

        public ExportOptions() {}

        public ExportOptions(boolean includeTextures, boolean includeMaterials, float scale) {
            this.includeTextures = includeTextures;
            this.includeMaterials = includeMaterials;
            this.scale = scale;
        }
    }

    public static class BufferData {
        public float[] vertices;
        public float[] normals;
        public float[] uvs;
        public int[] indices;
    }

    public boolean export(Scene scene, String outputPath, ExportOptions options) {
        try {
            if (progressCallback != null) {
                progressCallback.onProgress(0, "Starting GLB export...");
            }

            byte[] glbData = buildGLBStructure(scene);
            if (glbData == null) {
                if (progressCallback != null) {
                    progressCallback.onError("Failed to build GLB structure");
                }
                return false;
            }

            if (progressCallback != null) {
                progressCallback.onProgress(80, "Writing GLB file...");
            }

            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(glbData);
                fos.flush();
            }

            if (options.includeTextures) {
                String outputDir = outputFile.getParent();
                if (outputDir != null) {
                    exportTextures(scene, outputDir);
                }
            }

            if (progressCallback != null) {
                progressCallback.onProgress(100, "GLB export completed");
                progressCallback.onCompleted(outputPath);
            }

            return true;
        } catch (Exception e) {
            if (progressCallback != null) {
                progressCallback.onError("GLB export failed: " + e.getMessage());
            }
            return false;
        }
    }

    public byte[] buildGLBStructure(Scene scene) {
        try {
            String jsonStr = buildJSONChunk(scene);
            byte[] jsonData = jsonStr.getBytes("UTF-8");
            jsonData = padTo4Bytes(jsonData);

            byte[] binaryData = buildBinaryChunk(scene);
            binaryData = padTo4Bytes(binaryData);

            int totalLength = 12 + 8 + jsonData.length + 8 + binaryData.length;

            ByteBuffer buffer = ByteBuffer.allocate(totalLength);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.putInt(GLB_MAGIC);
            buffer.putInt(GLB_VERSION);
            buffer.putInt(totalLength);

            buffer.putInt(jsonData.length);
            buffer.putInt(JSON_CHUNK_TYPE);
            buffer.put(jsonData);

            buffer.putInt(binaryData.length);
            buffer.putInt(BIN_CHUNK_TYPE);
            buffer.put(binaryData);

            return buffer.array();
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] writeChunk(int type, byte[] data) {
        data = padTo4Bytes(data);
        ByteBuffer buffer = ByteBuffer.allocate(8 + data.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(data.length);
        buffer.putInt(type);
        buffer.put(data);
        return buffer.array();
    }

    public String buildJSONChunk(Scene scene) {
        try {
            JSONObject root = new JSONObject();
            JSONObject asset = new JSONObject();
            asset.put("version", "2.0");
            asset.put("generator", "3D Builder MC GLB Exporter");
            root.put("asset", asset);

            JSONArray scenes = new JSONArray();
            JSONObject sceneObj = new JSONObject();
            sceneObj.put("name", scene.getName() != null ? scene.getName() : "Scene");
            JSONArray nodes = new JSONArray();
            nodes.put(0);
            sceneObj.put("nodes", nodes);
            scenes.put(sceneObj);
            root.put("scenes", scenes);
            root.put("scene", 0);

            JSONArray rootNodes = new JSONArray();
            JSONObject nodeObj = new JSONObject();
            nodeObj.put("name", "RootNode");
            nodeObj.put("mesh", 0);
            rootNodes.put(nodeObj);
            root.put("nodes", rootNodes);

            JSONArray meshes = new JSONArray();
            JSONObject meshObj = new JSONObject();
            meshObj.put("name", "SceneMesh");
            JSONArray primitives = new JSONArray();
            JSONObject primitive = new JSONObject();
            primitive.put("attributes", new JSONObject().put("POSITION", 0).put("NORMAL", 1).put("TEXCOORD_0", 2));
            primitive.put("indices", 3);
            if (scene.getMaterialCount() > 0) {
                primitive.put("material", 0);
            }
            primitive.put("mode", 4);
            primitives.put(primitive);
            meshObj.put("primitives", primitives);
            meshes.put(meshObj);
            root.put("meshes", meshes);

            BufferData bufferData = collectBuffers(scene);

            int vertexCount = bufferData.vertices.length / 3;
            int normalCount = bufferData.normals.length / 3;
            int uvCount = bufferData.uvs.length / 2;
            int indexCount = bufferData.indices.length;

            int vertexByteLength = bufferData.vertices.length * 4;
            int normalByteLength = bufferData.normals.length * 4;
            int uvByteLength = bufferData.uvs.length * 4;
            int indexByteLength = bufferData.indices.length * 4;

            JSONArray accessors = new JSONArray();

            JSONObject posAccessor = new JSONObject();
            posAccessor.put("bufferView", 0);
            posAccessor.put("componentType", 5126);
            posAccessor.put("count", vertexCount);
            posAccessor.put("type", "VEC3");
            JSONObject posMax = new JSONObject();
            posMax.put("x", getMax(bufferData.vertices, 0, 3));
            posMax.put("y", getMax(bufferData.vertices, 1, 3));
            posMax.put("z", getMax(bufferData.vertices, 2, 3));
            JSONObject posMin = new JSONObject();
            posMin.put("x", getMin(bufferData.vertices, 0, 3));
            posMin.put("y", getMin(bufferData.vertices, 1, 3));
            posMin.put("z", getMin(bufferData.vertices, 2, 3));
            posAccessor.put("max", posMax);
            posAccessor.put("min", posMin);
            accessors.put(posAccessor);

            JSONObject normAccessor = new JSONObject();
            normAccessor.put("bufferView", 1);
            normAccessor.put("componentType", 5126);
            normAccessor.put("count", normalCount);
            normAccessor.put("type", "VEC3");
            accessors.put(normAccessor);

            JSONObject uvAccessor = new JSONObject();
            uvAccessor.put("bufferView", 2);
            uvAccessor.put("componentType", 5126);
            uvAccessor.put("count", uvCount);
            uvAccessor.put("type", "VEC2");
            accessors.put(uvAccessor);

            JSONObject idxAccessor = new JSONObject();
            idxAccessor.put("bufferView", 3);
            idxAccessor.put("componentType", 5125);
            idxAccessor.put("count", indexCount);
            idxAccessor.put("type", "SCALAR");
            accessors.put(idxAccessor);

            root.put("accessors", accessors);

            JSONArray bufferViews = new JSONArray();

            JSONObject posView = new JSONObject();
            posView.put("buffer", 0);
            posView.put("byteOffset", 0);
            posView.put("byteLength", vertexByteLength);
            posView.put("target", 34962);
            bufferViews.put(posView);

            JSONObject normView = new JSONObject();
            normView.put("buffer", 0);
            normView.put("byteOffset", vertexByteLength);
            normView.put("byteLength", normalByteLength);
            normView.put("target", 34962);
            bufferViews.put(normView);

            JSONObject uvView = new JSONObject();
            uvView.put("buffer", 0);
            uvView.put("byteOffset", vertexByteLength + normalByteLength);
            uvView.put("byteLength", uvByteLength);
            uvView.put("target", 34962);
            bufferViews.put(uvView);

            JSONObject idxView = new JSONObject();
            idxView.put("buffer", 0);
            idxView.put("byteOffset", vertexByteLength + normalByteLength + uvByteLength);
            idxView.put("byteLength", indexByteLength);
            idxView.put("target", 34963);
            bufferViews.put(idxView);

            root.put("bufferViews", bufferViews);

            int totalBufferLength = vertexByteLength + normalByteLength + uvByteLength + indexByteLength;
            JSONArray buffers = new JSONArray();
            JSONObject bufferObj = new JSONObject();
            bufferObj.put("byteLength", totalBufferLength);
            buffers.put(bufferObj);
            root.put("buffers", buffers);

            if (scene.getMaterialCount() > 0) {
                JSONArray materials = new JSONArray();
                JSONObject material = new JSONObject();
                material.put("name", "DefaultMaterial");
                JSONObject pbr = new JSONObject();
                JSONObject baseColorFactor = new JSONArray();
                baseColorFactor.put(0.8);
                baseColorFactor.put(0.8);
                baseColorFactor.put(0.8);
                baseColorFactor.put(1.0);
                pbr.put("baseColorFactor", baseColorFactor);
                pbr.put("metallicFactor", 0.0);
                pbr.put("roughnessFactor", 1.0);
                material.put("pbrMetallicRoughness", pbr);
                materials.put(material);
                root.put("materials", materials);
            }

            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public byte[] buildBinaryChunk(Scene scene) {
        try {
            BufferData bufferData = collectBuffers(scene);

            int vertexByteLength = bufferData.vertices.length * 4;
            int normalByteLength = bufferData.normals.length * 4;
            int uvByteLength = bufferData.uvs.length * 4;
            int indexByteLength = bufferData.indices.length * 4;
            int totalLength = vertexByteLength + normalByteLength + uvByteLength + indexByteLength;

            ByteBuffer buffer = ByteBuffer.allocate(totalLength);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            for (float v : bufferData.vertices) {
                buffer.putFloat(v);
            }
            for (float n : bufferData.normals) {
                buffer.putFloat(n);
            }
            for (float uv : bufferData.uvs) {
                buffer.putFloat(uv);
            }
            for (int idx : bufferData.indices) {
                buffer.putInt(idx);
            }

            return buffer.array();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public BufferData collectBuffers(Scene scene) {
        BufferData data = new BufferData();
        List<Float> vertices = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int vertexOffset = 0;
        List<Block3D> blocks = scene.getBlocks();

        for (Block3D block : blocks) {
            CubeData cube = generateCubeVertices(block);
            for (float v : cube.vertices) {
                vertices.add(v);
            }
            for (float n : cube.normals) {
                normals.add(n);
            }
            for (float uv : cube.uvs) {
                uvs.add(uv);
            }
            for (int idx : cube.indices) {
                indices.add(vertexOffset + idx);
            }
            vertexOffset += cube.vertices.length / 3;
        }

        data.vertices = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            data.vertices[i] = vertices.get(i);
        }
        data.normals = new float[normals.size()];
        for (int i = 0; i < normals.size(); i++) {
            data.normals[i] = normals.get(i);
        }
        data.uvs = new float[uvs.size()];
        for (int i = 0; i < uvs.size(); i++) {
            data.uvs[i] = uvs.get(i);
        }
        data.indices = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            data.indices[i] = indices.get(i);
        }

        return data;
    }

    private static class CubeData {
        float[] vertices;
        float[] normals;
        float[] uvs;
        int[] indices;
    }

    private CubeData generateCubeVertices(Block3D block) {
        CubeData cube = new CubeData();
        float x = block.getX();
        float y = block.getY();
        float z = block.getZ();
        float s = 1.0f;

        cube.vertices = new float[]{
            // Front face
            x, y, z + s,     x + s, y, z + s,     x + s, y + s, z + s,     x, y + s, z + s,
            // Back face
            x + s, y, z,     x, y, z,     x, y + s, z,     x + s, y + s, z,
            // Top face
            x, y + s, z + s,     x + s, y + s, z + s,     x + s, y + s, z,     x, y + s, z,
            // Bottom face
            x, y, z,     x + s, y, z,     x + s, y, z + s,     x, y, z + s,
            // Right face
            x + s, y, z + s,     x + s, y, z,     x + s, y + s, z,     x + s, y + s, z + s,
            // Left face
            x, y, z,     x, y, z + s,     x, y + s, z + s,     x, y + s, z
        };

        cube.normals = new float[]{
            0, 0, 1,   0, 0, 1,   0, 0, 1,   0, 0, 1,
            0, 0, -1,  0, 0, -1,  0, 0, -1,  0, 0, -1,
            0, 1, 0,   0, 1, 0,   0, 1, 0,   0, 1, 0,
            0, -1, 0,  0, -1, 0,  0, -1, 0,  0, -1, 0,
            1, 0, 0,   1, 0, 0,   1, 0, 0,   1, 0, 0,
            -1, 0, 0,  -1, 0, 0,  -1, 0, 0,  -1, 0, 0
        };

        cube.uvs = new float[]{
            0, 0,  1, 0,  1, 1,  0, 1,
            0, 0,  1, 0,  1, 1,  0, 1,
            0, 0,  1, 0,  1, 1,  0, 1,
            0, 0,  1, 0,  1, 1,  0, 1,
            0, 0,  1, 0,  1, 1,  0, 1,
            0, 0,  1, 0,  1, 1,  0, 1
        };

        cube.indices = new int[]{
            0, 1, 2,  0, 2, 3,
            4, 5, 6,  4, 6, 7,
            8, 9, 10,  8, 10, 11,
            12, 13, 14,  12, 14, 15,
            16, 17, 18,  16, 18, 19,
            20, 21, 22,  20, 22, 23
        };

        return cube;
    }

    public boolean exportTextures(Scene scene, String outputDir) {
        try {
            File texDir = new File(outputDir, "textures");
            if (!texDir.exists()) {
                texDir.mkdirs();
            }

            List<String> textures = scene.getTextures();
            if (textures == null) {
                return true;
            }

            for (int i = 0; i < textures.size(); i++) {
                String textureName = textures.get(i);
                if (textureName == null || textureName.isEmpty()) {
                    continue;
                }

                File srcFile = new File(textureName);
                if (srcFile.exists()) {
                    File destFile = new File(texDir, srcFile.getName());
                    copyFileInternal(srcFile, destFile);
                } else {
                    String assetPath = "textures/" + textureName;
                    try (InputStream is = context.getAssets().open(assetPath)) {
                        File destFile = new File(texDir, textureName);
                        copyStreamToFile(is, destFile);
                    } catch (IOException ignored) {
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void copyFileInternal(File src, File dest) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dest);
             java.io.FileInputStream fis = new java.io.FileInputStream(src)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }

    private void copyStreamToFile(InputStream is, File dest) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }

    public byte[] padTo4Bytes(byte[] data) {
        int padding = (4 - (data.length % 4)) % 4;
        if (padding == 0) {
            return data;
        }
        byte[] padded = new byte[data.length + padding];
        System.arraycopy(data, 0, padded, 0, data.length);
        for (int i = data.length; i < padded.length; i++) {
            padded[i] = 0x20;
        }
        return padded;
    }

    public byte[] intToBytes(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        return buffer.array();
    }

    private float getMax(float[] data, int startOffset, int stride) {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = startOffset; i < data.length; i += stride) {
            if (data[i] > max) {
                max = data[i];
            }
        }
        return max;
    }

    private float getMin(float[] data, int startOffset, int stride) {
        float min = Float.POSITIVE_INFINITY;
        for (int i = startOffset; i < data.length; i += stride) {
            if (data[i] < min) {
                min = data[i];
            }
        }
        return min;
    }
}
