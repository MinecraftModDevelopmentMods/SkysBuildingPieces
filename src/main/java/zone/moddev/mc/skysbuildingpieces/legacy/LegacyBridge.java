package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
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
        return ready
                && LegacyMapping.enabled(ModList.get().isLoaded("buildingbricks"), SkysBuildingPieces.forceReplace);
    }

    public static boolean recoveryActive() {
        return active() || ready && HistoricalFullBlocks.hasSource(savedIds);
    }

    private static CompoundTag registrySnapshot = new CompoundTag();
    private static CompoundTag laterRegistrySnapshot = new CompoundTag();
    private static boolean registryValidated;
    private static Path capturedDirectory;
    private static CompoundTag capturedRegistry = new CompoundTag();
    private static boolean persistenceRegistered;

    public static synchronized void registerPersistence() {
        if (!persistenceRegistered) {
            // ForgeHooks captures raw data; this event starts recovery once the
            // server and its live registries are available.
            MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> start(event.getServer()));
            persistenceRegistered = true;
        }
    }

    /** Capture the untouched registry and biome source before an early level save. */
    /** Capture the legacy snapshot before Forge processes its modern level data. */
    public static void captureLegacyLevelData(CompoundTag root,
            net.minecraft.world.level.storage.LevelStorageSource.LevelDirectory directory) {
        if (directory != null) {
            captureLegacyLevelData(root, directory.path());
        }
    }

    public static synchronized void captureLegacyLevelData(CompoundTag root, Path directory) {
        if (root == null || directory == null) {
            return;
        }
        Path normalized = directory.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalized) || normalized.getFileName().toString().equals("level.dat")
                || normalized.getFileName().toString().equals("level.dat_old")) {
            normalized = normalized.getParent();
        }
        capturedDirectory = normalized;
        capturedRegistry = root.getCompound("FML").copy();
        SourceLevelData.capture(normalized);
    }

    /** Preserve the complete uppercase snapshot for chunks not opened on this start. */
    public static synchronized void writeLegacyLevelData(CompoundTag root) {
        if (root == null) {
            return;
        }
        if (!capturedRegistry.isEmpty()) {
            root.put("FML", capturedRegistry.copy());
        } else if (!registrySnapshot.isEmpty() && worldDirectory != null
                && worldDirectory.toPath().toAbsolutePath().normalize().equals(capturedDirectory)) {
            root.put("FML", registrySnapshot.copy());
        }
    }

    /** Returns a copy of the original biome table and save version for this world. */
    public static CompoundTag sourceBiomeSnapshot(MinecraftServer server) {
        return SourceLevelData.forWorld(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
    }

    public static synchronized void start(MinecraftServer server) {
        resetRecovery();

        File level = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE).toFile();

        worldDirectory = level.getParentFile();

        File sidecar = new File(worldDirectory, "data/skysbuildingpieces_legacy_registry.dat");
        CompoundTag previous = new CompoundTag();

        try {
            if (sidecar.isFile()) {
                try (InputStream input = new FileInputStream(sidecar)) {
                    previous = NbtIo.readCompressed(input);
                }

                registrySnapshot = previous.contains("FML", 10) ? previous.getCompound("FML").copy() : previous.copy();
                laterRegistrySnapshot = previous.getCompound("source_registry").copy();

                if (laterRegistrySnapshot.isEmpty() && level.isFile()) {
                    try (InputStream input = new FileInputStream(level)) {
                        CompoundTag current = NbtIo.readCompressed(input);

                        if (current.getCompound("Data").getInt("DataVersion") <= 1343) {
                            laterRegistrySnapshot = current.getCompound("FML").copy();
                        }
                    }
                }
                if (laterRegistrySnapshot.isEmpty()
                        && !capturedRegistry.equals(registrySnapshot)
                        && worldDirectory.toPath().toAbsolutePath().normalize().equals(capturedDirectory)) {
                    // Forge can save level.dat before this event. The early hook still has
                    // the source registry, including pieces added after the older sidecar.
                    laterRegistrySnapshot = capturedRegistry.copy();
                }
            } else if (level.isFile()) {
                try (InputStream input = new FileInputStream(level)) {
                    registrySnapshot = NbtIo.readCompressed(input).getCompound("FML").copy();
                }
            }

            // The native loader supplies this before any save can discard unknown root data.
            // Keep it outside startup's recovery reset, and bind it to this exact world.
            if (registrySnapshot.isEmpty() && worldDirectory.toPath().toAbsolutePath().normalize().equals(capturedDirectory)) {
                registrySnapshot = capturedRegistry.copy();
            }

            savedIds.putAll(LegacyRegistrySources.merge(registrySnapshot, laterRegistrySnapshot));
            HistoricalBiomes.begin(laterRegistrySnapshot.isEmpty() ? registrySnapshot : laterRegistrySnapshot);

            if (!savedIds.isEmpty()) {
                NumericStateBridge.begin(savedIds, previous.getList("staging", 10));
                // Check every source before writing a sidecar or allowing destructive loading.
                LegacyPreflight.validate(worldDirectory, savedIds);
                registryValidated = true;
                persistRegistry();
            }
        } catch (IOException failure) {
            throw new RecoveryAbort("Cannot read the original registry snapshot; restore a backup", failure);
        }
    }

    static synchronized void persistRegistry() {
        if (!registryValidated || worldDirectory == null || registrySnapshot.isEmpty()) {
            return;
        }

        Path destination = new File(worldDirectory, "data/skysbuildingpieces_legacy_registry.dat").toPath();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");

        try {
            Files.createDirectories(destination.getParent());

            CompoundTag contents = new CompoundTag();

            contents.put("FML", registrySnapshot.copy());

            if (!laterRegistrySnapshot.isEmpty()) {
                contents.put("source_registry", laterRegistrySnapshot.copy());
            }

            contents.put("staging", NumericStateBridge.saveStaging());

            try (OutputStream output = Files.newOutputStream(temporary)) {
                NbtIo.writeCompressed(contents, output);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new RecoveryAbort("Cannot preserve the original registry snapshot", failure);
        }
    }

    /** The coremod calls this before the chunk enters vanilla data fixing. */
    public static void prepareLegacyChunk(CompoundTag root) {
        if (!ready || root == null) {
            return;
        }

        boolean savedInventory = prepareStacks(root);

        prepareNamedReplacements(root);

        if (root.getInt("DataVersion") > 1343 || !root.contains("Level", 10)) {
            return;
        }

        CompoundTag level = root.getCompound("Level");
        ListTag sections = level.getList("Sections", 10);
        boolean savedBiomes = HistoricalBiomes.prepare(root);
        boolean relevant = savedInventory || savedBiomes;
        boolean legacyPieces = false;

        for (int index = 0; index < sections.size(); index++) {
            CompoundTag section = sections.getCompound(index);
            byte[] low = section.getByteArray("Blocks");
            byte[] high = section.getByteArray("Add");

            if (low.length != 4096) {
                continue;
            }
            for (int block = 0; block < 4096; block++) {
                int number = low[block] & 255 | nibble(high, block) << 8;
                String name = savedIds.get(number);

                legacyPieces |= name != null && active() && LegacyMapping.owned(name);
                relevant |= name != null && (palette(name) != null || active() && LegacyMapping.owned(name)
                        || HistoricalFullBlocks.historical(name)) || NumericStateBridge.staged(number);
            }
        }
        if (!relevant) {
            return;
        }

        prepareChunk(null, root, legacyPieces);
        carryDeltas(root, level);
        queueShapeRefresh(level);
        level.putBoolean("TerrainPopulated", true);
        level.putBoolean("LightPopulated", true);
        level.putBoolean("SkysBuildingPiecesPreserveChunk", true);
    }

    private static void queueShapeRefresh(CompoundTag level) {
        ListTag refresh = level.getList("PostProcessing", 9);

        while (refresh.size() < 16) {
            refresh.add(new ListTag());
        }

        ListTag sections = level.getList("Sections", 10);

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            byte[] blocks = section.getByteArray("Blocks");

            if (blocks.length != 4096) {
                continue;
            }

            int sectionY = section.getByte("Y") & 255;

            if (sectionY >= 16) {
                throw new RecoveryAbort("Legacy piece section is outside the world height", null);
            }

            byte[] high = section.getByteArray("Add");
            byte[] metadata = section.getByteArray("Data");
            ListTag positions = refresh.getList(sectionY);
            Set<Short> queued = new HashSet<>();

            for (int index = 0; index < positions.size(); index++) {
                queued.add(positions.getShort(index));
            }
            for (int index = 0; index < 4096; index++) {
                int number = blocks[index] & 255 | nibble(high, index) << 8;
                String saved = savedIds.get(number);
                BlockState staged = NumericStateBridge.stagedState(number << 4 | nibble(metadata, index));

                if ((saved == null || palette(saved) == null) && staged == null) {
                    continue;
                }

                // Native post-processing packs X, Y, Z rather than the section array's X, Z, Y order. Existing
                // vanilla/mod entries stay intact.
                short packed = (short) ((index & 15) | (index >> 8 & 15) << 4 | (index >> 4 & 15) << 8);

                if (queued.add(packed)) {
                    positions.add(ShortTag.valueOf(packed));
                }
            }
        }

        level.put("PostProcessing", refresh);
    }

    public static CompoundTag finalizeLegacyChunk(CompoundTag root) {
        finalizeNbt(root);

        if (root != null) {
            HistoricalBiomes.finish(root);

            CompoundTag level = MigrationEvents.chunkLevel(root);

            // Older chunks wrap their contents in Level. Since 1.18 the chunk
            // itself carries these fields, so there is nothing to move.
            if (level != root) {
                carryDeltas(root, level);
            }

            if (level.getBoolean("SkysBuildingPiecesPreserveChunk")) {
                level.putString("Status", "full");
                level.remove("SkysBuildingPiecesPreserveChunk");
            }
        }

        return root;
    }

    private static void carryDeltas(CompoundTag root, CompoundTag level) {
        // Keep pending accounting inside Level while vanilla fixes the enclosing chunk.
        // The native load handler unwraps that same data from Forge's chunk root.
        String[] keys = { "skysbuildingpieces_delta", "skysbuildingpieces_additional_vanilla_delta",
                "skysbuildingpieces_module_delta", "skysbuildingpieces_additional_module_delta",
                "skysbuildingpieces_historical_delta", "skysbuildingpieces_retired_delta" };

        for (String key : keys) {
            if (root.contains(key) && level.contains(key) && !level.get(key).equals(root.get(key))) {
                throw new RecoveryAbort("Conflicting pending migration accounting: " + key, null);
            }
        }
        for (String key : keys) {
            if (root.contains(key)) {
                level.put(key, root.get(key));
                root.remove(key);
            }
        }
    }

    /** Also covers player and nested inventory data before the native item fixer runs. */
    public static void prepareNbt(CompoundTag root) {
        if (ready && root != null) {
            prepareStack(root);
        }
    }

    /** Finish identities which would otherwise be renamed a second time by vanilla. */
    public static CompoundTag finalizeNbt(CompoundTag root) {
        return finalizeNbt(root, Integer.MAX_VALUE);
    }

    public static CompoundTag finalizeNbt(CompoundTag root, int targetVersion) {
        if (ready && root != null) {
            HistoricalItems.finish(root, targetVersion);
        }

        return root;
    }

    static PieceBlock palette(String name) {
        String replacement = zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.replacementId(name);
        String identifier = replacement == null ? name : replacement;
        PieceBlock block = Pieces.BLOCKS.get(identifier);

        if (block == null && identifier.startsWith(SkysBuildingPieces.ID + ":")) {
            block = Pieces.BLOCKS.get(identifier.substring(SkysBuildingPieces.ID.length() + 1));
        }

        return block;
    }

    /** Named-state recovery stays separate from the pre-1.13 numeric bridge. */
    private static void prepareNamedReplacements(CompoundTag root) {
        if (!Catalogue.INSTANCE.hasReplacements()) {
            return;
        }

        ListTag sections = root.getCompound("Level").getList("Sections", 10);
        List<Runnable> changes = new ArrayList<>();

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            ListTag states = sections.getCompound(sectionIndex).getList("Palette", 10);

            for (int paletteIndex = 0; paletteIndex < states.size(); paletteIndex++) {
                CompoundTag saved = states.getCompound(paletteIndex);
                PieceBlock block = palette(saved.getString("Name"));

                if (block == null) {
                    continue;
                }
                try {
                    int metadata = saved.getCompound("Properties").contains("meta", 8)
                            ? Integer.parseInt(saved.getCompound("Properties").getString("meta"))
                            : 0;

                    if (metadata < 0 || metadata > 15) {
                        throw new IllegalArgumentException("Invalid saved palette value " + metadata);
                    }

                    String material = block.palette.material(metadata);
                    Catalogue.MaterialDef definition = Catalogue.INSTANCE.materials.get(material);

                    if (definition.replacement() == null && definition
                            .block(block.palette.shape.name().toLowerCase(java.util.Locale.ROOT)) == null) {
                        continue;
                    }

                    BlockState original = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), saved);
                    BlockState target = Pieces.canonicalState(original);
                    CompoundTag converted = saved.copy();
                    CompoundTag nativeState = NbtUtils.writeBlockState(target);

                    converted.putString("Name", nativeState.getString("Name"));

                    if (nativeState.contains("Properties", 10)) {
                        converted.put("Properties", nativeState.getCompound("Properties"));
                    } else {
                        converted.remove("Properties");
                    }
                    NumericStateBridge.preserveNativeName(converted);

                    final int index = paletteIndex;

                    changes.add(() -> states.set(index, converted));
                } catch (RuntimeException failure) {
                    throw new RecoveryAbort("Cannot recover retired named piece " + saved.getString("Name") + ": "
                            + failure.getMessage(), failure);
                }
            }
        }

        // Resolve the entire named palette before changing it.
        changes.forEach(Runnable::run);
    }

    public static synchronized void stop() {
        resetRecovery();
        capturedDirectory = null;
        capturedRegistry = new CompoundTag();
        SourceLevelData.clear();
    }

    private static void resetRecovery() {
        NumericStateBridge.reset();
        registrySnapshot = new CompoundTag();
        laterRegistrySnapshot = new CompoundTag();
        HistoricalBiomes.clear();
        HistoricalItems.clearNativeNames();
        registryValidated = false;
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

    public static void prepareStack(CompoundTag stack) {
        prepareStacks(stack);
    }

    private static boolean prepareStacks(CompoundTag stack) {
        if (!ready) {
            return false;
        }

        CompoundTag prepared = HistoricalItems.prepareCopy(stack);
        List<StackChange> changes = new ArrayList<StackChange>();

        collectInventories(prepared, changes);
        applyStacks(changes);

        if (prepared != stack) {
            for (String key : new ArrayList<>(stack.getAllKeys())) {
                if (!prepared.contains(key)) {
                    stack.remove(key);
                }
            }
            for (String key : prepared.getAllKeys()) {
                stack.put(key, prepared.get(key));
            }
        }

        // A saved but unpopulated chunk can contain only inventories. Keep it complete after recovery just as we do a
        // chunk containing placed pieces; otherwise vanilla can regenerate the terrain and erase those containers.
        return prepared != stack || !changes.isEmpty();
    }

    private static void applyStacks(List<StackChange> changes) {
        // Resolve every nested target before changing any part of the stack.
        for (StackChange change : changes) {
            change.stack.putString("id", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(change.output.getItem()).toString());
            change.stack.remove("Damage");
            HistoricalItems.preserveNativeItem(change.stack);

            if (worldDirectory != null && change.module != null) {
                pendingItems.computeIfAbsent(change.module, key -> new java.util.concurrent.atomic.AtomicLong())
                        .addAndGet(change.stack.getByte("Count") & 255);
            }
        }
    }

    private static void collectStack(CompoundTag stack, List<StackChange> changes) {
        if (stack.contains("id", 8) && stack.contains("Count", 99)) {
            collectIdentity(stack, changes);
        }

        for (String key : stack.getAllKeys()) {
            if (!key.equals("retained_tiles")) {
                collectInventories(stack.get(key), changes);
            }
        }
    }

    private static void collectInventories(Tag value, List<StackChange> changes) {
        if (value instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) value;

            if (compound.contains("id", 8) && compound.contains("Count", 99)) {
                collectStack(compound, changes);
                return;
            }
            for (String key : compound.getAllKeys()) {
                if (!key.equals("retained_tiles")) {
                    collectInventories(compound.get(key), changes);
                }
            }
        } else if (value instanceof ListTag) {
            ListTag list = (ListTag) value;

            for (int entryIndex = 0; entryIndex < list.size(); entryIndex++) {
                collectInventories(list.get(entryIndex), changes);
            }
        }
    }

    private static void collectIdentity(CompoundTag stack, List<StackChange> changes) {
        String registry = stack.getString("id");
        PieceItem item = Pieces.pieceItem(registry);

        if (item != null) {
            if (Catalogue.INSTANCE.materials.get(item.material).replacement() != null) {
                BlockState canonical = Pieces.state(item.material, item.piece.palette.shape,
                        item.piece.palette.shape == Shape.SLAB ? 1 : 0);
                net.minecraft.world.item.ItemStack output = Pieces.stack(canonical, 1);

                if (output == null || output.isEmpty()) {
                    throw new RecoveryAbort("No inventory target for retired material " + item.material, null);
                }

                changes.add(new StackChange(stack, output, null));
                return;
            }
        }

        PieceBlock sourcePalette = palette(registry);

        if (sourcePalette != null) {
            int metadata = stack.getShort("Damage");
            net.minecraft.world.item.ItemStack output = Pieces.legacyItem(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(sourcePalette).toString(),
                    metadata, 1);

            if (output == null || output.isEmpty()) {
                throw new RecoveryAbort("No inventory target for " + registry + " / " + metadata, null);
            }

            changes.add(new StackChange(stack, output, null));
            return;
        }
        if (LegacyPreflight.skyOwned(registry) && !HistoricalFullBlocks.historical(registry)
                && !net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .containsKey(new net.minecraft.resources.ResourceLocation(registry))) {
            throw new RecoveryAbort("Cannot recover " + registry + ". Restore its Sky add-on before loading.", null);
        }

        // Flattened native items can retain the same registry name as an old block. Their metadata is gone; do not
        // convert or count them again.
        if (HistoricalFullBlocks.historical(registry) && !stack.contains("Damage", 99)
                && !HistoricalFullBlocks.recoveryHolder(registry) && net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .containsKey(new net.minecraft.resources.ResourceLocation(registry))) {
            return;
        }

        BlockState retired = HistoricalFullBlocks.resolve(registry, stack.getShort("Damage"));

        if (retired != null) {
            net.minecraft.world.item.ItemStack output = Pieces.stack(retired, 1);

            if (output == null || output.isEmpty()) {
                throw new RecoveryAbort("Recovered block has no item: " + registry, null);
            }

            changes.add(new StackChange(stack, output, HistoricalFullBlocks.module(registry)));
            return;
        }
        if (!active() || !LegacyMapping.owned(registry)) {
            return;
        }

        CompoundTag tag = stack.getCompound("tag");
        String material = LegacyMapping.material(tag.getString("material"));
        // Item damage is a dynamic material index, never a placement orientation.
        Shape shape = LegacyMapping.shape(registry, 0);
        BlockState target = resolve(registry, material, shape, shape == Shape.SLAB ? 1 : 0);

        if (target == null) {
            return;
        }

        net.minecraft.world.item.ItemStack output = Pieces.stack(target, 1);

        if (output == null) {
            throw new RecoveryAbort("Recovered piece has no item: " + registry + " / " + material, null);
        }

        changes.add(new StackChange(stack, output, Catalogue.INSTANCE.moduleFor(material)));
        // The saved material is retained as unrelated custom data; all other NBT and counts remain untouched.
    }

    private static final class StackChange {
        final CompoundTag stack;
        final net.minecraft.world.item.ItemStack output;
        final String module;

        StackChange(CompoundTag stack, net.minecraft.world.item.ItemStack output, String module) {
            this.stack = stack;
            this.output = output;
            this.module = module;
        }
    }

    public static void prepareChunk(Level world, CompoundTag root) {
        prepareChunk(world, root, true);
    }

    private static void prepareChunk(Level world, CompoundTag root, boolean legacyPieces) {
        if (ready) {
            // Some containers reject missing item IDs before constructing an ItemStack. Recover saved stacks before
            // those serializers run, including inventories with names other than vanilla's Items.
            CompoundTag stored = root.getCompound("Level");
            List<StackChange> changes = new ArrayList<StackChange>();

            collectInventories(stored.get("TileEntities"), changes);
            collectInventories(stored.get("Entities"), changes);
            applyStacks(changes);
        }
        if (ready) {
            HistoricalFullBlocks.prepareChunk(root, savedIds);
        }
        if (!active() || !legacyPieces) {
            return;
        }

        CompoundTag level = root.getCompound("Level");
        CompoundTag coverage = level.getCompound("skysbuildingpieces_coverage");
        Set<String> pendingModules = new LinkedHashSet<String>();

        for (String module : Catalogue.INSTANCE.modules.keySet()) {
            if (coverage.getInt(module) < 1) {
                pendingModules.add(module);
            }
        }

        boolean pendingDirt = coverage.getInt("dirt_vertical_slabs") < 1;

        ListTag tiles = level.getList("TileEntities", 10);
        ListTag sections = level.getList("Sections", 10);

        Map<Integer, CompoundTag> materialAt = new HashMap<Integer, CompoundTag>();

        for (int tileIndex = 0; tileIndex < tiles.size(); tileIndex++) {
            CompoundTag legacyTileData = tiles.getCompound(tileIndex);
            int index = (legacyTileData.getInt("x") & 15) | ((legacyTileData.getInt("z") & 15) << 4)
                    | (legacyTileData.getInt("y") << 8);

            materialAt.put(index, legacyTileData);
        }

        // Resolve every candidate before modifying any section or deleting a legacy tile.
        List<Conversion> decisions = new ArrayList<Conversion>();

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            byte[] blocks = section.getByteArray("Blocks");
            byte[] blockIdHighNibbles = section.getByteArray("Add");
            byte[] data = section.getByteArray("Data");

            if (blocks.length != 4096 || data.length != 2048
                    || blockIdHighNibbles.length != 0 && blockIdHighNibbles.length != 2048) {
                throw new RecoveryAbort("Malformed legacy chunk section", null);
            }
            for (int blockIndex = 0; blockIndex < 4096; blockIndex++) {
                int numeric = (blocks[blockIndex] & 255) | (nibble(blockIdHighNibbles, blockIndex) << 8);
                String registry = savedIds.get(numeric);

                if (registry == null || !LegacyMapping.owned(registry)) {
                    continue;
                }

                int tilePositionKey = (blockIndex & 255)
                        | (((section.getByte("Y") & 255) * 16 + (blockIndex >> 8)) << 8);
                CompoundTag legacyTileData = materialAt.get(tilePositionKey);
                String material = legacyTileData == null ? "" : legacyTileData.getString("material");
                String module = Catalogue.INSTANCE.moduleFor(LegacyMapping.material(material));
                Shape shape = LegacyMapping.shape(registry, nibble(data, blockIndex));
                BlockState target = resolve(registry, LegacyMapping.material(material), shape,
                        LegacyMapping.orientation(shape, nibble(data, blockIndex)));

                if (target != null) {
                    decisions.add(new Conversion(section, blockIndex, target, legacyTileData, module));
                }
            }
        }

        if (decisions.isEmpty() && pendingModules.isEmpty() && !pendingDirt) {
            return;
        }

        Set<CompoundTag> removed = Collections.newSetFromMap(new IdentityHashMap<CompoundTag, Boolean>());
        ListTag retainedTileData = coverage.getList("retained_tiles", 10);

        // Every target is now known. Commit the block states before removing their old material tiles.
        for (Conversion conversion : decisions) {
            int encoded = NumericStateBridge.encode(conversion.state);
            int blockId = encoded >>> 4;
            byte[] blockIdLowBytes = conversion.section.getByteArray("Blocks");

            blockIdLowBytes[conversion.index] = (byte) blockId;

            byte[] blockIdHighNibbles = conversion.section.getByteArray("Add");

            if (blockIdHighNibbles.length == 0 && blockId > 255) {
                blockIdHighNibbles = new byte[2048];
            }
            if (blockIdHighNibbles.length != 0) {
                setNibble(blockIdHighNibbles, conversion.index, blockId >> 8);
                conversion.section.putByteArray("Add", blockIdHighNibbles);
            }

            byte[] meta = conversion.section.getByteArray("Data");

            setNibble(meta, conversion.index, encoded & 15);
            conversion.section.putByteArray("Blocks", blockIdLowBytes);
            conversion.section.putByteArray("Data", meta);

            if (conversion.tile != null) {
                removed.add(conversion.tile);
                retainedTileData.add(conversion.tile.copy());
            }
        }

        ListTag retained = new ListTag();

        for (int tileIndex = 0; tileIndex < tiles.size(); tileIndex++) {
            if (!removed.contains(tiles.getCompound(tileIndex))) {
                retained.add(tiles.getCompound(tileIndex));
            }
        }

        level.put("TileEntities", retained);

        CompoundTag deltas = new CompoundTag();

        for (String module : pendingModules) {
            coverage.putInt(module, 1);
            deltas.putLong(module, 0);
        }
        if (pendingDirt) {
            coverage.putInt("dirt_vertical_slabs", 1);
        }
        for (Conversion conversion : decisions) {
            deltas.putLong(conversion.module, deltas.getLong(conversion.module) + 1);
        }

        coverage.put("retained_tiles", retainedTileData);
        level.put("skysbuildingpieces_coverage", coverage);

        if (pendingModules.contains("vanilla")) {
            root.putLong("skysbuildingpieces_delta", deltas.getLong("vanilla"));
        } else if (deltas.getLong("vanilla") > 0) {
            root.putLong("skysbuildingpieces_additional_vanilla_delta", deltas.getLong("vanilla"));
        }

        deltas.remove("vanilla");

        CompoundTag additional = new CompoundTag();

        for (String module : new ArrayList<>(deltas.getAllKeys())) {
            if (!pendingModules.contains(module)) {
                additional.putLong(module, deltas.getLong(module));
                deltas.remove(module);
            }
        }
        if (!additional.isEmpty()) {
            root.put("skysbuildingpieces_additional_module_delta", additional);
        }

        if (!deltas.isEmpty()) {
            root.put("skysbuildingpieces_module_delta", deltas);
        }
    }

    /** Keep unsupported pieces with their installed mod; stop absent-mod recovery rather than guess. */
    public static BlockState resolve(String registry, String material, Shape shape, int orientation) {
        BlockState target = LegacyMapping.allowedMaterial(material, shape) ? Pieces.state(material, shape, orientation)
                : null;

        if (target == null) {
            String key = registry + " / " + material + " / " + shape;

            synchronized (unresolved) {
                unresolved.put(key, unresolved.getOrDefault(key, 0L) + 1);
            }
            if (!ModList.get().isLoaded("buildingbricks")) {
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

    public static synchronized void report(Level world) {
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

        state.setDirty();

        File destination = new File(worldDirectory, "serverconfig/skysbuildingpieces-migration-report.txt");

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
        final CompoundTag section, tile;
        final int index;
        final BlockState state;
        final String module;

        Conversion(CompoundTag section, int index, BlockState state, CompoundTag tile, String module) {
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
