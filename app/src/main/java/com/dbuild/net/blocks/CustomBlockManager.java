package com.dbuild.net.blocks;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages CRUD operations for custom blocks.
 * Provides create, read, update, delete, duplicate, import/export,
 * and persistence operations through BlockRegistry and BlockSerializer.
 */
public class CustomBlockManager {

    private final Context context;
    private final BlockRegistry registry;
    private final BlockSerializer serializer;
    private final File blocksDir;

    /**
     * Constructs a CustomBlockManager with the given Android context.
     *
     * @param context the Android context for file operations
     */
    public CustomBlockManager(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        this.registry = BlockRegistry.getInstance();
        this.serializer = new BlockSerializer();
        this.blocksDir = new File(context.getFilesDir(), "custom_blocks");
        ensureBlocksDir();
    }

    /**
     * Ensures the blocks storage directory exists.
     */
    private void ensureBlocksDir() {
        if (!blocksDir.exists()) {
            blocksDir.mkdirs();
        }
    }

    // ========== Create ==========

    /**
     * Creates a new custom block with the given name and registers it.
     *
     * @param name the display name for the new block
     * @return the newly created CustomBlock
     */
    public CustomBlock createCustomBlock(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "Custom Block";
        }
        CustomBlock block = CustomBlock.createNew(name.trim());
        block.setCategory("custom");
        registry.registerBlock(block);
        saveBlock(block);
        return block;
    }

    // ========== Read ==========

    /**
     * Gets a custom block by its ID.
     *
     * @param blockId the block ID
     * @return the CustomBlock, or null if not found
     */
    public CustomBlock getCustomBlock(String blockId) {
        return registry.getBlock(blockId);
    }

    /**
     * Returns all custom blocks (those in the "custom" category).
     *
     * @return list of custom blocks
     */
    public List<CustomBlock> getAllCustomBlocks() {
        List<CustomBlock> allBlocks = registry.getAllBlocks();
        List<CustomBlock> customBlocks = new ArrayList<>();
        for (CustomBlock block : allBlocks) {
            if ("custom".equals(block.getCategory())) {
                customBlocks.add(block);
            }
        }
        return customBlocks;
    }

    // ========== Update ==========

    /**
     * Edits the name of an existing custom block.
     *
     * @param blockId the ID of the block to edit
     * @param newName the new display name
     * @return true if the block was found and updated
     */
    public boolean editCustomBlock(String blockId, String newName) {
        CustomBlock block = registry.getBlock(blockId);
        if (block == null) {
            return false;
        }
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        block.setBlockName(newName.trim());
        saveBlock(block);
        return true;
    }

    /**
     * Assigns a texture to a specific face of a block.
     *
     * @param blockId     the block ID
     * @param face        the face to assign the texture to
     * @param texturePath the path of the texture file
     * @return true if the assignment succeeded
     */
    public boolean assignTextureToFace(String blockId, CustomBlock.BlockFace face,
                                       String texturePath) {
        CustomBlock block = registry.getBlock(blockId);
        if (block == null) {
            return false;
        }
        if (face == null) {
            return false;
        }
        block.setFaceTexture(face, texturePath);
        saveBlock(block);
        return true;
    }

    /**
     * Removes the texture from a specific face of a block.
     *
     * @param blockId the block ID
     * @param face    the face to clear
     * @return true if the texture was removed
     */
    public boolean removeTextureFromFace(String blockId, CustomBlock.BlockFace face) {
        CustomBlock block = registry.getBlock(blockId);
        if (block == null) {
            return false;
        }
        if (face == null) {
            return false;
        }
        block.setFaceTexture(face, null);
        saveBlock(block);
        return true;
    }

    /**
     * Sets the transparency property of a block.
     *
     * @param blockId     the block ID
     * @param transparent whether the block is transparent
     * @return true if the block was found and updated
     */
    public boolean setBlockTransparency(String blockId, boolean transparent) {
        CustomBlock block = registry.getBlock(blockId);
        if (block == null) {
            return false;
        }
        block.setTransparent(transparent);
        saveBlock(block);
        return true;
    }

    /**
     * Sets the emissive properties of a block.
     *
     * @param blockId   the block ID
     * @param emissive  whether the block emits light
     * @param strength  the emissive strength (0.0 to 1.0+)
     * @return true if the block was found and updated
     */
    public boolean setBlockEmissive(String blockId, boolean emissive, float strength) {
        CustomBlock block = registry.getBlock(blockId);
        if (block == null) {
            return false;
        }
        block.setEmissive(emissive);
        block.setEmissiveStrength(strength);
        saveBlock(block);
        return true;
    }

    // ========== Delete ==========

    /**
     * Deletes a custom block by its ID.
     * Removes from registry and deletes the persisted file.
     *
     * @param blockId the ID of the block to delete
     * @return true if the block was found and deleted
     */
    public boolean deleteCustomBlock(String blockId) {
        CustomBlock block = registry.getBlock(blockId);
        if (block == null) {
            return false;
        }

        // Delete persisted file
        File blockFile = getBlockFile(blockId);
        if (blockFile.exists()) {
            blockFile.delete();
        }

        // Remove from registry
        registry.unregisterBlock(blockId);
        return true;
    }

    // ========== Duplicate ==========

    /**
     * Duplicates a custom block with a new name and new UUID.
     *
     * @param blockId the ID of the block to duplicate
     * @param newName the name for the duplicated block
     * @return the newly created duplicate, or null if the source wasn't found
     */
    public CustomBlock duplicateCustomBlock(String blockId, String newName) {
        CustomBlock source = registry.getBlock(blockId);
        if (source == null) {
            return null;
        }
        if (newName == null || newName.trim().isEmpty()) {
            newName = source.getBlockName() + " (Copy)";
        }

        try {
            JSONObject json = source.toJSON();
            CustomBlock duplicate = CustomBlock.fromJSON(json);
            duplicate.setBlockId(UUID.randomUUID().toString());
            duplicate.setBlockName(newName.trim());
            duplicate.setCreatedAt(System.currentTimeMillis());
            duplicate.setModifiedAt(System.currentTimeMillis());

            // Deep copy the texture set
            if (source.getTextureSet() != null) {
                duplicate.setTextureSet(source.getTextureSet().deepCopy());
            }

            registry.registerBlock(duplicate);
            saveBlock(duplicate);
            return duplicate;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== Persistence ==========

    /**
     * Saves a block to persistent storage.
     *
     * @param block the block to save
     * @return true if the save succeeded
     */
    public boolean saveBlock(CustomBlock block) {
        if (block == null) {
            return false;
        }
        ensureBlocksDir();
        File blockFile = getBlockFile(block.getBlockId());
        return serializer.saveBlockToFile(block, blockFile);
    }

    /**
     * Loads all custom blocks from persistent storage.
     *
     * @return the number of blocks loaded
     */
    public int loadAllBlocks() {
        ensureBlocksDir();
        File[] files = blocksDir.listFiles();
        if (files == null) {
            return 0;
        }

        int loadedCount = 0;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                try {
                    CustomBlock block = serializer.loadBlockFromFile(file);
                    if (block != null) {
                        registry.registerBlock(block);
                        loadedCount++;
                    }
                } catch (Exception e) {
                    // Skip corrupted files
                }
            }
        }
        return loadedCount;
    }

    /**
     * Gets the file handle for a given block ID.
     *
     * @param blockId the block ID
     * @return the File object for the block's JSON file
     */
    private File getBlockFile(String blockId) {
        return new File(blocksDir, blockId + ".json");
    }

    // ========== Export / Import ==========

    /**
     * Exports a block to an external file.
     *
     * @param blockId the ID of the block to export
     * @param dest    the destination file
     * @return true if the export succeeded
     */
    public boolean exportBlock(String blockId, File dest) {
        CustomBlock block = registry.getBlock(blockId);
        if (block == null) {
            return false;
        }
        if (dest == null) {
            return false;
        }

        try {
            JSONObject json = serializer.serializeBlock(block);
            String jsonString = json.toString(2);
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);

            File parentDir = dest.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            FileOutputStream fos = new FileOutputStream(dest);
            try {
                fos.write(bytes);
                fos.flush();
            } finally {
                fos.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Imports a block from an external file.
     *
     * @param source the source file to import from
     * @return the imported CustomBlock, or null if import failed
     */
    public CustomBlock importBlock(File source) {
        if (source == null || !source.exists() || !source.canRead()) {
            return null;
        }

        try {
            CustomBlock block = serializer.loadBlockFromFile(source);
            if (block == null) {
                return null;
            }

            // Assign a new ID to avoid collisions with existing blocks
            String oldId = block.getBlockId();
            block.setBlockId(UUID.randomUUID().toString());
            block.setModifiedAt(System.currentTimeMillis());

            registry.registerBlock(block);
            saveBlock(block);
            return block;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads a file's contents as a UTF-8 string.
     *
     * @param file the file to read
     * @return the file contents as a string
     * @throws IOException if reading fails
     */
    private String readFileAsString(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] buffer = new byte[(int) file.length()];
            int bytesRead = fis.read(buffer);
            return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        } finally {
            fis.close();
        }
    }

    /**
     * Gets the total number of custom blocks.
     *
     * @return count of custom blocks
     */
    public int getCustomBlockCount() {
        return getAllCustomBlocks().size();
    }

    /**
     * Gets the Android context.
     *
     * @return the context
     */
    public Context getContext() {
        return context;
    }

    /**
     * Gets the block registry.
     *
     * @return the BlockRegistry instance
     */
    public BlockRegistry getRegistry() {
        return registry;
    }
}
