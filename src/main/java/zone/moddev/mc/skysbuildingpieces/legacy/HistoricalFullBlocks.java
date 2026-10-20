package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Exact retired-material mappings contributed by a catalogue, not general substitutions. */
public final class HistoricalFullBlocks {
    public static final String MODULE = "retired_bop_rocks";
    private static final Map<String, String[]> MATERIALS = new LinkedHashMap<>();
    private static final Map<String, String> HOLDERS = new LinkedHashMap<>();
    private static final Map<String, java.util.function.IntFunction<IBlockState>> STATES = new LinkedHashMap<>();
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
            java.util.function.IntFunction<IBlockState> states) {
        if (LegacyBridge.ready || historical(historicalId) || HOLDERS.containsKey(holderId) || states == null
                || module == null || module.isEmpty()) {
            throw new IllegalStateException("Invalid or late historical block mapping: " + historicalId);
        }

        STATES.put(historicalId, states);
        HOLDERS.put(holderId, historicalId);
        MODULES.put(historicalId, module);
    }

    public static boolean historical(String registryId) {
        return MODULES.containsKey(registryId);
    }

    public static String module(String registryId) {
        return MODULES.get(HOLDERS.getOrDefault(registryId, registryId));
    }

    public static boolean hasSource(Map<Integer, String> savedIds) {
        return savedIds.values().stream().anyMatch(HistoricalFullBlocks::historical);
    }

    public static IBlockState resolve(String registryId, int meta) {
        String name = HOLDERS.getOrDefault(registryId, registryId);
        String[] materials = MATERIALS.get(name);

        if (STATES.containsKey(name)) {
            IBlockState state = meta >= 0 && meta < 16 ? STATES.get(name).apply(meta) : null;

            if (state == null || state.getBlock() instanceof net.minecraft.block.BlockAir
                    || state.getBlock().hasTileEntity(state)) {
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

        IBlockState state = Pieces.nativeState(Catalogue.INSTANCE.materials.get(materials[meta]), "full");

        if (state == null || state.getBlock().hasTileEntity(state)) {
            throw new LegacyBridge.RecoveryAbort("No safe full-block target for " + name + " / " + meta, null);
        }

        return state;
    }

    public static void prepareChunk(NBTTagCompound root, Map<Integer, String> savedIds) {
        if (!hasSource(savedIds)) {
            return;
        }

        NBTTagCompound level = root.getCompoundTag("Level");
        NBTTagCompound coverage = level.getCompoundTag("skysbuildingpieces_coverage");

        Set<String> pending = new LinkedHashSet<>();

        for (String name : savedIds.values()) {
            if (historical(name) && coverage.getInteger(module(name)) < 1) {
                pending.add(module(name));
            }
        }
        if (pending.isEmpty()) {
            return;
        }

        List<Change> changes = new ArrayList<>();
        NBTTagList sections = level.getTagList("Sections", 10);

        for (int s = 0; s < sections.tagCount(); s++) {
            NBTTagCompound section = sections.getCompoundTagAt(s);
            byte[] blockIdLowBytes = section.getByteArray("Blocks");
            byte[] high = section.getByteArray("Add");
            byte[] meta = section.getByteArray("Data");

            if (blockIdLowBytes.length != 4096 || meta.length != 2048 || high.length != 0 && high.length != 2048) {
                throw new LegacyBridge.RecoveryAbort("Malformed retired-rock section", null);
            }
            for (int blockIndex = 0; blockIndex < 4096; blockIndex++) {
                int blockId = (blockIdLowBytes[blockIndex] & 255) | (LegacyBridge.nibble(high, blockIndex) << 8);
                Block block = Block.getBlockById(blockId);
                String name = block == null || block.getRegistryName() == null ? ""
                        : block.getRegistryName().toString();

                if (!HOLDERS.containsKey(name)) {
                    if (block == null || block == zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces.HOLDER
                            || block instanceof net.minecraft.block.BlockAir) {
                        name = savedIds.get(blockId);
                    } else {
                        continue;
                    }
                }
                if (!pending.contains(module(name))) {
                    continue;
                }

                IBlockState target = resolve(name, LegacyBridge.nibble(meta, blockIndex));

                if (target != null) {
                    changes.add(new Change(section, blockIndex, target, module(name)));
                }
            }
        }
        // Resolve every variant before changing the chunk's raw storage.
        for (Change change : changes) {
            int blockId = Block.getIdFromBlock(change.state.getBlock());
            byte[] blockIdLowBytes = change.section.getByteArray("Blocks");
            byte[] high = change.section.getByteArray("Add");
            byte[] meta = change.section.getByteArray("Data");

            blockIdLowBytes[change.index] = (byte) blockId;

            if (high.length == 0 && blockId > 255) {
                high = new byte[2048];
            }
            if (high.length != 0) {
                LegacyBridge.setNibble(high, change.index, blockId >> 8);
                change.section.setByteArray("Add", high);
            }

            LegacyBridge.setNibble(meta, change.index, change.state.getBlock().getMetaFromState(change.state));
            change.section.setByteArray("Blocks", blockIdLowBytes);
            change.section.setByteArray("Data", meta);
        }

        NBTTagCompound deltas = new NBTTagCompound();

        for (String module : pending) {
            coverage.setInteger(module, 1);
            deltas.setLong(module, 0);
        }
        for (Change change : changes) {
            deltas.setLong(change.module, deltas.getLong(change.module) + 1);
        }

        level.setTag("skysbuildingpieces_coverage", coverage);

        if (pending.contains(MODULE)) {
            root.setLong("skysbuildingpieces_retired_delta", deltas.getLong(MODULE));
            deltas.removeTag(MODULE);
        }
        if (!deltas.isEmpty()) {
            root.setTag("skysbuildingpieces_historical_delta", deltas);
        }
    }

    private static final class Change {
        final NBTTagCompound section;
        final int index;
        final IBlockState state;
        final String module;

        Change(NBTTagCompound section, int index, IBlockState state, String module) {
            this.section = section;
            this.index = index;
            this.state = state;
            this.module = module;
        }
    }
}
