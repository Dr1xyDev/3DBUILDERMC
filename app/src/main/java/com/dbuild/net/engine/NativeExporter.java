package com.dbuild.net.engine;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * Wrapper for native export operations.
 * Supports GLB, GLTF, OBJ, and M3X formats.
 * Falls back to Java implementations when native library is unavailable.
 */
public class NativeExporter {

    private static final String TAG = "NativeExporter";

    /** Supported export format constants */
    public static final String FORMAT_GLB = "GLB";
    public static final String FORMAT_GLTF = "GLTF";
    public static final String FORMAT_OBJ = "OBJ";
    public static final String FORMAT_M3X = "M3X";

    private final NativeModel model;
    private final RustBridge rustBridge;

    /**
     * Creates a new NativeExporter for the given model.
     * @param model The model to export
     */
    public NativeExporter(NativeModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must not be null");
        }
        this.model = model;
        this.rustBridge = new RustBridge();
    }

    /**
     * Exports the model to GLB (GL Binary) format.
     * @param outputPath Output file path
     * @return true if export succeeded
     */
    public boolean exportGLB(String outputPath) {
        if (!model.isValid()) {
            Log.e(TAG, "Cannot export: model is not valid");
            return false;
        }
        if (outputPath == null || outputPath.isEmpty()) {
            Log.e(TAG, "Cannot export: output path is null or empty");
            return false;
        }

        try {
            boolean result = rustBridge.exportGLB(model.getModelPtr(), outputPath);
            if (result) {
                Log.i(TAG, "GLB export successful: " + outputPath);
            } else {
                Log.w(TAG, "GLB export failed: " + outputPath);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Exception during GLB export", e);
            return false;
        }
    }

    /**
     * Exports the model to GLTF (GL Transmission Format) format.
     * @param outputPath Output file path
     * @return true if export succeeded
     */
    public boolean exportGLTF(String outputPath) {
        if (!model.isValid()) {
            Log.e(TAG, "Cannot export: model is not valid");
            return false;
        }
        if (outputPath == null || outputPath.isEmpty()) {
            Log.e(TAG, "Cannot export: output path is null or empty");
            return false;
        }

        try {
            boolean result = rustBridge.exportGLTF(model.getModelPtr(), outputPath);
            if (result) {
                Log.i(TAG, "GLTF export successful: " + outputPath);
            } else {
                Log.w(TAG, "GLTF export failed: " + outputPath);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Exception during GLTF export", e);
            return false;
        }
    }

    /**
     * Exports the model to OBJ (Wavefront) format.
     * @param outputPath Output file path
     * @return true if export succeeded
     */
    public boolean exportOBJ(String outputPath) {
        if (!model.isValid()) {
            Log.e(TAG, "Cannot export: model is not valid");
            return false;
        }
        if (outputPath == null || outputPath.isEmpty()) {
            Log.e(TAG, "Cannot export: output path is null or empty");
            return false;
        }

        try {
            boolean result = rustBridge.exportOBJ(model.getModelPtr(), outputPath);
            if (result) {
                Log.i(TAG, "OBJ export successful: " + outputPath);
            } else {
                Log.w(TAG, "OBJ export failed, attempting Java fallback");
                result = exportOBJFallback(outputPath);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Exception during OBJ export, attempting fallback", e);
            return exportOBJFallback(outputPath);
        }
    }

    /**
     * Exports the model to M3X (custom Minecraft 3D) format.
     * @param outputPath Output file path
     * @return true if export succeeded
     */
    public boolean exportM3X(String outputPath) {
        if (!model.isValid()) {
            Log.e(TAG, "Cannot export: model is not valid");
            return false;
        }
        if (outputPath == null || outputPath.isEmpty()) {
            Log.e(TAG, "Cannot export: output path is null or empty");
            return false;
        }

        try {
            boolean result = rustBridge.exportM3X(model.getModelPtr(), outputPath);
            if (result) {
                Log.i(TAG, "M3X export successful: " + outputPath);
            } else {
                Log.w(TAG, "M3X export failed: " + outputPath);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Exception during M3X export", e);
            return false;
        }
    }

    /**
     * Returns the list of supported export formats.
     * @return Array of format name strings
     */
    public String[] getSupportedFormats() {
        return new String[]{FORMAT_GLB, FORMAT_GLTF, FORMAT_OBJ, FORMAT_M3X};
    }

    /**
     * Returns the file extension for a given format.
     * @param format Format name (e.g. "GLB")
     * @return File extension (e.g. ".glb")
     */
    public String getFileExtensionForFormat(String format) {
        if (format == null) return "";
        switch (format.toUpperCase(Locale.US)) {
            case FORMAT_GLB:
                return ".glb";
            case FORMAT_GLTF:
                return ".gltf";
            case FORMAT_OBJ:
                return ".obj";
            case FORMAT_M3X:
                return ".m3x";
            default:
                return "";
        }
    }

    /**
     * Returns the MIME type for a given format.
     * @param format Format name (e.g. "GLB")
     * @return MIME type string
     */
    public String getMimeTypeForFormat(String format) {
        if (format == null) return "application/octet-stream";
        switch (format.toUpperCase(Locale.US)) {
            case FORMAT_GLB:
                return "model/gltf-binary";
            case FORMAT_GLTF:
                return "model/gltf+json";
            case FORMAT_OBJ:
                return "text/plain";
            case FORMAT_M3X:
                return "application/json";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * Estimates the file size for a given format based on block count.
     * This is a rough estimate for UI purposes (e.g. showing estimated size before export).
     * @param format Format name
     * @return Estimated file size in bytes
     */
    public long estimateFileSize(String format) {
        int blockCount = model.getBlockCount();
        if (blockCount == 0) return 0;

        if (format == null) return 0;

        // Rough estimates based on typical data:
        // Each block contributes 24 vertices * 3 floats = 72 floats for positions
        // Plus normals, UVs, indices, and format overhead

        switch (format.toUpperCase(Locale.US)) {
            case FORMAT_GLB: {
                // GLB is binary and compact: ~200 bytes per block + header
                long headerSize = 512; // JSON header + binary header
                long perBlock = 200;
                return headerSize + blockCount * perBlock;
            }
            case FORMAT_GLTF: {
                // GLTF has JSON overhead, larger than GLB
                long headerSize = 2048;
                long perBlock = 350;
                return headerSize + blockCount * perBlock;
            }
            case FORMAT_OBJ: {
                // OBJ is text-based and verbose
                // v x y z\n = ~30 chars per vertex
                // 24 vertices per block
                long perVertex = 30;
                long perNormal = 32;
                long perUV = 24;
                long perFace = 48;
                int vertsPerBlock = 24;
                int facesPerBlock = 12;
                long perBlock = vertsPerBlock * (perVertex + perNormal + perUV) + facesPerBlock * perFace;
                return 256 + blockCount * perBlock;
            }
            case FORMAT_M3X: {
                // M3X is JSON-based, moderate size
                long headerSize = 512;
                long perBlock = 250;
                return headerSize + blockCount * perBlock;
            }
            default:
                return 0;
        }
    }

    /**
     * Checks if a specific format is supported.
     * @param format Format name
     * @return true if the format is supported
     */
    public boolean isFormatSupported(String format) {
        if (format == null) return false;
        String[] supported = getSupportedFormats();
        for (String s : supported) {
            if (s.equalsIgnoreCase(format)) return true;
        }
        return false;
    }

    /**
     * Exports to the format determined by the file extension in the output path.
     * @param outputPath Output file path with extension
     * @return true if export succeeded
     */
    public boolean exportAuto(String outputPath) {
        if (outputPath == null || outputPath.isEmpty()) return false;

        String lower = outputPath.toLowerCase(Locale.US);
        if (lower.endsWith(".glb")) return exportGLB(outputPath);
        if (lower.endsWith(".gltf")) return exportGLTF(outputPath);
        if (lower.endsWith(".obj")) return exportOBJ(outputPath);
        if (lower.endsWith(".m3x")) return exportM3X(outputPath);

        Log.e(TAG, "Unknown file extension in: " + outputPath);
        return false;
    }

    /**
     * Java fallback for OBJ export when native is unavailable.
     * Generates a basic OBJ file from the model's mesh data.
     */
    private boolean exportOBJFallback(String outputPath) {
        try {
            // Ensure mesh is built
            model.buildMesh();

            float[] vertices = model.getVertices();
            float[] normals = model.getNormals();
            float[] uvs = model.getUVs();
            int[] indices = model.getIndices();

            if (vertices == null || vertices.length == 0) {
                Log.w(TAG, "No vertex data to export");
                return false;
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));

            try {
                writer.write("# 3D Builder MC OBJ Export\n");
                writer.write("# Generated by NativeExporter Java fallback\n");
                writer.write("# Blocks: " + model.getBlockCount() + "\n\n");
                writer.write("o MinecraftModel\n\n");

                // Write vertices
                for (int i = 0; i < vertices.length; i += 3) {
                    writer.write(String.format(Locale.US, "v %.6f %.6f %.6f\n",
                            vertices[i], vertices[i + 1], vertices[i + 2]));
                }
                writer.write("\n");

                // Write normals
                if (normals != null && normals.length > 0) {
                    for (int i = 0; i < normals.length; i += 3) {
                        writer.write(String.format(Locale.US, "vn %.6f %.6f %.6f\n",
                                normals[i], normals[i + 1], normals[i + 2]));
                    }
                    writer.write("\n");
                }

                // Write UVs
                if (uvs != null && uvs.length > 0) {
                    for (int i = 0; i < uvs.length; i += 2) {
                        writer.write(String.format(Locale.US, "vt %.6f %.6f\n",
                                uvs[i], uvs[i + 1]));
                    }
                    writer.write("\n");
                }

                // Write faces
                if (indices != null && indices.length > 0) {
                    boolean hasNormals = normals != null && normals.length > 0;
                    boolean hasUVs = uvs != null && uvs.length > 0;

                    for (int i = 0; i < indices.length; i += 3) {
                        int i0 = indices[i] + 1;  // OBJ is 1-indexed
                        int i1 = indices[i + 1] + 1;
                        int i2 = indices[i + 2] + 1;

                        if (hasNormals && hasUVs) {
                            writer.write(String.format(Locale.US, "f %d/%d/%d %d/%d/%d %d/%d/%d\n",
                                    i0, i0, i0, i1, i1, i1, i2, i2, i2));
                        } else if (hasUVs) {
                            writer.write(String.format(Locale.US, "f %d/%d %d/%d %d/%d\n",
                                    i0, i0, i1, i1, i2, i2));
                        } else if (hasNormals) {
                            writer.write(String.format(Locale.US, "f %d//%d %d//%d %d//%d\n",
                                    i0, i0, i1, i1, i2, i2));
                        } else {
                            writer.write(String.format(Locale.US, "f %d %d %d\n", i0, i1, i2));
                        }
                    }
                }

                writer.write("\n# End of file\n");
                Log.i(TAG, "OBJ fallback export successful: " + outputPath);
                return true;

            } finally {
                writer.close();
            }

        } catch (IOException e) {
            Log.e(TAG, "OBJ fallback export I/O error", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "OBJ fallback export error", e);
            return false;
        }
    }
}
