package com.dbuild.net.blocks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles serialization and deserialization of CustomBlock objects
 * to and from JSON format. Provides methods for single blocks,
 * block lists, and file-based persistence.
 */
public class BlockSerializer {

    private static final String JSON_KEY_BLOCK_LIST = "blocks";
    private static final String JSON_KEY_VERSION = "version";
    private static final String JSON_KEY_COUNT = "count";
    private static final int CURRENT_VERSION = 1;

    /**
     * Constructs a BlockSerializer.
     */
    public BlockSerializer() {
    }

    // ========== Single Block Serialization ==========

    /**
     * Serializes a CustomBlock to a JSONObject.
     *
     * @param block the block to serialize
     * @return JSONObject representation of the block
     * @throws IllegalArgumentException if block is null
     * @throws RuntimeException         if serialization fails
     */
    public JSONObject serializeBlock(CustomBlock block) {
        if (block == null) {
            throw new IllegalArgumentException("Cannot serialize null block");
        }
        try {
            return block.toJSON();
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize block: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes a CustomBlock from a JSONObject.
     * Validates the JSON structure before parsing.
     *
     * @param json the JSONObject to parse
     * @return the deserialized CustomBlock
     * @throws IllegalArgumentException if json is null or invalid
     * @throws RuntimeException         if deserialization fails
     */
    public CustomBlock deserializeBlock(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("Cannot deserialize null JSON");
        }

        // Validate minimum required fields
        if (!validateBlockJSON(json)) {
            throw new IllegalArgumentException("Invalid block JSON structure");
        }

        try {
            return CustomBlock.fromJSON(json);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to deserialize block: " + e.getMessage(), e);
        }
    }

    // ========== Block List Serialization ==========

    /**
     * Serializes a list of CustomBlock objects to a JSONArray.
     *
     * @param blocks the list of blocks to serialize
     * @return JSONArray containing all block JSON representations
     */
    public JSONArray serializeBlockList(List<CustomBlock> blocks) {
        if (blocks == null) {
            return new JSONArray();
        }

        JSONArray array = new JSONArray();
        for (CustomBlock block : blocks) {
            if (block != null) {
                try {
                    array.put(block.toJSON());
                } catch (JSONException e) {
                    // Skip blocks that fail to serialize
                }
            }
        }
        return array;
    }

    /**
     * Deserializes a list of CustomBlock objects from a JSONArray.
     * Skips any entries that fail validation or parsing.
     *
     * @param json the JSONArray to parse
     * @return list of successfully deserialized CustomBlock objects
     */
    public List<CustomBlock> deserializeBlockList(JSONArray json) {
        List<CustomBlock> blocks = new ArrayList<>();
        if (json == null) {
            return blocks;
        }

        for (int i = 0; i < json.length(); i++) {
            try {
                JSONObject blockJson = json.getJSONObject(i);
                if (validateBlockJSON(blockJson)) {
                    CustomBlock block = CustomBlock.fromJSON(blockJson);
                    blocks.add(block);
                }
            } catch (JSONException e) {
                // Skip malformed entries
            }
        }
        return blocks;
    }

    // ========== File-Based Persistence ==========

    /**
     * Saves a CustomBlock to a file as JSON.
     *
     * @param block the block to save
     * @param file  the destination file
     * @return true if the save succeeded
     */
    public boolean saveBlockToFile(CustomBlock block, File file) {
        if (block == null || file == null) {
            return false;
        }

        FileOutputStream fos = null;
        try {
            JSONObject json = block.toJSON();
            String jsonString = json.toString(2);
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Loads a CustomBlock from a JSON file.
     *
     * @param file the source file
     * @return the deserialized CustomBlock, or null if loading failed
     */
    public CustomBlock loadBlockFromFile(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return null;
        }

        String jsonString = readFileAsString(file);
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(jsonString);
            if (!validateBlockJSON(json)) {
                return null;
            }
            return CustomBlock.fromJSON(json);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Saves a list of blocks to a directory, one file per block.
     * Each file is named {blockId}.json.
     *
     * @param blocks the list of blocks to save
     * @param dir    the destination directory
     * @return true if all blocks were saved successfully
     */
    public boolean saveAllBlocks(List<CustomBlock> blocks, File dir) {
        if (blocks == null || dir == null) {
            return false;
        }

        if (!dir.exists()) {
            dir.mkdirs();
        }
        if (!dir.isDirectory()) {
            return false;
        }

        boolean allSuccess = true;
        for (CustomBlock block : blocks) {
            if (block != null) {
                File blockFile = new File(dir, block.getBlockId() + ".json");
                if (!saveBlockToFile(block, blockFile)) {
                    allSuccess = false;
                }
            }
        }
        return allSuccess;
    }

    /**
     * Loads all blocks from JSON files in a directory.
     *
     * @param dir the source directory
     * @return list of loaded CustomBlock objects
     */
    public List<CustomBlock> loadAllBlocksFromDir(File dir) {
        List<CustomBlock> blocks = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return blocks;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return blocks;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                CustomBlock block = loadBlockFromFile(file);
                if (block != null) {
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    /**
     * Saves a block list as a single JSON file with metadata.
     *
     * @param blocks the list of blocks to save
     * @param file   the destination file
     * @return true if the save succeeded
     */
    public boolean saveBlockListToFile(List<CustomBlock> blocks, File file) {
        if (blocks == null || file == null) {
            return false;
        }

        FileOutputStream fos = null;
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put(JSON_KEY_VERSION, CURRENT_VERSION);
            wrapper.put(JSON_KEY_COUNT, blocks.size());
            wrapper.put(JSON_KEY_BLOCK_LIST, serializeBlockList(blocks));

            String jsonString = wrapper.toString(2);
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Loads a block list from a single JSON file with metadata.
     *
     * @param file the source file
     * @return list of loaded CustomBlock objects
     */
    public List<CustomBlock> loadBlockListFromFile(File file) {
        List<CustomBlock> blocks = new ArrayList<>();
        if (file == null || !file.exists() || !file.canRead()) {
            return blocks;
        }

        String jsonString = readFileAsString(file);
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return blocks;
        }

        try {
            JSONObject wrapper = new JSONObject(jsonString);
            JSONArray blockArray = wrapper.optJSONArray(JSON_KEY_BLOCK_LIST);
            if (blockArray != null) {
                return deserializeBlockList(blockArray);
            }
        } catch (JSONException e) {
            // Maybe it's a single block file, try parsing as such
            try {
                JSONObject singleBlock = new JSONObject(jsonString);
                if (validateBlockJSON(singleBlock)) {
                    CustomBlock block = CustomBlock.fromJSON(singleBlock);
                    blocks.add(block);
                }
            } catch (JSONException e2) {
                // Give up
            }
        }
        return blocks;
    }

    // ========== Validation ==========

    /**
     * Validates that a JSONObject has the minimum required fields
     * for a CustomBlock.
     *
     * @param json the JSON to validate
     * @return true if the JSON structure is valid
     */
    public boolean validateBlockJSON(JSONObject json) {
        if (json == null) {
            return false;
        }

        // blockId must exist and be non-empty
        String blockId = json.optString("blockId", "");
        if (blockId.isEmpty()) {
            return false;
        }

        // blockName should exist
        if (!json.has("blockName")) {
            return false;
        }

        // customColor must have 4 elements if present
        JSONArray colorArray = json.optJSONArray("customColor");
        if (colorArray != null && colorArray.length() != 4) {
            return false;
        }

        return true;
    }

    // ========== Utility ==========

    /**
     * Reads a file's entire contents as a UTF-8 string.
     *
     * @param file the file to read
     * @return the file contents, or null on error
     */
    private String readFileAsString(File file) {
        FileInputStream fis = null;
        BufferedReader reader = null;
        try {
            fis = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
