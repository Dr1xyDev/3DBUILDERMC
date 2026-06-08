package com.dbuild.net.export;

import android.content.Context;

import com.dbuild.net.model.Block3D;
import com.dbuild.net.model.Scene;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class GLTFExporter {

    private final Context context;
    private ProgressCallback progressCallback;

    public GLTFExporter(Context context) {
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

    public static class MeshData {
        public float[] vertices;
        public float[] normals;
        public float[] uvs;
        public int[] indices;
        public List<String> textureNames = new ArrayList<>();
    }

    public boolean export(Scene scene, String outputPath, ExportOptions options) {
        try {
            if (progressCallback != null) {
                progressCallback.onProgress(0, "Starting glTF export...");
            }

            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            if (progressCallback != null) {
                progressCallback.onProgress(20, "Building glTF JSON...");
            }

            JSONObject gltfJson = buildGLTFJSON(scene);

            if (progressCallback != null) {
                progressCallback.onProgress(50, "Writing binary buffer...");
            }

            String outputDir = outputFile.getParent();
            String binFileName = writeBinaryBuffer(scene, outputDir);

            if (progressCallback != null) {
                progressCallback.onProgress(70, "Writing glTF file...");
            }

            String jsonStr = gltfJson.toString(2);
            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 Writer writer = new OutputStreamWriter(fos, "UTF-8")) {
                writer.write(jsonStr);
                writer.flush();
            }

            if (options.includeTextures) {
                if (progressCallback != null) {
                    progressCallback.onProgress(85, "Exporting textures...");
                }
                if (outputDir != null) {
                    exportTextures(scene, outputDir);
                }
            }

            if (progressCallback != null) {
                progressCallback.onProgress(100, "glTF export completed");
                progressCallback.onCompleted(outputPath);
            }

            return true;
        } catch (Exception e) {
            if (progressCallback != null) {
                progressCallback.onError("glTF export failed: " + e.getMessage());
            }
            return false;
        }
    }

    public JSONObject buildGLTFJSON(Scene scene) {
        try {
            JSONObject root = new JSONObject();
            root.put("asset", buildAssetInfo());

            JSONArray scenesArr = new JSONArray();
            scenesArr.put(buildSceneObject(scene));
            root.put("scenes", scenesArr);
            root.put("scene", 0);

            root.put("nodes", buildNodes(scene));
            root.put("meshes", buildMeshes(scene));
            root.put("accessors", buildAccessors(scene));
            root.put("bufferViews", buildBufferViews(scene));
            root.put("buffers", buildBuffers(scene));

            if (scene.getMaterialCount() > 0) {
                root.put("materials", buildMaterials(scene));
            }

            List<String> textures = scene.getTextures();
            if (textures != null && !textures.isEmpty()) {
                root.put("textures", buildTextures(scene));
                root.put("images", buildImages(scene));
            }

            return root;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public JSONObject buildAssetInfo() {
        try {
            JSONObject asset = new JSONObject();
            asset.put("version", "2.0");
            asset.put("generator", "3D Builder MC glTF Exporter");
            return asset;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public JSONObject buildSceneObject(Scene scene) {
        try {
            JSONObject sceneObj = new JSONObject();
            sceneObj.put("name", scene.getName() != null ? scene.getName() : "Scene");
            JSONArray nodes = new JSONArray();
            nodes.put(0);
            sceneObj.put("nodes", nodes);
            return sceneObj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public JSONArray buildNodes(Scene scene) {
        try {
            JSONArray nodes = new JSONArray();
            JSONObject node = new JSONObject();
            node.put("name", "RootNode");
            node.put("mesh", 0);

            List<Block3D> blocks = scene.getBlocks();
            if (blocks != null && !blocks.isEmpty()) {
                JSONArray translation = new JSONArray();
                translation.put(0.0);
                translation.put(0.0);
                translation.put(0.0);
                node.put("translation", translation);
            }

            nodes.put(node);
            return nodes;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public JSONArray buildMeshes(Scene scene) {
        try {
            JSONArray meshes = new JSONArray();
            JSONObject mesh = new JSONObject();
            mesh.put("name", "SceneMesh");

            JSONArray primitives = new JSONArray();
            JSONObject primitive = new JSONObject();
            JSONObject attributes = new JSONObject();
            attributes.put("POSITION", 0);
            attributes.put("NORMAL", 1);
            attributes.put("TEXCOORD_0", 2);
            primitive.put("attributes", attributes);
            primitive.put("indices", 3);

            if (scene.getMaterialCount() > 0) {
                primitive.put("material", 0);
            }
            primitive.put("mode", 4);
            primitives.put(primitive);

            mesh.put("primitives", primitives);
            meshes.put(mesh);
            return meshes;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public JSONArray buildAccessors(Scene scene) {
        try {
            MeshData meshData = collectMeshData(scene);
            JSONArray accessors = new JSONArray();

            int vertexCount = meshData.vertices.length / 3;
            int normalCount = meshData.normals.length / 3;
            int uvCount = meshData.uvs.length / 2;
            int indexCount = meshData.indices.length;

            JSONObject posAccessor = new JSONObject();
            posAccessor.put("bufferView", 0);
            posAccessor.put("componentType", 5126);
            posAccessor.put("count", vertexCount);
            posAccessor.put("type", "VEC3");
            JSONObject posMax = new JSONObject();
            posMax.put("x", getMaxValue(meshData.vertices, 0, 3));
            posMax.put("y", getMaxValue(meshData.vertices, 1, 3));
            posMax.put("z", getMaxValue(meshData.vertices, 2, 3));
            JSONObject posMin = new JSONObject();
            posMin.put("x", getMinValue(meshData.vertices, 0, 3));
            posMin.put("y", getMinValue(meshData.vertices, 1, 3));
            posMin.put("z", getMinValue(meshData.vertices, 2, 3));
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

            return accessors;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public JSONArray buildBufferViews(Scene scene) {
        try {
            MeshData meshData = collectMeshData(scene);
            int vertexByteLength = meshData.vertices.length * 4;
            int normalByteLength = meshData.normals.length * 4;
            int uvByteLength = meshData.uvs.length * 4;
            int indexByteLength = meshData.indices.length * 4;

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

            return bufferViews;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public JSONArray buildBuffers(Scene scene) {
        try {
            MeshData meshData = collectMeshData(scene);
            int totalLength = (meshData.vertices.length + meshData.normals.length + meshData.uvs.length) * 4
                    + meshData.indices.length * 4;

            JSONArray buffers = new JSONArray();
            JSONObject buffer = new JSONObject();
            buffer.put("uri", getBaseFileName(scene) + ".bin");
            buffer.put("byteLength", totalLength);
            buffers.put(buffer);
            return buffers;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public JSONArray buildMaterials(Scene scene) {
        try {
            JSONArray materials = new JSONArray();
            JSONObject material = new JSONObject();
            material.put("name", "DefaultMaterial");

            JSONObject pbr = new JSONObject();
            JSONArray baseColor = new JSONArray();
            baseColor.put(0.8);
            baseColor.put(0.8);
            baseColor.put(0.8);
            baseColor.put(1.0);
            pbr.put("baseColorFactor", baseColor);
            pbr.put("metallicFactor", 0.0);
            pbr.put("roughnessFactor", 1.0);
            material.put("pbrMetallicRoughness", pbr);

            material.put("doubleSided", true);
            materials.put(material);
            return materials;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public JSONArray buildTextures(Scene scene) {
        try {
            JSONArray textures = new JSONArray();
            List<String> texList = scene.getTextures();
            if (texList == null) {
                return textures;
            }
            for (int i = 0; i < texList.size(); i++) {
                JSONObject texture = new JSONObject();
                texture.put("sampler", 0);
                texture.put("source", i);
                textures.put(texture);
            }
            return textures;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public JSONArray buildImages(Scene scene) {
        try {
            JSONArray images = new JSONArray();
            List<String> texList = scene.getTextures();
            if (texList == null) {
                return images;
            }
            for (int i = 0; i < texList.size(); i++) {
                JSONObject image = new JSONObject();
                String texName = texList.get(i);
                File f = new File(texName);
                image.put("uri", "textures/" + f.getName());
                images.put(image);
            }
            return images;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public String writeBinaryBuffer(Scene scene, String outputDir) {
        try {
            MeshData meshData = collectMeshData(scene);
            String binFileName = getBaseFileName(scene) + ".bin";
            File binFile = new File(outputDir, binFileName);

            int vertexByteLength = meshData.vertices.length * 4;
            int normalByteLength = meshData.normals.length * 4;
            int uvByteLength = meshData.uvs.length * 4;
            int indexByteLength = meshData.indices.length * 4;
            int totalLength = vertexByteLength + normalByteLength + uvByteLength + indexByteLength;

            ByteBuffer buffer = ByteBuffer.allocate(totalLength);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            for (float v : meshData.vertices) {
                buffer.putFloat(v);
            }
            for (float n : meshData.normals) {
                buffer.putFloat(n);
            }
            for (float uv : meshData.uvs) {
                buffer.putFloat(uv);
            }
            for (int idx : meshData.indices) {
                buffer.putInt(idx);
            }

            try (FileOutputStream fos = new FileOutputStream(binFile)) {
                fos.write(buffer.array());
                fos.flush();
            }

            return binFileName;
        } catch (Exception e) {
            return null;
        }
    }

    public MeshData collectMeshData(Scene scene) {
        MeshData data = new MeshData();
        List<Float> vertices = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int vertexOffset = 0;
        List<Block3D> blocks = scene.getBlocks();

        for (Block3D block : blocks) {
            float x = block.getX();
            float y = block.getY();
            float z = block.getZ();
            float s = 1.0f;

            float[] cubeVerts = {
                x, y, z + s,     x + s, y, z + s,     x + s, y + s, z + s,     x, y + s, z + s,
                x + s, y, z,     x, y, z,     x, y + s, z,     x + s, y + s, z,
                x, y + s, z + s,     x + s, y + s, z + s,     x + s, y + s, z,     x, y + s, z,
                x, y, z,     x + s, y, z,     x + s, y, z + s,     x, y, z + s,
                x + s, y, z + s,     x + s, y, z,     x + s, y + s, z,     x + s, y + s, z + s,
                x, y, z,     x, y, z + s,     x, y + s, z + s,     x, y + s, z
            };

            float[] cubeNorms = {
                0, 0, 1,   0, 0, 1,   0, 0, 1,   0, 0, 1,
                0, 0, -1,  0, 0, -1,  0, 0, -1,  0, 0, -1,
                0, 1, 0,   0, 1, 0,   0, 1, 0,   0, 1, 0,
                0, -1, 0,  0, -1, 0,  0, -1, 0,  0, -1, 0,
                1, 0, 0,   1, 0, 0,   1, 0, 0,   1, 0, 0,
                -1, 0, 0,  -1, 0, 0,  -1, 0, 0,  -1, 0, 0
            };

            float[] cubeUvs = {
                0, 0,  1, 0,  1, 1,  0, 1,
                0, 0,  1, 0,  1, 1,  0, 1,
                0, 0,  1, 0,  1, 1,  0, 1,
                0, 0,  1, 0,  1, 1,  0, 1,
                0, 0,  1, 0,  1, 1,  0, 1,
                0, 0,  1, 0,  1, 1,  0, 1
            };

            int[] cubeIdx = {
                0, 1, 2,  0, 2, 3,
                4, 5, 6,  4, 6, 7,
                8, 9, 10,  8, 10, 11,
                12, 13, 14,  12, 14, 15,
                16, 17, 18,  16, 18, 19,
                20, 21, 22,  20, 22, 23
            };

            for (float v : cubeVerts) vertices.add(v);
            for (float n : cubeNorms) normals.add(n);
            for (float uv : cubeUvs) uvs.add(uv);
            for (int idx : cubeIdx) indices.add(vertexOffset + idx);
            vertexOffset += cubeVerts.length / 3;
        }

        data.vertices = toFloatArray(vertices);
        data.normals = toFloatArray(normals);
        data.uvs = toFloatArray(uvs);
        data.indices = toIntArray(indices);

        return data;
    }

    private boolean exportTextures(Scene scene, String outputDir) {
        try {
            File texDir = new File(outputDir, "textures");
            if (!texDir.exists()) {
                texDir.mkdirs();
            }
            List<String> textures = scene.getTextures();
            if (textures == null) return true;
            for (String textureName : textures) {
                if (textureName == null || textureName.isEmpty()) continue;
                File srcFile = new File(textureName);
                if (srcFile.exists()) {
                    File destFile = new File(texDir, srcFile.getName());
                    copyFileInternal(srcFile, destFile);
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
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
        }
    }

    private String getBaseFileName(Scene scene) {
        String name = scene.getName();
        if (name == null || name.isEmpty()) {
            name = "scene";
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private float getMaxValue(float[] data, int startOffset, int stride) {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = startOffset; i < data.length; i += stride) {
            if (data[i] > max) max = data[i];
        }
        return max;
    }

    private float getMinValue(float[] data, int startOffset, int stride) {
        float min = Float.POSITIVE_INFINITY;
        for (int i = startOffset; i < data.length; i += stride) {
            if (data[i] < min) min = data[i];
        }
        return min;
    }

    private float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}
