package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.IProperty;
import net.minecraft.state.properties.Half;
import net.minecraft.state.properties.StairsShape;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Fixed placed palettes and separate material items for the native recipe book. */
public final class Pieces {
    public static final Map<String, PieceBlock> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, Item> RETIRED_TEMPLATES = new LinkedHashMap<>();
    private static final Map<String, PieceItem> ITEMS = new LinkedHashMap<>();
    private static final Map<Block, java.util.function.Function<BlockState, Item>> STATE_ITEMS = new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean initialized;

    private Pieces() {
    }

    public static PieceItem pieceItem(String identifier) {
        return ITEMS.get(identifier);
    }

    public static void register() {
        initialized = true;

        for (String name : new String[] { "slab", "vertical_slab", "step", "corner", "stairs", "wall", "pane" }) {
            Item item = new Item(new Item.Properties().maxStackSize(1));

            item.setRegistryName(SkysBuildingPieces.ID, "template_" + name);
            RETIRED_TEMPLATES.put(name, item);
        }
    }

    public static void registerBlocks(IForgeRegistry<Block> registry) {
        registerNamespaceBlocks(SkysBuildingPieces.ID, registry);
    }

    /** Called on the contributor's own mod bus, keeping registry ownership correct. */
    public static void registerNamespaceBlocks(String namespace, IForgeRegistry<Block> registry) {
        Catalogue.INSTANCE.freeze();

        for (Catalogue.Palette palette : Catalogue.INSTANCE.palettes) {
            if (!palette.namespace.equals(namespace)) {
                continue;
            }

            PieceBlock block = new PieceBlock(palette);

            registry.register(block);
            BLOCKS.put(palette.module.equals("vanilla") ? palette.id : palette.registryId(), block);
        }
    }

    public static void registerItems(IForgeRegistry<Item> registry) {
        registerNamespaceItems(SkysBuildingPieces.ID, registry);
        RETIRED_TEMPLATES.values().forEach(registry::register);
    }

    public static void registerNamespaceItems(String namespace, IForgeRegistry<Item> registry) {
        for (PieceBlock block : BLOCKS.values()) {
            if (!block.palette.namespace.equals(namespace)) {
                continue;
            }

            // Keep the old item identity available for mixed and unopened saves. Normal drops, recipes and creative
            // entries use the flat items below.
            BlockItem carrier = new BlockItem(block, new Item.Properties());

            carrier.setRegistryName(block.getRegistryName());
            registry.register(carrier);

            for (String material : block.palette.materials) {
                String identifier = PaletteItemIds.itemId(block.palette, material);

                if (ITEMS.containsKey(identifier)) {
                    throw new IllegalStateException("Duplicate piece item: " + identifier);
                }

                PieceItem item = new PieceItem(block, material);

                item.setRegistryName(new ResourceLocation(identifier));
                registry.register(item);
                ITEMS.put(identifier, item);
            }
        }
    }

    public static void validateMaterials() {
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            nativeState(material, "full");
        }
    }

    /** Flat inventory identities for native full blocks that retain a saved palette. */
    public static synchronized void registerStateItems(Block block,
            java.util.function.Function<BlockState, Item> items) {
        if (zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge.ready || block == null || items == null
                || STATE_ITEMS.containsKey(block)) {
            throw new IllegalStateException("Invalid, duplicate or late state-item lookup");
        }

        STATE_ITEMS.put(block, items);
    }

    public static BlockState nativeState(Catalogue.MaterialDef material, String shape) {
        if (material.replacement() != null) {
            material = Catalogue.INSTANCE.materials.get(Catalogue.INSTANCE.resolveReplacement(material.id));
        }

        JsonElement value = material.block(shape);

        return value == null ? null : nativeState(value);
    }

    private static BlockState nativeState(JsonElement value) {
        JsonObject definition = value.isJsonObject() ? value.getAsJsonObject() : null;
        String identifier = definition == null ? value.getAsString() : definition.get("id").getAsString();
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(identifier));

        if (block == null || block == Blocks.AIR) {
            if (definition != null && definition.has("fallback")) {
                return nativeState(definition.get("fallback"));
            }
            if (definition != null && definition.has("optional") && definition.get("optional").getAsBoolean()) {
                return null;
            }

            throw new IllegalStateException("Missing native building material: " + identifier);
        }

        BlockState result = block.getDefaultState();

        if (definition != null && definition.has("properties")) {
            for (Map.Entry<String, JsonElement> entry : definition.getAsJsonObject("properties").entrySet()) {
                IProperty<?> property = block.getStateContainer().getProperty(entry.getKey());

                if (property == null) {
                    throw new IllegalStateException("Unknown property " + entry.getKey() + " on " + identifier);
                }

                result = withProperty(result, property, entry.getValue().getAsString());
            }
        }

        return result;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, IProperty<T> property,
            String value) {
        T parsed = property.parseValue(value).orElseThrow(
                () -> new IllegalStateException("Invalid " + property.getName() + "=" + value + " on " + state));

        return state.with(property, parsed);
    }

    public static BlockState state(String material, Shape shape, int orientation) {
        material = Catalogue.INSTANCE.resolveReplacement(material);

        if (shape == Shape.STEP) {
            shape = orientation >= 8 ? Shape.VERTICAL_STEP : Shape.HORIZONTAL_STEP;
            orientation = orientation >= 8 ? orientation & 3 : orientation;
        }

        Catalogue.MaterialDef definition = Catalogue.INSTANCE.materials.get(material);

        if (definition == null) {
            return null;
        }
        if (shape == Shape.VERTICAL_SLAB) {
            BlockState horizontal = nativeState(definition, "slab");
            BlockState vertical = horizontal == null ? null : NativeSlabState.vertical(horizontal, orientation);

            if (vertical != null) {
                return vertical;
            }
        }

        BlockState nativePiece = nativeState(definition, shape.name().toLowerCase(Locale.ROOT));

        if (nativePiece != null) {
            if (shape == Shape.SLAB) {
                return NativeSlabState.orient(nativePiece, orientation);
            }
            if (shape == Shape.STAIRS) {
                Direction facing = new Direction[] { Direction.EAST, Direction.WEST, Direction.SOUTH,
                        Direction.NORTH }[orientation & 3];

                return nativePiece.with(StairsBlock.FACING, facing)
                        .with(StairsBlock.HALF, orientation < 4 ? Half.BOTTOM : Half.TOP)
                        .with(StairsBlock.SHAPE, StairsShape.STRAIGHT);
            }

            return nativePiece;
        }
        if (!Catalogue.supportsShape(definition, shape)) {
            return null;
        }
        for (PieceBlock block : BLOCKS.values()) {
            Shape stored = block.palette.shape;
            int mapped = orientation;

            if (stored == Shape.STEP && (shape == Shape.HORIZONTAL_STEP || shape == Shape.VERTICAL_STEP)) {
                mapped = (shape == Shape.VERTICAL_STEP ? 8 : 0) + orientation;
            } else if (stored != shape) {
                continue;
            }
            if (block.palette.materials.contains(material)) {
                return block.getStateFromMeta(block.palette.meta(material, mapped));
            }
        }

        return null;
    }

    /** Recover retired materials and newly native equivalents without changing the saved geometry. */
    public static BlockState canonicalState(BlockState state) {
        if (!(state.getBlock() instanceof PieceBlock)) {
            return state;
        }

        PieceBlock source = (PieceBlock) state.getBlock();
        Catalogue.MaterialDef material = source.definition(state);

        if (material.replacement() == null
                && material.block(source.palette.shape.name().toLowerCase(Locale.ROOT)) == null) {
            return state;
        }

        int orientation = source.getMetaFromState(state) % source.palette.shape.states;
        BlockState result = state(material.id, source.palette.shape, orientation);

        if (result == null || result.getBlock().hasTileEntity(result)) {
            throw new IllegalStateException("No safe replacement geometry for " + material.id);
        }
        for (Map.Entry<IProperty<?>, Comparable<?>> property : state.getValues().entrySet()) {
            if (property.getKey().getName().equals("meta")) {
                continue;
            }

            IProperty<?> target = result.getBlock().getStateContainer().getProperty(property.getKey().getName());

            if (target != null) {
                result = withProperty(result, target, property.getValue().toString().toLowerCase(Locale.ROOT));
            } else if (property.getKey().getName().equals("waterlogged") && Boolean.TRUE.equals(property.getValue())) {
                throw new IllegalStateException("Material replacement would lose water: " + material.id);
            }
        }

        return result;
    }

    static ItemStack slabDrop(String material, BlockState fallback) {
        BlockState horizontal = state(material, Shape.SLAB, 1);

        return stack(horizontal == null ? fallback : horizontal, 1);
    }

    public static ItemStack stack(BlockState state, int count) {
        if (state == null) {
            return null;
        }

        state = canonicalState(state);

        if (state.has(net.minecraft.block.SlabBlock.TYPE)
                && state.get(net.minecraft.block.SlabBlock.TYPE) == net.minecraft.state.properties.SlabType.DOUBLE) {
            return null; // A native double slab has no distinct full-block inventory item.
        }

        Item item;

        if (state.getBlock() instanceof PieceBlock) {
            PieceBlock block = (PieceBlock) state.getBlock();

            item = ITEMS.get(PaletteItemIds.itemId(block.palette, block.definition(state).id));
        } else {
            java.util.function.Function<BlockState, Item> lookup = STATE_ITEMS.get(state.getBlock());

            item = lookup == null ? Item.getItemFromBlock(state.getBlock()) : lookup.apply(state);
        }

        return item == null || item == Items.AIR ? null : new ItemStack(item, count);
    }

    /** Resolve the old palette item while retaining unrelated stack NBT elsewhere. */
    public static ItemStack legacyItem(String registryId, int metadata, int count) {
        if (metadata < 0 || metadata > 15) {
            throw new IllegalArgumentException("Invalid saved piece metadata: " + registryId + ":" + metadata);
        }
        for (PieceBlock block : BLOCKS.values()) {
            if (block.getRegistryName().toString().equals(registryId)) {
                String material = block.palette.material(metadata);
                Shape shape = block.palette.shape;
                BlockState canonical = state(material, shape, shape == Shape.SLAB ? 1 : 0);

                return stack(canonical, count);
            }
        }

        return null;
    }
}
