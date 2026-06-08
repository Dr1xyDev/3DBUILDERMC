package com.dbuild.net.export;

import android.content.Context;

import com.dbuild.net.model.Block3D;
import com.dbuild.net.model.Scene;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OBJExporter {

    private final Context context;
    private ProgressCallback progressCallback;

    public OBJExporter(Context context) {
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
        public boolean exportNormals = true;
        public boolean exportUVs = true;
        public boolean groupPerBlock = true;

        public ExportOptions() {}

        public ExportOptions(boolean includeTextures, boolean includeMaterials, float scale) {
            this.includeTextures = includeTextures;
            this.includeMaterials = includeMaterials;
            this.scale = scale;
        }
    }

    public static class CubeData {
        public float[] vertices;
        public float[] normals;
        public float[] uvs;
        public int[] indices;
    }

    public boolean export(Scene scene, String outputPath, ExportOptions options) {
        try {
            if (progressCallback != null) {
                progressCallback.onProgress(0, "Starting OBJ export...");
            }

            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            String mtlFileName = getBaseName(outputPath) + ".mtl";

            if (progressCallback != null) {
                progressCallback.onProgress(20, "Writing OBJ file...");
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 Writer writer = new OutputStreamWriter(fos, "UTF-8")) {
                writer.write("# 3D Builder MC OBJ Export\n");
                writer.write("# https://dbuild.net\n\n");
                writer.write("mtllib " + mtlFileName + "\n\n");

                boolean result = writeOBJ(scene, writer);
                if (!result) {
                    if (progressCallback != null) {
                        progressCallback.onError("Failed to write OBJ data");
                    }
                    return false;
                }
            }

            if (progressCallback != null) {
                progressCallback.onProgress(60, "Writing MTL file...");
            }

            String mtlPath = new File(parentDir, mtlFileName).getAbsolutePath();
            try (FileOutputStream mtlFos = new FileOutputStream(mtlPath);
                 Writer mtlWriter = new OutputStreamWriter(mtlFos, "UTF-8")) {
                mtlWriter.write("# 3D Builder MC MTL Export\n\n");
                writeMTL(scene, mtlWriter, mtlFileName);
            }

            if (options.includeTextures) {
                if (progressCallback != null) {
                    progressCallback.onProgress(80, "Exporting textures...");
                }
                if (parentDir != null) {
                    exportTextures(scene, parentDir.getAbsolutePath());
                }
            }

            if (progressCallback != null) {
                progressCallback.onProgress(100, "OBJ export completed");
                progressCallback.onCompleted(outputPath);
            }

            return true;
        } catch (Exception e) {
            if (progressCallback != null) {
                progressCallback.onError("OBJ export failed: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean writeOBJ(Scene scene, Writer writer) {
        try {
            List<Block3D> blocks = scene.getBlocks();
            int vertexOffset = 1;
            int normalOffset = 1;
            int uvOffset = 1;

            for (int blockIdx = 0; blockIdx < blocks.size(); blockIdx++) {
                Block3D block = blocks.get(blockIdx);
                CubeData cube = generateCubeVertices(block);

                writer.write("o Block_" + blockIdx + "\n");

                for (int i = 0; i < cube.vertices.length; i += 3) {
                    writer.write(formatVertex(cube.vertices[i], cube.vertices[i + 1], cube.vertices[i + 2]));
                    writer.write("\n");
                }

                if (cube.uvs != null && cube.uvs.length > 0) {
                    for (int i = 0; i < cube.uvs.length; i += 2) {
                        writer.write(formatUV(cube.uvs[i], cube.uvs[i + 1]));
                        writer.write("\n");
                    }
                }

                if (cube.normals != null && cube.normals.length > 0) {
                    for (int i = 0; i < cube.normals.length; i += 3) {
                        writer.write(formatNormal(cube.normals[i], cube.normals[i + 1], cube.normals[i + 2]));
                        writer.write("\n");
                    }
                }

                String textureName = block.getTextureName();
                if (textureName != null && !textureName.isEmpty()) {
                    writer.write("usemtl " + sanitizeMaterialName(textureName) + "\n");
                } else {
                    writer.write("usemtl DefaultMaterial\n");
                }

                for (int i = 0; i < cube.indices.length; i += 3) {
                    int v1 = cube.indices[i] + vertexOffset;
                    int v2 = cube.indices[i + 1] + vertexOffset;
                    int v3 = cube.indices[i + 2] + vertexOffset;
                    int vt1 = cube.indices[i] + uvOffset;
                    int vt2 = cube.indices[i + 1] + uvOffset;
                    int vt3 = cube.indices[i + 2] + uvOffset;
                    int vn1 = cube.indices[i] + normalOffset;
                    int vn2 = cube.indices[i + 1] + normalOffset;
                    int vn3 = cube.indices[i + 2] + normalOffset;

                    writer.write(formatFace(v1, vt1, vn1, v2, vt2, vn2, v3, vt3, vn3));
                    writer.write("\n");
                }

                writer.write("\n");

                vertexOffset += cube.vertices.length / 3;
                normalOffset += cube.normals.length / 3;
                uvOffset += cube.uvs.length / 2;
            }

            writer.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean writeMTL(Scene scene, Writer writer, String mtlFileName) {
        try {
            Map<String, String> materialTextures = new HashMap<>();
            List<Block3D> blocks = scene.getBlocks();

            for (Block3D block : blocks) {
                String texName = block.getTextureName();
                if (texName != null && !texName.isEmpty()) {
                    String matName = sanitizeMaterialName(texName);
                    if (!materialTextures.containsKey(matName)) {
                        materialTextures.put(matName, texName);
                    }
                }
            }

            if (materialTextures.isEmpty()) {
                writer.write("newmtl DefaultMaterial\n");
                writer.write("Ka 0.2 0.2 0.2\n");
                writer.write("Kd 0.8 0.8 0.8\n");
                writer.write("Ks 0.0 0.0 0.0\n");
                writer.write("Ns 1.0\n");
                writer.write("d 1.0\n");
                writer.write("illum 2\n\n");
            } else {
                for (Map.Entry<String, String> entry : materialTextures.entrySet()) {
                    String matName = entry.getKey();
                    String texFile = entry.getValue();
                    File f = new File(texFile);

                    writer.write("newmtl " + matName + "\n");
                    writer.write("Ka 0.2 0.2 0.2\n");
                    writer.write("Kd 0.8 0.8 0.8\n");
                    writer.write("Ks 0.0 0.0 0.0\n");
                    writer.write("Ns 1.0\n");
                    writer.write("d 1.0\n");
                    writer.write("illum 2\n");
                    writer.write("map_Kd textures/" + f.getName() + "\n\n");
                }

                if (!materialTextures.containsKey("DefaultMaterial")) {
                    writer.write("newmtl DefaultMaterial\n");
                    writer.write("Ka 0.2 0.2 0.2\n");
                    writer.write("Kd 0.8 0.8 0.8\n");
                    writer.write("Ks 0.0 0.0 0.0\n");
                    writer.write("Ns 1.0\n");
                    writer.write("d 1.0\n");
                    writer.write("illum 2\n\n");
                }
            }

            writer.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String formatVertex(float x, float y, float z) {
        return String.format("v %.6f %.6f %.6f", x, y, z);
    }

    public String formatNormal(float x, float y, float z) {
        return String.format("vn %.6f %.6f %.6f", x, y, z);
    }

    public String formatUV(float u, float v) {
        return String.format("vt %.6f %.6f", u, v);
    }

    public String formatFace(int v1, int vt1, int vn1, int v2, int vt2, int vn2, int v3, int vt3, int vn3) {
        return String.format("f %d/%d/%d %d/%d/%d %d/%d/%d",
                v1, vt1, vn1, v2, vt2, vn2, v3, vt3, vn3);
    }

    public boolean exportTextures(Scene scene, String outputDir) {
        try {
            File texDir = new File(outputDir, "textures");
            if (!texDir.exists()) {
                texDir.mkdirs();
            }

            List<Block3D> blocks = scene.getBlocks();
            List<String> exportedTextures = new ArrayList<>();

            for (Block3D block : blocks) {
                String textureName = block.getTextureName();
                if (textureName == null || textureName.isEmpty()) {
                    continue;
                }
                if (exportedTextures.contains(textureName)) {
                    continue;
                }
                exportedTextures.add(textureName);

                File srcFile = new File(textureName);
                if (srcFile.exists()) {
                    File destFile = new File(texDir, srcFile.getName());
                    copyFileInternal(srcFile, destFile);
                } else {
                    String assetPath = "textures/" + textureName;
                    try (java.io.InputStream is = context.getAssets().open(assetPath)) {
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

    public CubeData generateCubeVertices(Block3D block) {
        CubeData cube = new CubeData();
        float x = block.getX();
        float y = block.getY();
        float z = block.getZ();
        float s = 1.0f;

        cube.vertices = new float[]{
            x, y, z + s,       x + s, y, z + s,       x + s, y + s, z + s,       x, y + s, z + s,
            x + s, y, z,       x, y, z,               x, y + s, z,               x + s, y + s, z,
            x, y + s, z + s,   x + s, y + s, z + s,   x + s, y + s, z,           x, y + s, z,
            x, y, z,           x + s, y, z,           x + s, y, z + s,           x, y, z + s,
            x + s, y, z + s,   x + s, y, z,           x + s, y + s, z,           x + s, y + s, z + s,
            x, y, z,           x, y, z + s,           x, y + s, z + s,           x, y + s, z
        };

        cube.normals = new float[]{
            0.0f, 0.0f, 1.0f,    0.0f, 0.0f, 1.0f,    0.0f, 0.0f, 1.0f,    0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, -1.0f,   0.0f, 0.0f, -1.0f,   0.0f, 0.0f, -1.0f,   0.0f, 0.0f, -1.0f,
            0.0f, 1.0f, 0.0f,    0.0f, 1.0f, 0.0f,    0.0f, 1.0f, 0.0f,    0.0f, 1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,   0.0f, -1.0f, 0.0f,   0.0f, -1.0f, 0.0f,   0.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 0.0f,    1.0f, 0.0f, 0.0f,    1.0f, 0.0f, 0.0f,    1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,   -1.0f, 0.0f, 0.0f,   -1.0f, 0.0f, 0.0f,   -1.0f, 0.0f, 0.0f
        };

        cube.uvs = new float[]{
            0.0f, 0.0f,   1.0f, 0.0f,   1.0f, 1.0f,   0.0f, 1.0f,
            0.0f, 0.0f,   1.0f, 0.0f,   1.0f, 1.0f,   0.0f, 1.0f,
            0.0f, 0.0f,   1.0f, 0.0f,   1.0f, 1.0f,   0.0f, 1.0f,
            0.0f, 0.0f,   1.0f, 0.0f,   1.0f, 1.0f,   0.0f, 1.0f,
            0.0f, 0.0f,   1.0f, 0.0f,   1.0f, 1.0f,   0.0f, 1.0f,
            0.0f, 0.0f,   1.0f, 0.0f,   1.0f, 1.0f,   0.0f, 1.0f
        };

        cube.indices = new int[]{
            0, 1, 2,     0, 2, 3,
            4, 5, 6,     4, 6, 7,
            8, 9, 10,    8, 10, 11,
            12, 13, 14,  12, 14, 15,
            16, 17, 18,  16, 18, 19,
            20, 21, 22,  20, 22, 23
        };

        return cube;
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

    private void copyStreamToFile(java.io.InputStream is, File dest) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }

    private String getBaseName(String path) {
        File file = new File(path);
        String name = file.getName();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            return name.substring(0, dotIdx);
        }
        return name;
    }

    private String sanitizeMaterialName(String name) {
        if (name == null) return "DefaultMaterial";
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isEmpty()) return "DefaultMaterial";
        return sanitized;
    }
}
