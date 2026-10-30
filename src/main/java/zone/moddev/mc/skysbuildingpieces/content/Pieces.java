package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

public final class Pieces {
    public static final Map<String, PieceBlock> BLOCKS = new LinkedHashMap<String, PieceBlock>();
    // Earlier betas may have saved these IDs. They are hidden, cannot be made, and have no cutting behaviour; a
    // one-item recipe recycles them to paper.
    public static final Map<String, Item> RETIRED_TEMPLATES = new LinkedHashMap<String, Item>();
    // The compact catalogue is used only by the migration test fixture.
    public static final boolean COMPACT = false;
    public static boolean initialized;

    public static void register() {
        Catalogue.INSTANCE.modules.get("vanilla");
        initialized = true;

        for (String name : new String[] { "slab", "vertical_slab", "step", "corner", "stairs", "wall", "pane" }) {
            Item item = new Item().setMaxStackSize(1).setTranslationKey("skysbuildingpieces.template_" + name)
                    .setRegistryName("skysbuildingpieces", "template_" + name);

            RETIRED_TEMPLATES.put(name, item);
        }
    }

    public static void registerBlocks(IForgeRegistry<Block> registry) {
        Catalogue.INSTANCE.freeze();

        for (Catalogue.Module module : Catalogue.INSTANCE.modules.values()) {
            registerForOwner(module.namespace, () -> registerModule(module, registry));
        }
    }

    public static void registerItems(IForgeRegistry<Item> registry) {
        for (PieceBlock block : BLOCKS.values()) {
            registerForOwner(block.getRegistryName().getNamespace(),
                    () -> registry.register(new PieceItem(block).setRegistryName(block.getRegistryName())));
        }
        for (Item item : RETIRED_TEMPLATES.values()) {
            registry.register(item);
        }
    }

    /**
     * Registry events reach the core, but the contributor owns its palette IDs.
     * Restore the previous context even if registration fails.
     */
    private static void registerForOwner(String namespace, Runnable registration) {
        net.minecraftforge.fml.common.Loader loader = net.minecraftforge.fml.common.Loader.instance();
        net.minecraftforge.fml.common.ModContainer previous = loader.activeModContainer();
        net.minecraftforge.fml.common.ModContainer owner = loader.getIndexedModList().get(namespace);
        if (owner == null) {
            throw new IllegalStateException("No loaded mod owns the piece namespace " + namespace);
        }
        try {
            loader.setActiveModContainer(owner);
            registration.run();
        } finally {
            loader.setActiveModContainer(previous);
        }
    }

    private static void registerModule(Catalogue.Module module, IForgeRegistry<Block> registry) {
        for (Catalogue.MaterialDef definition : module.materials.values()) {
            nativeState(definition, "full");
        }
        for (Catalogue.Palette palette : module.palettes) {
            if (COMPACT && !(palette.materials.contains("minecraft:grass")
                    || palette.materials.contains("minecraft:dirt") || palette.materials.contains("minecraft:glass")
                    || palette.materials.contains("minecraft:stone"))) {
                continue;
            }

            PieceBlock block = new PieceBlock(palette);

            registry.register(block);
            BLOCKS.put(palette.module.equals("vanilla") ? palette.id : palette.registryId(), block);

            if (palette.group.equals("wood")) {
                net.minecraft.init.Blocks.FIRE.setFireInfo(block, 5, 20);
            }
            if (palette.group.equals("cloth")) {
                net.minecraft.init.Blocks.FIRE.setFireInfo(block, 30, 60);
            }
        }
    }

    public static IBlockState nativeState(Catalogue.MaterialDef material, String shape) {
        if (material.id.equals("minecraft:grass_path") && shape.equals("slab")) {
            // Path slabs remain owned by Grass Slabs, which is optional here.
            Block pathSlab = Block.REGISTRY.getObject(new ResourceLocation("skysgrassslabs", "path_slab"));
            return pathSlab == null || pathSlab == net.minecraft.init.Blocks.AIR ? null : pathSlab.getDefaultState();
        }
        JsonElement value = material.block(shape);

        if (value == null) {
            return null;
        }

        return nativeState(value);
    }

    private static IBlockState nativeState(JsonElement value) {
        String registryId = value.isJsonPrimitive() ? value.getAsString()
                : value.getAsJsonObject().get("id").getAsString();
        int meta = value.isJsonObject() && value.getAsJsonObject().has("meta")
                ? value.getAsJsonObject().get("meta").getAsInt()
                : 0;
        Block block = Block.REGISTRY.getObject(new ResourceLocation(registryId));

        if (block == null || block instanceof net.minecraft.block.BlockAir) {
            // An add-on may name a specific replacement for a retired material. Missing materials without an explicit
            // fallback still stop loading.
            if (value.isJsonObject() && value.getAsJsonObject().has("fallback")) {
                return nativeState(value.getAsJsonObject().get("fallback"));
            }

            throw new IllegalStateException("Missing native material " + registryId);
        }

        return block.getStateFromMeta(meta);
    }

    public static IBlockState state(String material, Shape shape, int orientation) {
        if (shape == Shape.STEP) {
            shape = orientation >= 8 ? Shape.VERTICAL_STEP : Shape.HORIZONTAL_STEP;
            orientation = orientation >= 8 ? orientation & 3 : orientation;
        }

        Catalogue.MaterialDef materialDefinition = Catalogue.INSTANCE.materials.get(material);

        if (materialDefinition == null || !Catalogue.supportsShape(materialDefinition, shape)) {
            return null;
        }

        IBlockState nativeState = nativeState(materialDefinition, shape.name().toLowerCase(Locale.ROOT));

        if (nativeState != null) {
            if (shape == Shape.SLAB) {
                return NativeSlabState.orient(nativeState, orientation);
            }
            if (shape == Shape.STAIRS) {
                return nativeState.getBlock().getStateFromMeta(orientation & 7);
            }

            return nativeState;
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

    static ItemStack slabDrop(String material, IBlockState fallback) {
        IBlockState horizontal = state(material, Shape.SLAB, 1);

        if (horizontal != null) {
            return stack(horizontal, 1);
        }
        if (material.equals("minecraft:dirt")) {
            Item dirt = Item.getByNameOrId("skysgrassslabs:dirt_slab");

            if (dirt != null) {
                return new ItemStack(dirt, 1, 0);
            }
        }

        return stack(fallback, 1);
    }

    public static ItemStack stack(IBlockState state, int count) {
        if (state == null) {
            return null;
        }

        Block block = state.getBlock();
        // A material identity is not its ordinary breaking drop: vanilla podzol, for example, drops dirt but its
        // crafting input is podzol.
        int meta = block instanceof PieceBlock ? ((PieceBlock) block).canonicalMeta(state)
                : block instanceof net.minecraft.block.BlockStairs ? 0
                        : block instanceof net.minecraft.block.BlockSlab ? block.damageDropped(state)
                                : block.getMetaFromState(state);
        Item item = Item.getItemFromBlock(block);

        // Facing is a placed state, not an item variant. Mineralogy's six-way
        // slabs, for example, use one ordinary item regardless of orientation.
        if (item != null && !item.getHasSubtypes()) {
            meta = 0;
        }

        return item == null || item == net.minecraft.init.Items.AIR ? null : new ItemStack(item, count, meta);
    }

    private Pieces() {
    }
}
