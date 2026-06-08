package com.dbuild.net.export;

import android.content.Context;

import com.dbuild.net.model.Block3D;
import com.dbuild.net.model.Scene;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class M3XExporter {

    public static final int M3X_MAGIC = 0x4D335820;
    public static final int M3X_VERSION = 1;

    private static final int SECTION_SCENE = 1;
    private static final int SECTION_BLOCKS = 2;
    private static final int SECTION_TEXTURES = 3;
    private static final int SECTION_UV = 4;
    private static final int SECTION_CAMERA = 5;
    private static final int SECTION_SETTINGS = 6;

    private final Context context;
    private ProgressCallback progressCallback;

    public M3XExporter(Context context) {
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
        public boolean compressData = true;

        public ExportOptions() {}

        public ExportOptions(boolean includeTextures, boolean includeMaterials, float scale) {
            this.includeTextures = includeTextures;
            this.includeMaterials = includeMaterials;
            this.scale = scale;
        }
    }

    public static class M3XInfo {
        public String name;
        public int version;
        public int blockCount;
        public long size;
    }

    public static class IndexEntry {
        int sectionType;
        long offset;
        int compressedSize;
        int uncompressedSize;
    }

    public boolean export(Scene scene, String outputPath, ExportOptions options) {
        try {
            if (progressCallback != null) {
                progressCallback.onProgress(0, "Starting M3X export...");
            }

            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            if (progressCallback != null) {
                progressCallback.onProgress(20, "Building M3X data...");
            }

            byte[] m3xData = buildM3XData(scene);
            if (m3xData == null) {
                if (progressCallback != null) {
                    progressCallback.onError("Failed to build M3X data");
                }
                return false;
            }

            if (progressCallback != null) {
                progressCallback.onProgress(80, "Writing M3X file...");
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(m3xData);
                fos.flush();
            }

            if (progressCallback != null) {
                progressCallback.onProgress(100, "M3X export completed");
                progressCallback.onCompleted(outputPath);
            }

            return true;
        } catch (Exception e) {
            if (progressCallback != null) {
                progressCallback.onError("M3X export failed: " + e.getMessage());
            }
            return false;
        }
    }

    public byte[] buildM3XData(Scene scene) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            List<IndexEntry> indexEntries = new ArrayList<>();
            List<byte[]> sections = new ArrayList<>();

            byte[] sceneData = buildSceneSection(scene);
            byte[] compressedScene = compressData(sceneData);
            IndexEntry sceneEntry = new IndexEntry();
            sceneEntry.sectionType = SECTION_SCENE;
            sceneEntry.compressedSize = compressedScene.length;
            sceneEntry.uncompressedSize = sceneData.length;
            indexEntries.add(sceneEntry);
            sections.add(compressedScene);

            byte[] blockData = buildBlockSection(scene);
            byte[] compressedBlocks = compressData(blockData);
            IndexEntry blockEntry = new IndexEntry();
            blockEntry.sectionType = SECTION_BLOCKS;
            blockEntry.compressedSize = compressedBlocks.length;
            blockEntry.uncompressedSize = blockData.length;
            indexEntries.add(blockEntry);
            sections.add(compressedBlocks);

            byte[] textureData = buildTextureSection(scene);
            IndexEntry textureEntry = new IndexEntry();
            textureEntry.sectionType = SECTION_TEXTURES;
            if (textureData != null && textureData.length > 0) {
                byte[] compressedTextures = compressData(textureData);
                textureEntry.compressedSize = compressedTextures.length;
                textureEntry.uncompressedSize = textureData.length;
                sections.add(compressedTextures);
            } else {
                textureEntry.compressedSize = 0;
                textureEntry.uncompressedSize = 0;
                sections.add(new byte[0]);
            }
            indexEntries.add(textureEntry);

            byte[] uvData = buildUVSection(scene);
            byte[] compressedUV = compressData(uvData);
            IndexEntry uvEntry = new IndexEntry();
            uvEntry.sectionType = SECTION_UV;
            uvEntry.compressedSize = compressedUV.length;
            uvEntry.uncompressedSize = uvData.length;
            indexEntries.add(uvEntry);
            sections.add(compressedUV);

            byte[] cameraData = buildCameraSection(scene);
            byte[] compressedCamera = compressData(cameraData);
            IndexEntry cameraEntry = new IndexEntry();
            cameraEntry.sectionType = SECTION_CAMERA;
            cameraEntry.compressedSize = compressedCamera.length;
            cameraEntry.uncompressedSize = cameraData.length;
            indexEntries.add(cameraEntry);
            sections.add(compressedCamera);

            byte[] settingsData = buildSettingsSection(scene);
            byte[] compressedSettings = compressData(settingsData);
            IndexEntry settingsEntry = new IndexEntry();
            settingsEntry.sectionType = SECTION_SETTINGS;
            settingsEntry.compressedSize = compressedSettings.length;
            settingsEntry.uncompressedSize = settingsData.length;
            indexEntries.add(settingsEntry);
            sections.add(compressedSettings);

            int headerSize = 16;
            int sectionDataSize = 0;
            for (byte[] section : sections) {
                sectionDataSize += 12 + section.length;
            }
            int indexTableSize = 4 + indexEntries.size() * 20;
            int totalSize = headerSize + sectionDataSize + indexTableSize + 8;

            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.putInt(M3X_MAGIC);
            buffer.putInt(M3X_VERSION);
            buffer.putInt(0);
            buffer.putInt(headerSize + sectionDataSize);

            long currentOffset = headerSize;
            for (int i = 0; i < sections.size(); i++) {
                byte[] section = sections.get(i);
                indexEntries.get(i).offset = currentOffset;

                buffer.putInt(indexEntries.get(i).sectionType);
                buffer.putInt(indexEntries.get(i).compressedSize);
                buffer.putInt(indexEntries.get(i).uncompressedSize);
                buffer.put(section);

                currentOffset += 12 + section.length;
            }

            buffer.putInt(indexEntries.size());
            for (IndexEntry entry : indexEntries) {
                buffer.putInt(entry.sectionType);
                buffer.putLong(entry.offset);
                buffer.putInt(entry.compressedSize);
                buffer.putInt(entry.uncompressedSize);
            }

            byte[] writtenData = new byte[buffer.position()];
            buffer.rewind();
            buffer.get(writtenData);

            long checksum = calculateChecksum(writtenData);
            buffer.putLong(checksum);

            byte[] result = new byte[buffer.position()];
            buffer.rewind();
            buffer.get(result);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] buildSceneSection(Scene scene) {
        try {
            JSONObject sceneJson = new JSONObject();
            sceneJson.put("name", scene.getName() != null ? scene.getName() : "Untitled");
            sceneJson.put("blockCount", scene.getBlockCount());
            sceneJson.put("materialCount", scene.getMaterialCount());
            return sceneJson.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private byte[] buildBlockSection(Scene scene) {
        try {
            List<Block3D> blocks = scene.getBlocks();
            JSONArray blocksArray = new JSONArray();
            for (Block3D block : blocks) {
                JSONObject blockObj = new JSONObject();
                blockObj.put("x", block.getX());
                blockObj.put("y", block.getY());
                blockObj.put("z", block.getZ());

                JSONArray colors = new JSONArray();
                float[] blockColors = block.getColors();
                if (blockColors != null) {
                    for (float c : blockColors) {
                        colors.put(c);
                    }
                }
                blockObj.put("colors", colors);

                String textureName = block.getTextureName();
                blockObj.put("texture", textureName != null ? textureName : "");
                blocksArray.put(blockObj);
            }
            return blocksArray.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private byte[] buildTextureSection(Scene scene) {
        try {
            List<String> textures = scene.getTextures();
            if (textures == null || textures.isEmpty()) {
                return new byte[0];
            }
            JSONObject texObj = new JSONObject();
            JSONArray texArray = new JSONArray();
            for (String tex : textures) {
                if (tex != null && !tex.isEmpty()) {
                    File texFile = new File(tex);
                    if (texFile.exists()) {
                        byte[] fileBytes = readFileBytes(texFile);
                        JSONObject texEntry = new JSONObject();
                        texEntry.put("name", texFile.getName());
                        texEntry.put("size", fileBytes.length);
                        texEntry.put("data", android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP));
                        texArray.put(texEntry);
                    } else {
                        JSONObject texEntry = new JSONObject();
                        texEntry.put("name", tex);
                        texEntry.put("size", 0);
                        texEntry.put("data", "");
                        texArray.put(texEntry);
                    }
                }
            }
            texObj.put("textures", texArray);
            return texObj.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private byte[] buildUVSection(Scene scene) {
        try {
            List<Block3D> blocks = scene.getBlocks();
            JSONArray uvArray = new JSONArray();
            for (Block3D block : blocks) {
                JSONObject uvObj = new JSONObject();
                uvObj.put("x", block.getX());
                uvObj.put("y", block.getY());
                uvObj.put("z", block.getZ());

                JSONArray faceUVs = new JSONArray();
                for (int face = 0; face < 6; face++) {
                    JSONObject faceUV = new JSONObject();
                    faceUV.put("u", 0.0f);
                    faceUV.put("v", 0.0f);
                    faceUV.put("uScale", 1.0f);
                    faceUV.put("vScale", 1.0f);
                    faceUVs.put(faceUV);
                }
                uvObj.put("faceUVs", faceUVs);
                uvArray.put(uvObj);
            }
            return uvArray.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private byte[] buildCameraSection(Scene scene) {
        try {
            JSONObject cameraJson = new JSONObject();
            cameraJson.put("positionX", 0.0f);
            cameraJson.put("positionY", 5.0f);
            cameraJson.put("positionZ", 10.0f);
            cameraJson.put("rotationX", -30.0f);
            cameraJson.put("rotationY", 0.0f);
            cameraJson.put("fov", 60.0f);
            cameraJson.put("near", 0.1f);
            cameraJson.put("far", 1000.0f);
            return cameraJson.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private byte[] buildSettingsSection(Scene scene) {
        try {
            JSONObject settingsJson = new JSONObject();
            settingsJson.put("gridEnabled", true);
            settingsJson.put("gridSize", 16);
            settingsJson.put("snapEnabled", true);
            settingsJson.put("renderMode", "solid");
            settingsJson.put("showAxes", true);
            return settingsJson.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public byte[] compressData(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(data);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return data;
        }
    }

    public byte[] decompressData(byte[] compressedData) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(compressedData))) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = gzip.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public long calculateChecksum(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return crc32.getValue();
    }

    public boolean validateM3X(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[16];
            int read = fis.read(header);
            if (read < 16) {
                return false;
            }
            ByteBuffer buffer = ByteBuffer.wrap(header);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            int magic = buffer.getInt();
            if (magic != M3X_MAGIC) {
                return false;
            }

            int version = buffer.getInt();
            if (version != M3X_VERSION) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public M3XInfo getM3XInfo(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[16];
            int read = fis.read(header);
            if (read < 16) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(header);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            int magic = buffer.getInt();
            if (magic != M3X_MAGIC) {
                return null;
            }

            M3XInfo info = new M3XInfo();
            info.version = buffer.getInt();
            info.size = file.length();

            int flags = buffer.getInt();
            int offsetTablePos = buffer.getInt();

            fis.getChannel().position(offsetTablePos);
            byte[] indexHeaderBytes = new byte[4];
            read = fis.read(indexHeaderBytes);
            if (read < 4) {
                return info;
            }
            ByteBuffer idxBuf = ByteBuffer.wrap(indexHeaderBytes);
            idxBuf.order(ByteOrder.LITTLE_ENDIAN);
            int entryCount = idxBuf.getInt();

            info.blockCount = 0;
            for (int i = 0; i < entryCount; i++) {
                byte[] entryBytes = new byte[20];
                read = fis.read(entryBytes);
                if (read < 20) break;
                ByteBuffer entryBuf = ByteBuffer.wrap(entryBytes);
                entryBuf.order(ByteOrder.LITTLE_ENDIAN);
                int sectionType = entryBuf.getInt();
                if (sectionType == SECTION_SCENE) {
                    long offset = entryBuf.getLong();
                    int compSize = entryBuf.getInt();
                    int uncompSize = entryBuf.getInt();

                    fis.getChannel().position(offset + 4);
                    byte[] sceneSectionHeader = new byte[8];
                    fis.read(sceneSectionHeader);
                    ByteBuffer secBuf = ByteBuffer.wrap(sceneSectionHeader);
                    secBuf.order(ByteOrder.LITTLE_ENDIAN);
                    int cSize = secBuf.getInt();
                    int uSize = secBuf.getInt();

                    byte[] compressedScene = new byte[cSize];
                    fis.read(compressedScene);
                    byte[] sceneBytes = decompressData(compressedScene);
                    if (sceneBytes != null) {
                        JSONObject sceneJson = new JSONObject(new String(sceneBytes, "UTF-8"));
                        info.name = sceneJson.optString("name", "Unknown");
                        info.blockCount = sceneJson.optInt("blockCount", 0);
                    }
                    break;
                }
            }

            if (info.name == null) {
                info.name = "Unknown";
            }

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readFileBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            int remaining = data.length;
            while (remaining > 0) {
                int read = fis.read(data, offset, remaining);
                if (read == -1) break;
                offset += read;
                remaining -= read;
            }
            return data;
        }
    }
}
