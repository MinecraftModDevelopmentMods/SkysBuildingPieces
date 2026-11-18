package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;

import java.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Exact retired-material mappings contributed by a catalogue, not general substitutions. */
public final class HistoricalFullBlocks {
    public static final String MODULE = "retired_bop_rocks";
    private static final Map<String, String[]> MATERIALS = new LinkedHashMap<>();
    private static final Map<String, String> HOLDERS = new LinkedHashMap<>();
    private static final Map<String, java.util.function.IntFunction<BlockState>> STATES = new LinkedHashMap<>();
    private static final Map<String, String> MODULES = new LinkedHashMap<>();

    private HistoricalFullBlocks() {
    }

    public static void register(String historicalId, String holderId, String... materials) {
        if (LegacyBridge.ready || materials.length < 1 || materials.length > 16 || historical(historicalId)
                || HOLDERS.containsKey(holderId)) {
            throw new IllegalStateException("Invalid or late retired-material mapping: " + historicalId);
        }

        boolean assigned = false;

        for (String material : materials) {
            if (material != null) {
                assigned = true;

                if (!Catalogue.INSTANCE.materials.containsKey(material)) {
                    throw new IllegalArgumentException("Unknown retired material: " + material);
                }
            }
        }
        if (!assigned) {
            throw new IllegalArgumentException("Retired-material palette is empty");
        }

        MATERIALS.put(historicalId, materials.clone());
        HOLDERS.put(holderId, historicalId);
        MODULES.put(historicalId, MODULE);
    }

    /** Internal early-recovery extension for retired blocks outside the piece catalogue. */
    public static void registerStates(String historicalId, String holderId, String module,
            java.util.function.IntFunction<BlockState> states) {
        if (LegacyBridge.ready || historical(historicalId) || HOLDERS.containsKey(holderId) || states == null
                || module == null || module.isEmpty()) {
            throw new IllegalStateException("Invalid or late historical block mapping: " + historicalId);
        }

        STATES.put(historicalId, states);
        HOLDERS.put(holderId, historicalId);
        MODULES.put(historicalId, module);
    }

    public static boolean historical(String registryId) {
        return MODULES.containsKey(registryId) || HOLDERS.containsKey(registryId);
    }

    public static String module(String registryId) {
        String direct = MODULES.get(registryId);

        return direct != null ? direct : MODULES.get(HOLDERS.getOrDefault(registryId, registryId));
    }

    public static boolean recoveryHolder(String registryId) {
        return HOLDERS.containsKey(registryId) && !registryId.equals(HOLDERS.get(registryId));
    }

    static boolean sameSource(String first, String second) {
        return HOLDERS.getOrDefault(first, first).equals(HOLDERS.getOrDefault(second, second));
    }

    /** A later flattening pass must also visit holders left by an earlier recovery module. */
    public static void registerHolderCoverage(String holderId, String module) {
        if (LegacyBridge.ready || !recoveryHolder(holderId) || MODULES.containsKey(holderId) || module == null
                || module.isEmpty()) {
            throw new IllegalStateException("Invalid or late recovery-holder coverage: " + holderId);
        }

        MODULES.put(holderId, module);
    }

    public static void registerFlatteningCoverage(String sourceId, String module) {
        if (LegacyBridge.ready || !MODULES.containsKey(sourceId) || module == null || module.isEmpty()) {
            throw new IllegalStateException("Invalid or late flattening coverage: " + sourceId);
        }

        MODULES.put(sourceId, module);
    }

    public static boolean hasSource(Map<Integer, String> savedIds) {
        return savedIds.values().stream().anyMatch(HistoricalFullBlocks::historical);
    }

    public static BlockState resolve(String registryId, int meta) {
        String name = HOLDERS.getOrDefault(registryId, registryId);
        String[] materials = MATERIALS.get(name);

        if (STATES.containsKey(name)) {
            BlockState state = meta >= 0 && meta < 16 ? STATES.get(name).apply(meta) : null;

            if (state == null || state.getBlock() instanceof net.minecraft.world.level.block.AirBlock
                    || state.hasBlockEntity()) {
                throw new LegacyBridge.RecoveryAbort("World upgrade stopped: no safe target for " + name + " / " + meta
                        + ". Restore the original modpack and use a backup.", null);
            }

            return state;
        }
        if (materials == null) {
            return null;
        }
        if (meta < 0 || meta >= materials.length || materials[meta] == null) {
            throw new LegacyBridge.RecoveryAbort("World upgrade stopped: unassigned retired-rock metadata " + name
                    + " / " + meta + ". Restore the original modpack and use a backup.", null);
        }

        BlockState state = Pieces.nativeState(Catalogue.INSTANCE.materials.get(materials[meta]), "full");

        if (state == null || state.hasBlockEntity()) {
            throw new LegacyBridge.RecoveryAbort("No safe full-block target for " + name + " / " + meta, null);
        }

        return state;
    }

    public static void prepareChunk(CompoundTag root, Map<Integer, String> savedIds) {
        if (!hasSource(savedIds)) {
            return;
        }

        CompoundTag level = root.getCompoundOrEmpty("Level");
        CompoundTag coverage = level.getCompoundOrEmpty("skysbuildingpieces_coverage");

        Set<String> pending = new LinkedHashSet<>();

        for (String name : savedIds.values()) {
            if (historical(name) && coverage.getIntOr(module(name), 0) < 1) {
                pending.add(module(name));
            }
        }
        if (pending.isEmpty()) {
            return;
        }

        List<Change> changes = new ArrayList<>();
        ListTag sections = level.getListOrEmpty("Sections");

        for (int s = 0; s < sections.size(); s++) {
            CompoundTag section = sections.getCompoundOrEmpty(s);
            byte[] blockIdLowBytes = section.getByteArray("Blocks").orElseGet(() -> new byte[0]);
            byte[] high = section.getByteArray("Add").orElseGet(() -> new byte[0]);
            byte[] meta = section.getByteArray("Data").orElseGet(() -> new byte[0]);

            if (blockIdLowBytes.length != 4096 || meta.length != 2048 || high.length != 0 && high.length != 2048) {
                throw new LegacyBridge.RecoveryAbort("Malformed retired-rock section", null);
            }
            for (int blockIndex = 0; blockIndex < 4096; blockIndex++) {
                int blockId = (blockIdLowBytes[blockIndex] & 255) | (LegacyBridge.nibble(high, blockIndex) << 8);
                String name = savedIds.get(blockId);

                if (!pending.contains(module(name))) {
                    continue;
                }

                BlockState target = resolve(name, LegacyBridge.nibble(meta, blockIndex));

                if (target != null) {
                    changes.add(new Change(section, blockIndex, target, module(name)));
                }
            }
        }
        // Resolve every variant before changing the chunk's raw storage.
        for (Change change : changes) {
            int encoded = NumericStateBridge.encode(change.state);
            int blockId = encoded >>> 4;
            byte[] blockIdLowBytes = change.section.getByteArray("Blocks").orElseGet(() -> new byte[0]);
            byte[] high = change.section.getByteArray("Add").orElseGet(() -> new byte[0]);
            byte[] meta = change.section.getByteArray("Data").orElseGet(() -> new byte[0]);

            blockIdLowBytes[change.index] = (byte) blockId;

            if (high.length == 0 && blockId > 255) {
                high = new byte[2048];
            }
            if (high.length != 0) {
                LegacyBridge.setNibble(high, change.index, blockId >> 8);
                change.section.putByteArray("Add", high);
            }

            LegacyBridge.setNibble(meta, change.index, encoded & 15);
            change.section.putByteArray("Blocks", blockIdLowBytes);
            change.section.putByteArray("Data", meta);
        }

        CompoundTag deltas = new CompoundTag();

        for (String module : pending) {
            coverage.putInt(module, 1);
            deltas.putLong(module, 0);
        }
        for (Change change : changes) {
            deltas.putLong(change.module, deltas.getLongOr(change.module, 0L) + 1);
        }

        level.put("skysbuildingpieces_coverage", coverage);

        if (pending.contains(MODULE)) {
            root.putLong("skysbuildingpieces_retired_delta", deltas.getLongOr(MODULE, 0L));
            deltas.remove(MODULE);
        }
        if (!deltas.isEmpty()) {
            root.put("skysbuildingpieces_historical_delta", deltas);
        }
    }

    private static final class Change {
        final CompoundTag section;
        final int index;
        final BlockState state;
        final String module;

        Change(CompoundTag section, int index, BlockState state, String module) {
            this.section = section;
            this.index = index;
            this.state = state;
            this.module = module;
        }
    }
}
