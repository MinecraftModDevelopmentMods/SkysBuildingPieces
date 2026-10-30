package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Resolves legacy material data before Minecraft creates blocks and inventories. */
public final class LegacyBridge {
    public static volatile boolean ready;
    private static final Map<Integer, String> savedIds = new HashMap<Integer, String>();
    private static final Map<String, Long> unresolved = new TreeMap<String, Long>();
    private static File worldDirectory;
    private static final Map<String, java.util.concurrent.atomic.AtomicLong> pendingItems = new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();

    private LegacyBridge() {
    }

    public static boolean active() {
        return ready && LegacyMapping.enabled(Loader.isModLoaded("buildingbricks"), SkysBuildingPieces.forceReplace);
    }

    public static boolean recoveryActive() {
        return active() || ready && HistoricalFullBlocks.hasSource(savedIds);
    }

    public static void start(MinecraftServer server) {
        stop();
        worldDirectory = server.isDedicatedServer() ? server.getFile(server.getFolderName())
                : new File(server.getDataDirectory(), "saves/" + server.getFolderName());

        File level = new File(worldDirectory, "level.dat");

        if (level.isFile()) {
            try {
                NBTTagCompound root;

                try (InputStream input = new FileInputStream(level)) {
                    root = CompressedStreamTools.readCompressed(input);
                }

                NBTTagCompound registry = root.getCompoundTag("FML").getCompoundTag("Registries")
                        .getCompoundTag("minecraft:blocks");
                NBTTagList ids = registry.getTagList("ids", 10);

                for (int registryIndex = 0; registryIndex < ids.tagCount(); registryIndex++) {
                    NBTTagCompound registryEntry = ids.getCompoundTagAt(registryIndex);
                    String name = registryEntry.getString("K");

                    if (LegacyMapping.owned(name) || HistoricalFullBlocks.historical(name)) {
                        savedIds.put(registryEntry.getInteger("V"), name);
                    }
                }

                File sidecar = new File(worldDirectory, "data/skysbuildingpieces_legacy_registry.dat");

                if (sidecar.isFile()) {
                    NBTTagList prior;

                    try (InputStream input = new FileInputStream(sidecar)) {
                        prior = CompressedStreamTools.readCompressed(input).getCompoundTag("Registries")
                                .getCompoundTag("minecraft:blocks").getTagList("ids", 10);
                    }
                    for (int registryIndex = 0; registryIndex < prior.tagCount(); registryIndex++) {
                        NBTTagCompound registryEntry = prior.getCompoundTagAt(registryIndex);

                        if (LegacyMapping.owned(registryEntry.getString("K"))
                                || HistoricalFullBlocks.historical(registryEntry.getString("K"))) {
                            savedIds.putIfAbsent(registryEntry.getInteger("V"), registryEntry.getString("K"));
                        }
                    }
                }
                if ((!Loader.isModLoaded("buildingbricks") || HistoricalFullBlocks.hasSource(savedIds))
                        && !savedIds.isEmpty()) {
                    LegacyPreflight.validate(worldDirectory, savedIds);
                }
                // An unsafe upgrade must not write even a migration sidecar.
                if (recoveryActive() && !savedIds.isEmpty() && !sidecar.isFile()) {
                    sidecar.getParentFile().mkdirs();

                    try (OutputStream output = new FileOutputStream(sidecar)) {
                        CompressedStreamTools.writeCompressed(root.getCompoundTag("FML"), output);
                    }
                }
            } catch (IOException failure) {
                throw new RecoveryAbort("Cannot read the legacy registry snapshot", failure);
            }
        }
    }

    public static synchronized void stop() {
        savedIds.clear();
        unresolved.clear();
        worldDirectory = null;
        pendingItems.clear();
    }

    public static boolean hasPendingReport() {
        synchronized (unresolved) {
            return pendingItems.values().stream().anyMatch(pendingCount -> pendingCount.get() != 0)
                    || !unresolved.isEmpty();
        }
    }

    public static void prepareStack(NBTTagCompound stack) {
        if (ready) {
            zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.prepareStacks(stack);
        }
        if (!ready) {
            return;
        }

        List<StackChange> changes = new ArrayList<StackChange>();

        collectStack(stack, changes);
        applyStacks(changes);
    }

    private static void applyStacks(List<StackChange> changes) {
        // Resolve every nested target before changing any part of the stack.
        for (StackChange change : changes) {
            change.stack.setString("id", change.output.getItem().getRegistryName().toString());
            change.stack.setShort("Damage", (short) change.output.getMetadata());

            if (worldDirectory != null) {
                pendingItems.computeIfAbsent(change.module, key -> new java.util.concurrent.atomic.AtomicLong())
                        .addAndGet(change.stack.getByte("Count") & 255);
            }
        }
    }

    private static void collectStack(NBTTagCompound stack, List<StackChange> changes) {
        if (stack.hasKey("id", 8) && stack.hasKey("Count", 99)) {
            collectIdentity(stack, changes);
        }

        collectInventories(stack.getTag("tag"), changes);
        collectInventories(stack.getTag("ForgeCaps"), changes);
    }

    private static void collectInventories(NBTBase value, List<StackChange> changes) {
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) value;

            if (compound.hasKey("id", 8) && compound.hasKey("Count", 99)) {
                collectStack(compound, changes);
                return;
            }
            for (String key : compound.getKeySet()) {
                NBTBase child = compound.getTag(key);

                collectInventories(child, changes);
            }
        } else if (value instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) value;

            for (int entryIndex = 0; entryIndex < list.tagCount(); entryIndex++) {
                collectInventories(list.get(entryIndex), changes);
            }
        }
    }

    private static void collectIdentity(NBTTagCompound stack, List<StackChange> changes) {
        String registry = stack.getString("id");
        IBlockState retired = HistoricalFullBlocks.resolve(registry, stack.getShort("Damage"));

        if (retired != null) {
            net.minecraft.item.ItemStack output = Pieces.stack(retired, 1);

            if (output == null || output.isEmpty()) {
                throw new RecoveryAbort("Recovered block has no item: " + registry, null);
            }

            changes.add(new StackChange(stack, output, HistoricalFullBlocks.module(registry)));
            return;
        }
        if (!active() || !LegacyMapping.owned(registry)) {
            return;
        }

        NBTTagCompound tag = stack.getCompoundTag("tag");
        String material = LegacyMapping.material(tag.getString("material"));
        // Item damage is a dynamic material index, never a placement orientation.
        Shape shape = LegacyMapping.shape(registry, 0);
        IBlockState target = resolve(registry, material, shape, shape == Shape.SLAB ? 1 : 0);

        if (target == null) {
            return;
        }

        net.minecraft.item.ItemStack output = Pieces.stack(target, 1);

        if (output == null) {
            throw new RecoveryAbort("Recovered piece has no item: " + registry + " / " + material, null);
        }

        changes.add(new StackChange(stack, output, Catalogue.INSTANCE.moduleFor(material)));
        // The saved material is retained as unrelated custom data; all other NBT and counts remain untouched.
    }

    private static final class StackChange {
        final NBTTagCompound stack;
        final net.minecraft.item.ItemStack output;
        final String module;

        StackChange(NBTTagCompound stack, net.minecraft.item.ItemStack output, String module) {
            this.stack = stack;
            this.output = output;
            this.module = module;
        }
    }

    public static void prepareChunk(World world, NBTTagCompound root) {
        if (ready) {
            zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.prepareStacks(root.getCompoundTag("Level"));
        }
        if (ready) {
            // Some containers reject missing item IDs before constructing an ItemStack. Recover saved stacks before
            // those serializers run, including inventories with names other than vanilla's Items.
            NBTTagCompound stored = root.getCompoundTag("Level");
            List<StackChange> changes = new ArrayList<StackChange>();

            collectInventories(stored.getTag("TileEntities"), changes);
            collectInventories(stored.getTag("Entities"), changes);
            applyStacks(changes);
        }
        if (ready) {
            HistoricalFullBlocks.prepareChunk(root, savedIds);
        }
        if (!active()) {
            return;
        }

        NBTTagCompound level = root.getCompoundTag("Level");
        NBTTagCompound coverage = level.getCompoundTag("skysbuildingpieces_coverage");
        Set<String> pendingModules = new LinkedHashSet<String>();

        for (String module : Catalogue.INSTANCE.modules.keySet()) {
            if (coverage.getInteger(module) < 1) {
                pendingModules.add(module);
            }
        }

        boolean pendingDirt = coverage.getInteger("dirt_vertical_slabs") < 1;

        if (pendingModules.isEmpty() && !pendingDirt) {
            return;
        }

        NBTTagList tiles = level.getTagList("TileEntities", 10);
        NBTTagList sections = level.getTagList("Sections", 10);

        Map<Integer, NBTTagCompound> materialAt = new HashMap<Integer, NBTTagCompound>();

        for (int tileIndex = 0; tileIndex < tiles.tagCount(); tileIndex++) {
            NBTTagCompound legacyTileData = tiles.getCompoundTagAt(tileIndex);
            int index = (legacyTileData.getInteger("x") & 15) | ((legacyTileData.getInteger("z") & 15) << 4)
                    | (legacyTileData.getInteger("y") << 8);

            materialAt.put(index, legacyTileData);
        }

        // Resolve every candidate before modifying any section or deleting a legacy tile.
        List<Conversion> decisions = new ArrayList<Conversion>();

        for (int sectionIndex = 0; sectionIndex < sections.tagCount(); sectionIndex++) {
            NBTTagCompound section = sections.getCompoundTagAt(sectionIndex);
            byte[] blocks = section.getByteArray("Blocks");
            byte[] blockIdHighNibbles = section.getByteArray("Add");
            byte[] data = section.getByteArray("Data");

            if (blocks.length != 4096 || data.length != 2048
                    || blockIdHighNibbles.length != 0 && blockIdHighNibbles.length != 2048) {
                throw new RecoveryAbort("Malformed legacy chunk section", null);
            }
            for (int blockIndex = 0; blockIndex < 4096; blockIndex++) {
                int numeric = (blocks[blockIndex] & 255) | (nibble(blockIdHighNibbles, blockIndex) << 8);
                Block legacyBlock = Block.getBlockById(numeric);
                net.minecraft.util.ResourceLocation registeredName = legacyBlock == null ? null
                        : Block.REGISTRY.getNameForObject(legacyBlock);
                String registry = registeredName == null ? "" : registeredName.toString();

                if (!LegacyMapping.owned(registry)) {
                    // Never reinterpret an already converted Sky or unrelated live ID through a historical snapshot
                    // that used the same numeric slot.
                    if (legacyBlock == SkysBuildingPieces.HOLDER || legacyBlock == null
                            || legacyBlock instanceof net.minecraft.block.BlockAir) {
                        registry = savedIds.get(numeric);
                    } else {
                        continue;
                    }
                }
                if (registry == null || !LegacyMapping.owned(registry)) {
                    continue;
                }

                int tilePositionKey = (blockIndex & 255)
                        | (((section.getByte("Y") & 255) * 16 + (blockIndex >> 8)) << 8);
                NBTTagCompound legacyTileData = materialAt.get(tilePositionKey);
                String material = legacyTileData == null ? "" : legacyTileData.getString("material");
                String module = Catalogue.INSTANCE.moduleFor(LegacyMapping.material(material));
                boolean dirtVertical = registry.equals("buildingbricks:dirt_vertical_slab");

                if (dirtVertical ? !pendingDirt : module != null && !pendingModules.contains(module)) {
                    continue;
                }

                Shape shape = LegacyMapping.shape(registry, nibble(data, blockIndex));
                IBlockState target = resolve(registry, LegacyMapping.material(material), shape,
                        LegacyMapping.orientation(shape, nibble(data, blockIndex)));

                if (target != null) {
                    decisions.add(new Conversion(section, blockIndex, target, legacyTileData, module));
                }
            }
        }

        Set<NBTTagCompound> removed = Collections.newSetFromMap(new IdentityHashMap<NBTTagCompound, Boolean>());
        NBTTagList retainedTileData = coverage.getTagList("retained_tiles", 10);

        // Every target is now known. Commit the block states before removing their old material tiles.
        for (Conversion conversion : decisions) {
            int blockId = Block.getIdFromBlock(conversion.state.getBlock());
            byte[] blockIdLowBytes = conversion.section.getByteArray("Blocks");

            blockIdLowBytes[conversion.index] = (byte) blockId;

            byte[] blockIdHighNibbles = conversion.section.getByteArray("Add");

            if (blockIdHighNibbles.length == 0 && blockId > 255) {
                blockIdHighNibbles = new byte[2048];
            }
            if (blockIdHighNibbles.length != 0) {
                setNibble(blockIdHighNibbles, conversion.index, blockId >> 8);
                conversion.section.setByteArray("Add", blockIdHighNibbles);
            }

            byte[] meta = conversion.section.getByteArray("Data");

            setNibble(meta, conversion.index, conversion.state.getBlock().getMetaFromState(conversion.state));
            conversion.section.setByteArray("Blocks", blockIdLowBytes);
            conversion.section.setByteArray("Data", meta);

            if (conversion.tile != null) {
                removed.add(conversion.tile);
                retainedTileData.appendTag(conversion.tile.copy());
            }
        }

        NBTTagList retained = new NBTTagList();

        for (int tileIndex = 0; tileIndex < tiles.tagCount(); tileIndex++) {
            if (!removed.contains(tiles.getCompoundTagAt(tileIndex))) {
                retained.appendTag(tiles.getCompoundTagAt(tileIndex));
            }
        }

        level.setTag("TileEntities", retained);

        NBTTagCompound deltas = new NBTTagCompound();

        for (String module : pendingModules) {
            coverage.setInteger(module, 1);
            deltas.setLong(module, 0);
        }
        if (pendingDirt) {
            coverage.setInteger("dirt_vertical_slabs", 1);
        }
        for (Conversion conversion : decisions) {
            deltas.setLong(conversion.module, deltas.getLong(conversion.module) + 1);
        }

        coverage.setTag("retained_tiles", retainedTileData);
        level.setTag("skysbuildingpieces_coverage", coverage);

        if (pendingModules.contains("vanilla")) {
            root.setLong("skysbuildingpieces_delta", deltas.getLong("vanilla"));
        } else if (deltas.getLong("vanilla") > 0) {
            root.setLong("skysbuildingpieces_additional_vanilla_delta", deltas.getLong("vanilla"));
        }

        deltas.removeTag("vanilla");

        if (!deltas.isEmpty()) {
            root.setTag("skysbuildingpieces_module_delta", deltas);
        }
    }

    /** Keep unsupported pieces with their installed mod; stop absent-mod recovery rather than guess. */
    public static IBlockState resolve(String registry, String material, Shape shape, int orientation) {
        IBlockState target = LegacyMapping.allowedMaterial(material, shape) ? Pieces.state(material, shape, orientation)
                : null;

        if (target == null) {
            String key = registry + " / " + material + " / " + shape;

            synchronized (unresolved) {
                unresolved.put(key, unresolved.getOrDefault(key, 0L) + 1);
            }
            if (!Loader.isModLoaded("buildingbricks")) {
                throw new RecoveryAbort("Cannot safely recover " + key
                        + ". Restore BuildingBricks and its material add-ons, then load a backup. Upgrade was stopped.",
                        null);
            }
        }

        return target;
    }

    public static int nibble(byte[] packedValues, int index) {
        // Legacy sections pack two four-bit values into each byte, low nibble first.
        return packedValues.length == 0 ? 0 : (packedValues[index >> 1] >> ((index & 1) * 4)) & 15;
    }

    public static void setNibble(byte[] packedValues, int index, int value) {
        int shift = (index & 1) * 4;

        packedValues[index >> 1] = (byte) ((packedValues[index >> 1] & ~(15 << shift)) | ((value & 15) << shift));
    }

    public static synchronized void report(World world) {
        if (!recoveryActive()) {
            return;
        }

        PiecesWorldState state = PiecesWorldState.get(world);

        for (Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry : pendingItems.entrySet()) {
            state.addItems(entry.getKey(), entry.getValue().getAndSet(0));
        }
        synchronized (unresolved) {
            for (Map.Entry<String, Long> entry : unresolved.entrySet()) {
                state.unresolved.put(entry.getKey(),
                        state.unresolved.getOrDefault(entry.getKey(), 0L) + entry.getValue());
            }

            unresolved.clear();
        }

        state.markDirty();

        File destination = new File(world.getSaveHandler().getWorldDirectory(),
                "serverconfig/skysbuildingpieces-migration-report.txt");

        destination.getParentFile().mkdirs();

        StringBuilder reportText = new StringBuilder("Sky's Building Pieces ").append(SkysBuildingPieces.VERSION)
                .append("\nForced replacement: ").append(SkysBuildingPieces.forceReplace).append("\nVanilla blocks: ")
                .append(state.blocks).append("\nVanilla items: ").append(state.items).append("\nVanilla chunks: ")
                .append(state.chunks).append('\n');

        for (Map.Entry<String, PiecesWorldState.Totals> entry : state.modules.entrySet()) {
            reportText.append(entry.getKey()).append(" blocks: ").append(entry.getValue().blocks).append("\n")
                    .append(entry.getKey()).append(" items: ").append(entry.getValue().items).append("\n")
                    .append(entry.getKey()).append(" chunks: ").append(entry.getValue().chunks).append('\n');
        }

        reportText.append("Unresolved encounters (including historical reports; left unchanged at encounter):\n");

        for (Map.Entry<String, Long> entry : state.unresolved.entrySet()) {
            reportText.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
        }
        try {
            Files.write(destination.toPath(), reportText.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new RecoveryAbort("Cannot write migration report", failure);
        }
    }

    private static final class Conversion {
        final NBTTagCompound section, tile;
        final int index;
        final IBlockState state;
        final String module;

        Conversion(NBTTagCompound section, int index, IBlockState state, NBTTagCompound tile, String module) {
            this.section = section;
            this.index = index;
            this.state = state;
            this.tile = tile;
            this.module = module;
        }
    }

    /** An Error deliberately bypasses Forge's catch-and-regenerate chunk fallback. */
    public static final class RecoveryAbort extends Error {
        public RecoveryAbort(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
