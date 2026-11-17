package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Registers individual pieces and keeps the old palette identities for recovery. */
public final class Pieces {
    public static final Map<String, PieceBlock> BLOCKS = new LinkedHashMap<>();
    private static final Map<PieceKey, PieceBlock> PLAYABLE_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, Item> RETIRED_TEMPLATES = new LinkedHashMap<>();
    private static final Map<String, PieceItem> ITEMS = new LinkedHashMap<>();
    private static final Map<Block, java.util.function.Function<BlockState, Item>> STATE_ITEMS = new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean initialized;

    private Pieces() {
    }

    private record PieceKey(String material, Shape shape) {
    }

    public static Collection<PieceBlock> playableBlocks() {
        return java.util.Collections.unmodifiableCollection(PLAYABLE_BLOCKS.values());
    }

    public static Collection<PieceBlock> registeredBlocks() {
        var blocks = new ArrayList<>(BLOCKS.values());
        blocks.addAll(PLAYABLE_BLOCKS.values());
        return blocks;
    }

    public static PieceItem pieceItem(String identifier) {
        return ITEMS.get(identifier);
    }

    public static void register() {
        initialized = true;
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

            registry.register(Identifier.parse(palette.registryId()), block);
            BLOCKS.put(palette.module.equals("vanilla") ? palette.id : palette.registryId(), block);

            for (String material : palette.materials) {
                Identifier id = Identifier.parse(PaletteItemIds.itemId(palette, material));
                PieceBlock piece = new PieceBlock(palette.forMaterial(id.getPath(), material), false);
                registry.register(id, piece);
                PLAYABLE_BLOCKS.put(new PieceKey(material, palette.shape), piece);
            }
        }
    }

    public static void registerItems(IForgeRegistry<Item> registry) {
        registerNamespaceItems(SkysBuildingPieces.ID, registry);
        // Retain old item identities, creating their intrusive holders only
        // while Forge has opened the item registry.
        for (String name : new String[] { "slab", "vertical_slab", "step", "corner", "stairs", "wall", "pane" }) {
            Identifier id = Identifier.fromNamespaceAndPath(SkysBuildingPieces.ID, "template_" + name);
            Item item = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1));
            RETIRED_TEMPLATES.put(name, item);
            registry.register(id, item);
        }
    }

    public static void registerNamespaceItems(String namespace, IForgeRegistry<Item> registry) {
        for (PieceBlock block : BLOCKS.values()) {
            if (!block.palette.namespace.equals(namespace)) {
                continue;
            }

            // Keep the old item identity available for mixed and unopened saves. Normal drops, recipes and creative
            // entries use the flat items below.
            Identifier blockId = Identifier.parse(block.palette.registryId());
            Item carrier = new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, blockId)));

            registry.register(blockId, carrier);

            for (String material : block.palette.materials) {
                String identifier = PaletteItemIds.itemId(block.palette, material);

                if (ITEMS.containsKey(identifier)) {
                    throw new IllegalStateException("Duplicate piece item: " + identifier);
                }

                PieceItem item = new PieceItem(PLAYABLE_BLOCKS.get(new PieceKey(material, block.palette.shape)), material);

                registry.register(Identifier.parse(identifier), item);
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
        Block block = ForgeRegistries.BLOCKS.getValue(Identifier.parse(identifier));

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
        PieceBlock block = PLAYABLE_BLOCKS.get(new PieceKey(material, shape));
        if (block == null && (shape == Shape.HORIZONTAL_STEP || shape == Shape.VERTICAL_STEP)) {
            block = PLAYABLE_BLOCKS.get(new PieceKey(material, Shape.STEP));
            if (shape == Shape.VERTICAL_STEP) {
                orientation += 8;
            }
        }

        return block == null ? null : block.getStateFromMeta(orientation);
    }

    /** Recover retired materials and newly native equivalents without changing the saved geometry. */
    public static BlockState canonicalState(BlockState state) {
        return canonicalState(state, false);
    }

    /** Named Sky wool pieces keep their own identity when vanilla gains an equivalent. */
    public static BlockState canonicalNamedState(BlockState state) {
        return canonicalState(state, true);
    }

    private static BlockState canonicalState(BlockState state, boolean namedSave) {
        if (!(state.getBlock() instanceof PieceBlock)) {
            return state;
        }

        PieceBlock source = (PieceBlock) state.getBlock();
        Catalogue.MaterialDef material = source.definition(state);

        if (!source.isLegacyPalette() && material.replacement() == null
                && material.block(source.palette.shape.name().toLowerCase(Locale.ROOT)) == null) {
            return state;
        }

        Shape shape = source.palette.shape;
        int orientation = source.orientation(state);
        BlockState result;
        if (namedSave && source.isLegacyPalette() && source.palette.group.equals("cloth")
                && (shape == Shape.SLAB || shape == Shape.STAIRS)) {
            PieceBlock woolPiece = PLAYABLE_BLOCKS.get(new PieceKey(material.id, shape));
            result = woolPiece == null ? null : woolPiece.getStateFromMeta(orientation);
        } else {
            result = state(material.id, shape, orientation);
        }

        if (result == null || result.hasBlockEntity()) {
            throw new IllegalStateException("No safe replacement geometry for " + material.id);
        }
        for (Property.Value<?> property : state.getValues().toList()) {
            if (property.property().getName().equals("meta") || property.property().getName().equals("orientation")) {
                continue;
            }

            Property<?> target = result.getBlock().getStateDefinition().getProperty(property.property().getName());

            if (target != null) {
                result = withProperty(result, target, property.valueName());
            } else if (property.property().getName().equals("waterlogged") && Boolean.TRUE.equals(property.value())) {
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
        Item item = item(state);
        return item == null ? null : new ItemStack(item, count);
    }

    static Item item(BlockState state) {
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

        return item == Items.AIR ? null : item;
    }

    /** Resolve the old palette item while retaining unrelated stack NBT elsewhere. */
    public static ItemStack legacyItem(String registryId, int metadata, int count) {
        if (metadata < 0 || metadata > 15) {
            throw new IllegalArgumentException("Invalid saved piece metadata: " + registryId + ":" + metadata);
        }
        for (PieceBlock block : BLOCKS.values()) {
            if (ForgeRegistries.BLOCKS.getKey(block).toString().equals(registryId)) {
                String material = block.palette.material(metadata);
                Shape shape = block.palette.shape;
                BlockState canonical = state(material, shape, shape == Shape.SLAB ? 1 : 0);

                return stack(canonical, count);
            }
        }

        return null;
    }
}
