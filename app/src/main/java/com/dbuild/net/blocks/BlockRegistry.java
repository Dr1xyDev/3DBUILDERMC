package com.dbuild.net.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Singleton registry of all available block types.
 * Analogous to Minecraft's block registry, it maintains a central
 * map of all registered blocks, including default Minecraft-like blocks.
 */
public class BlockRegistry {

    private static volatile BlockRegistry instance;

    private final Map<String, CustomBlock> registeredBlocks;
    private boolean defaultsInitialized;

    /**
     * Private constructor for singleton pattern.
     */
    private BlockRegistry() {
        registeredBlocks = new LinkedHashMap<>();
        defaultsInitialized = false;
    }

    /**
     * Returns the singleton instance of BlockRegistry.
     * Uses double-checked locking for thread safety.
     *
     * @return the BlockRegistry instance
     */
    public static BlockRegistry getInstance() {
        if (instance == null) {
            synchronized (BlockRegistry.class) {
                if (instance == null) {
                    instance = new BlockRegistry();
                }
            }
        }
        return instance;
    }

    // ========== Registration Methods ==========

    /**
     * Registers a block in the registry.
     * If a block with the same ID already exists, it will be replaced.
     *
     * @param block the CustomBlock to register
     * @throws IllegalArgumentException if block is null or has no ID
     */
    public void registerBlock(CustomBlock block) {
        if (block == null) {
            throw new IllegalArgumentException("Cannot register null block");
        }
        if (block.getBlockId() == null || block.getBlockId().isEmpty()) {
            throw new IllegalArgumentException("Block must have a valid ID");
        }
        synchronized (registeredBlocks) {
            registeredBlocks.put(block.getBlockId(), block);
        }
    }

    /**
     * Unregisters a block by its ID.
     *
     * @param blockId the ID of the block to unregister
     * @return true if the block was found and removed, false otherwise
     */
    public boolean unregisterBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        synchronized (registeredBlocks) {
            return registeredBlocks.remove(blockId) != null;
        }
    }

    /**
     * Gets a registered block by its ID.
     *
     * @param blockId the ID of the block to retrieve
     * @return the CustomBlock, or null if not found
     */
    public CustomBlock getBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }
        synchronized (registeredBlocks) {
            return registeredBlocks.get(blockId);
        }
    }

    /**
     * Returns a list of all registered blocks.
     *
     * @return unmodifiable list of all CustomBlock instances
     */
    public List<CustomBlock> getAllBlocks() {
        synchronized (registeredBlocks) {
            return Collections.unmodifiableList(new ArrayList<>(registeredBlocks.values()));
        }
    }

    /**
     * Returns all blocks in a given category.
     *
     * @param category the category name to filter by
     * @return list of blocks in the specified category
     */
    public List<CustomBlock> getBlocksByCategory(String category) {
        if (category == null || category.isEmpty()) {
            return Collections.emptyList();
        }
        List<CustomBlock> result = new ArrayList<>();
        synchronized (registeredBlocks) {
            for (CustomBlock block : registeredBlocks.values()) {
                if (category.equalsIgnoreCase(block.getCategory())) {
                    result.add(block);
                }
            }
        }
        return result;
    }

    /**
     * Checks if a block with the given ID exists in the registry.
     *
     * @param blockId the ID to check
     * @return true if the block is registered
     */
    public boolean blockExists(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        synchronized (registeredBlocks) {
            return registeredBlocks.containsKey(blockId);
        }
    }

    /**
     * Returns the total number of registered blocks.
     *
     * @return the count of registered blocks
     */
    public int getBlockCount() {
        synchronized (registeredBlocks) {
            return registeredBlocks.size();
        }
    }

    /**
     * Searches for blocks whose name contains the query string (case-insensitive).
     *
     * @param query the search query
     * @return list of matching CustomBlock instances
     */
    public List<CustomBlock> searchBlocks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllBlocks();
        }
        String lowerQuery = query.toLowerCase(Locale.US);
        List<CustomBlock> result = new ArrayList<>();
        synchronized (registeredBlocks) {
            for (CustomBlock block : registeredBlocks.values()) {
                if (block.getBlockName().toLowerCase(Locale.US).contains(lowerQuery) ||
                        block.getBlockType().toLowerCase(Locale.US).contains(lowerQuery) ||
                        block.getCategory().toLowerCase(Locale.US).contains(lowerQuery)) {
                    result.add(block);
                }
            }
        }
        return result;
    }

    /**
     * Clears all blocks from the registry.
     */
    public void clearRegistry() {
        synchronized (registeredBlocks) {
            registeredBlocks.clear();
            defaultsInitialized = false;
        }
    }

    // ========== Default Blocks Initialization ==========

    /**
     * Initializes the registry with Minecraft-like default blocks.
     * Only initializes once; subsequent calls are no-ops unless the
     * registry has been cleared.
     */
    public void initializeDefaultBlocks() {
        synchronized (registeredBlocks) {
            if (defaultsInitialized) {
                return;
            }

            // === Natural Blocks ===
            registerBlock(createDefaultBlock("Grass", "natural", "grass",
                    new float[]{0.36f, 0.7f, 0.22f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/grass_top.png",
                    "textures/blocks/grass_side.png",
                    "textures/blocks/dirt.png"));

            registerBlock(createDefaultBlock("Dirt", "natural", "dirt",
                    new float[]{0.55f, 0.38f, 0.22f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/dirt.png", null, null));

            registerBlock(createDefaultBlock("Stone", "natural", "stone",
                    new float[]{0.5f, 0.5f, 0.5f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/stone.png", null, null));

            registerBlock(createDefaultBlock("Sand", "natural", "sand",
                    new float[]{0.93f, 0.87f, 0.51f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/sand.png", null, null));

            registerBlock(createDefaultBlock("Gravel", "natural", "gravel",
                    new float[]{0.53f, 0.53f, 0.53f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/gravel.png", null, null));

            registerBlock(createDefaultBlock("Clay", "natural", "clay",
                    new float[]{0.58f, 0.62f, 0.66f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/clay.png", null, null));

            registerBlock(createDefaultBlock("Snow", "natural", "snow",
                    new float[]{0.95f, 0.97f, 1.0f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/snow.png", null, null));

            registerBlock(createDefaultBlock("Ice", "natural", "ice",
                    new float[]{0.7f, 0.85f, 0.95f, 0.7f}, true, false, 0.0f,
                    "textures/blocks/ice.png", null, null));

            // === Wood Blocks ===
            registerBlock(createDefaultBlock("Oak Log", "wood", "oak_log",
                    new float[]{0.45f, 0.32f, 0.15f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/oak_log_top.png",
                    "textures/blocks/oak_log.png",
                    null));

            registerBlock(createDefaultBlock("Birch Log", "wood", "birch_log",
                    new float[]{0.8f, 0.78f, 0.7f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/birch_log_top.png",
                    "textures/blocks/birch_log.png",
                    null));

            registerBlock(createDefaultBlock("Spruce Log", "wood", "spruce_log",
                    new float[]{0.33f, 0.24f, 0.12f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/spruce_log_top.png",
                    "textures/blocks/spruce_log.png",
                    null));

            registerBlock(createDefaultBlock("Oak Planks", "wood", "oak_planks",
                    new float[]{0.7f, 0.56f, 0.35f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/oak_planks.png", null, null));

            registerBlock(createDefaultBlock("Birch Planks", "wood", "birch_planks",
                    new float[]{0.82f, 0.77f, 0.62f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/birch_planks.png", null, null));

            registerBlock(createDefaultBlock("Spruce Planks", "wood", "spruce_planks",
                    new float[]{0.53f, 0.4f, 0.25f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/spruce_planks.png", null, null));

            // === Building Blocks ===
            registerBlock(createDefaultBlock("Brick", "building", "brick",
                    new float[]{0.6f, 0.33f, 0.28f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/brick.png", null, null));

            registerBlock(createDefaultBlock("Stone Brick", "building", "stone_brick",
                    new float[]{0.47f, 0.47f, 0.47f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/stone_brick.png", null, null));

            registerBlock(createDefaultBlock("Cobblestone", "building", "cobblestone",
                    new float[]{0.45f, 0.45f, 0.45f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/cobblestone.png", null, null));

            registerBlock(createDefaultBlock("Glass", "building", "glass",
                    new float[]{0.8f, 0.9f, 1.0f, 0.3f}, true, false, 0.0f,
                    "textures/blocks/glass.png", null, null));

            registerBlock(createDefaultBlock("Iron Block", "building", "iron_block",
                    new float[]{0.77f, 0.77f, 0.77f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/iron_block.png", null, null));

            registerBlock(createDefaultBlock("Gold Block", "building", "gold_block",
                    new float[]{1.0f, 0.85f, 0.2f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/gold_block.png", null, null));

            registerBlock(createDefaultBlock("Diamond Block", "building", "diamond_block",
                    new float[]{0.4f, 0.85f, 0.9f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/diamond_block.png", null, null));

            registerBlock(createDefaultBlock("Emerald Block", "building", "emerald_block",
                    new float[]{0.15f, 0.7f, 0.3f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/emerald_block.png", null, null));

            // === Ores ===
            registerBlock(createDefaultBlock("Coal Ore", "ore", "coal_ore",
                    new float[]{0.35f, 0.35f, 0.35f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/coal_ore.png", null, null));

            registerBlock(createDefaultBlock("Iron Ore", "ore", "iron_ore",
                    new float[]{0.55f, 0.5f, 0.47f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/iron_ore.png", null, null));

            registerBlock(createDefaultBlock("Gold Ore", "ore", "gold_ore",
                    new float[]{0.55f, 0.5f, 0.25f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/gold_ore.png", null, null));

            registerBlock(createDefaultBlock("Diamond Ore", "ore", "diamond_ore",
                    new float[]{0.45f, 0.65f, 0.7f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/diamond_ore.png", null, null));

            registerBlock(createDefaultBlock("Redstone Ore", "ore", "redstone_ore",
                    new float[]{0.6f, 0.2f, 0.15f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/redstone_ore.png", null, null));

            registerBlock(createDefaultBlock("Emerald Ore", "ore", "emerald_ore",
                    new float[]{0.35f, 0.6f, 0.35f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/emerald_ore.png", null, null));

            // === Fluid Blocks ===
            registerBlock(createDefaultBlock("Water", "fluid", "water",
                    new float[]{0.2f, 0.4f, 0.85f, 0.65f}, true, false, 0.0f,
                    "textures/blocks/water.png", null, null));

            registerBlock(createDefaultBlock("Lava", "fluid", "lava",
                    new float[]{0.9f, 0.4f, 0.05f, 1.0f}, false, true, 1.0f,
                    "textures/blocks/lava.png", null, null));

            // === Functional Blocks ===
            registerBlock(createDefaultBlock("TNT", "functional", "tnt",
                    new float[]{0.8f, 0.2f, 0.15f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/tnt_top.png",
                    "textures/blocks/tnt_side.png",
                    "textures/blocks/tnt_bottom.png"));

            registerBlock(createDefaultBlock("Crafting Table", "functional", "crafting_table",
                    new float[]{0.6f, 0.45f, 0.28f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/crafting_table_top.png",
                    "textures/blocks/crafting_table_side.png",
                    "textures/blocks/oak_planks.png"));

            registerBlock(createDefaultBlock("Furnace", "functional", "furnace",
                    new float[]{0.47f, 0.47f, 0.47f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/furnace_top.png",
                    "textures/blocks/furnace_side.png",
                    null));

            registerBlock(createDefaultBlock("Glowstone", "functional", "glowstone",
                    new float[]{0.9f, 0.85f, 0.5f, 1.0f}, false, true, 0.8f,
                    "textures/blocks/glowstone.png", null, null));

            registerBlock(createDefaultBlock("Redstone Lamp", "functional", "redstone_lamp",
                    new float[]{0.95f, 0.75f, 0.3f, 1.0f}, false, true, 0.6f,
                    "textures/blocks/redstone_lamp_on.png", null, null));

            // === Decorative Blocks ===
            registerBlock(createDefaultBlock("Wool White", "decorative", "wool_white",
                    new float[]{0.9f, 0.9f, 0.9f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/wool_white.png", null, null));

            registerBlock(createDefaultBlock("Wool Red", "decorative", "wool_red",
                    new float[]{0.75f, 0.15f, 0.12f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/wool_red.png", null, null));

            registerBlock(createDefaultBlock("Wool Blue", "decorative", "wool_blue",
                    new float[]{0.2f, 0.25f, 0.7f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/wool_blue.png", null, null));

            registerBlock(createDefaultBlock("Wool Green", "decorative", "wool_green",
                    new float[]{0.2f, 0.6f, 0.2f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/wool_green.png", null, null));

            registerBlock(createDefaultBlock("Wool Black", "decorative", "wool_black",
                    new float[]{0.15f, 0.15f, 0.15f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/wool_black.png", null, null));

            registerBlock(createDefaultBlock("Wool Yellow", "decorative", "wool_yellow",
                    new float[]{0.9f, 0.85f, 0.2f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/wool_yellow.png", null, null));

            // === Nether Blocks ===
            registerBlock(createDefaultBlock("Netherrack", "nether", "netherrack",
                    new float[]{0.4f, 0.18f, 0.18f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/netherrack.png", null, null));

            registerBlock(createDefaultBlock("Soul Sand", "nether", "soul_sand",
                    new float[]{0.4f, 0.33f, 0.2f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/soul_sand.png", null, null));

            registerBlock(createDefaultBlock("Nether Brick", "nether", "nether_brick",
                    new float[]{0.25f, 0.15f, 0.2f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/nether_brick.png", null, null));

            // === End Blocks ===
            registerBlock(createDefaultBlock("End Stone", "end", "end_stone",
                    new float[]{0.82f, 0.82f, 0.72f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/end_stone.png", null, null));

            registerBlock(createDefaultBlock("Obsidian", "end", "obsidian",
                    new float[]{0.1f, 0.06f, 0.16f, 1.0f}, false, false, 0.0f,
                    "textures/blocks/obsidian.png", null, null));

            defaultsInitialized = true;
        }
    }

    /**
     * Helper method to create a default block with appropriate texture setup.
     *
     * @param name         display name
     * @param category     block category
     * @param blockType    block type identifier
     * @param color        RGBA color
     * @param transparent  whether the block is transparent
     * @param emissive     whether the block emits light
     * @param emissiveStr  emissive strength
     * @param topTexture   texture for top face (and all faces if side/bottom are null)
     * @param sideTexture  texture for north/south/east/west (optional)
     * @param bottomTexture texture for bottom face (optional)
     * @return the configured CustomBlock
     */
    private CustomBlock createDefaultBlock(String name, String category, String blockType,
                                           float[] color, boolean transparent,
                                           boolean emissive, float emissiveStr,
                                           String topTexture, String sideTexture,
                                           String bottomTexture) {
        CustomBlock block = new CustomBlock(name);
        block.setBlockType(blockType);
        block.setCategory(category);
        block.setCustomColor(color[0], color[1], color[2], color[3]);
        block.setTransparent(transparent);
        block.setEmissive(emissive);
        block.setEmissiveStrength(emissiveStr);

        BlockTextureSet textureSet = block.getTextureSet();

        // Set top face
        if (topTexture != null) {
            textureSet.setTexturePath(CustomBlock.BlockFace.TOP, topTexture);
        }

        // Set side faces
        String sidePath = sideTexture != null ? sideTexture : topTexture;
        if (sidePath != null) {
            textureSet.setTexturePath(CustomBlock.BlockFace.NORTH, sidePath);
            textureSet.setTexturePath(CustomBlock.BlockFace.SOUTH, sidePath);
            textureSet.setTexturePath(CustomBlock.BlockFace.EAST, sidePath);
            textureSet.setTexturePath(CustomBlock.BlockFace.WEST, sidePath);
        }

        // Set bottom face
        String bottomPath = bottomTexture != null ? bottomTexture : topTexture;
        if (bottomPath != null) {
            textureSet.setTexturePath(CustomBlock.BlockFace.BOTTOM, bottomPath);
        }

        return block;
    }

    /**
     * Gets all unique categories currently in the registry.
     *
     * @return list of category strings
     */
    public List<String> getAllCategories() {
        List<String> categories = new ArrayList<>();
        synchronized (registeredBlocks) {
            for (CustomBlock block : registeredBlocks.values()) {
                String cat = block.getCategory();
                if (!categories.contains(cat)) {
                    categories.add(cat);
                }
            }
        }
        Collections.sort(categories);
        return categories;
    }

    /**
     * Resets the singleton instance. Primarily for testing purposes.
     */
    public static void resetInstance() {
        synchronized (BlockRegistry.class) {
            if (instance != null) {
                instance.clearRegistry();
            }
            instance = null;
        }
    }

    /**
     * Checks if default blocks have been initialized.
     *
     * @return true if defaults are loaded
     */
    public boolean isDefaultsInitialized() {
        return defaultsInitialized;
    }
}
