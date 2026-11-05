package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
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
    }

    public static void registerBlocks(Registry<Block> registry) {
        registerNamespaceBlocks(SkysBuildingPieces.ID, registry);
    }

    /** Called on the contributor's own mod bus, keeping registry ownership correct. */
    public static void registerNamespaceBlocks(String namespace, Registry<Block> registry) {
        Catalogue.INSTANCE.freeze();

        for (Catalogue.Palette palette : Catalogue.INSTANCE.palettes) {
            if (!palette.namespace.equals(namespace)) {
                continue;
            }

            PieceBlock block = new PieceBlock(palette);

            Registry.register(registry, ResourceLocation.parse(palette.registryId()), block);
            BLOCKS.put(palette.module.equals("vanilla") ? palette.id : palette.registryId(), block);
        }
    }

    public static void registerItems(Registry<Item> registry) {
        registerNamespaceItems(SkysBuildingPieces.ID, registry);
        // Retain old item identities, creating their intrusive holders only
        // while Forge has opened the item registry.
        for (String name : new String[] { "slab", "vertical_slab", "step", "corner", "stairs", "wall", "pane" }) {
            Item item = new Item(new Item.Properties().stacksTo(1));
            RETIRED_TEMPLATES.put(name, item);
            Registry.register(registry, ResourceLocation.fromNamespaceAndPath(SkysBuildingPieces.ID, "template_" + name), item);
        }
    }

    public static void registerNamespaceItems(String namespace, Registry<Item> registry) {
        for (PieceBlock block : BLOCKS.values()) {
            if (!block.palette.namespace.equals(namespace)) {
                continue;
            }

            // Keep the old item identity available for mixed and unopened saves. Normal drops, recipes and creative
            // entries use the flat items below.
            BlockItem carrier = new BlockItem(block, new Item.Properties());

            Registry.register(registry, ResourceLocation.parse(block.palette.registryId()), carrier);

            for (String material : block.palette.materials) {
                String identifier = PaletteItemIds.itemId(block.palette, material);

                if (ITEMS.containsKey(identifier)) {
                    throw new IllegalStateException("Duplicate piece item: " + identifier);
                }

                PieceItem item = new PieceItem(block, material);

                Registry.register(registry, ResourceLocation.parse(identifier), item);
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
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(identifier));

        if (block == null || block == Blocks.AIR) {
            if (definition != null && definition.has("fallback")) {
                return nativeState(definition.get("fallback"));
            }
            if (definition != null && definition.has("optional") && definition.get("optional").getAsBoolean()) {
                return null;
            }

            throw new IllegalStateException("Missing native building material: " + identifier);
        }

        BlockState result = block.defaultBlockState();

        if (definition != null && definition.has("properties")) {
            for (Map.Entry<String, JsonElement> entry : definition.getAsJsonObject("properties").entrySet()) {
                Property<?> property = block.getStateDefinition().getProperty(entry.getKey());

                if (property == null) {
                    throw new IllegalStateException("Unknown property " + entry.getKey() + " on " + identifier);
                }

                result = withProperty(result, property, entry.getValue().getAsString());
            }
        }

        return result;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property,
            String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException("Invalid " + property.getName() + "=" + value + " on " + state));

        return state.setValue(property, parsed);
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

                return nativePiece.setValue(StairBlock.FACING, facing)
                        .setValue(StairBlock.HALF, orientation < 4 ? Half.BOTTOM : Half.TOP)
                        .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
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

        if (result == null || result.hasBlockEntity()) {
            throw new IllegalStateException("No safe replacement geometry for " + material.id);
        }
        for (Map.Entry<Property<?>, Comparable<?>> property : state.getValues().entrySet()) {
            if (property.getKey().getName().equals("meta")) {
                continue;
            }

            Property<?> target = result.getBlock().getStateDefinition().getProperty(property.getKey().getName());

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

        if (state.hasProperty(net.minecraft.world.level.block.SlabBlock.TYPE)
                && state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE) {
            return null; // A native double slab has no distinct full-block inventory item.
        }

        Item item;

        if (state.getBlock() instanceof PieceBlock) {
            PieceBlock block = (PieceBlock) state.getBlock();

            item = ITEMS.get(PaletteItemIds.itemId(block.palette, block.definition(state).id));
        } else {
            java.util.function.Function<BlockState, Item> lookup = STATE_ITEMS.get(state.getBlock());

            item = lookup == null ? state.getBlock().asItem() : lookup.apply(state);
        }

        return item == null || item == Items.AIR ? null : new ItemStack(item, count);
    }

    /** Resolve the old palette item while retaining unrelated stack NBT elsewhere. */
    public static ItemStack legacyItem(String registryId, int metadata, int count) {
        if (metadata < 0 || metadata > 15) {
            throw new IllegalArgumentException("Invalid saved piece metadata: " + registryId + ":" + metadata);
        }
        for (PieceBlock block : BLOCKS.values()) {
            if (BuiltInRegistries.BLOCK.getKey(block).toString().equals(registryId)) {
                String material = block.palette.material(metadata);
                Shape shape = block.palette.shape;
                BlockState canonical = state(material, shape, shape == Shape.SLAB ? 1 : 0);

                return stack(canonical, count);
            }
        }

        return null;
    }
}
