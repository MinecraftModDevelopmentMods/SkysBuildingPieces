package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Keeps migration totals and preserves saved fields this version does not interpret. */
public final class PiecesWorldState extends SavedData {
    public static final String NAME = "skysbuildingpieces_world_state";
    // This is our own schema, including saves written before DataVersion was present.
    static final SavedDataType<PiecesWorldState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SkysBuildingPieces.ID, NAME), PiecesWorldState::new,
            CompoundTag.CODEC.xmap(PiecesWorldState::fromTag, state -> state.saveTag(new CompoundTag())), null);

    public long blocks, items, chunks;
    public final java.util.Map<String, Long> unresolved = new java.util.TreeMap<String, Long>();
    public final java.util.Map<String, Totals> modules = new java.util.TreeMap<String, Totals>();

    public static final class Totals {
        public long blocks, items, chunks;
    }

    private Totals totals(String module) {
        return modules.computeIfAbsent(module, key -> new Totals());
    }

    public void addBlocks(String module, long count) {
        if (module.equals("vanilla")) {
            blocks += count;
        } else {
            totals(module).blocks += count;
        }
    }

    public void addItems(String module, long count) {
        if (count == 0) {
            return;
        }
        if (module.equals("vanilla")) {
            items += count;
        } else {
            totals(module).items += count;
        }
    }

    public void addChunk(String module, long count) {
        addBlocks(module, count);

        if (module.equals("vanilla")) {
            chunks++;
        } else {
            totals(module).chunks++;
        }
    }

    private CompoundTag retained = new CompoundTag();

    public PiecesWorldState() {
        super();
    }

    public PiecesWorldState(String name) {
        super();
    }

    public void load(CompoundTag compoundTag) {
        int schema = compoundTag.getIntOr("schema_version", 0);

        if (schema != 1) {
            throw new IllegalStateException("Unsupported building-pieces state schema " + schema);
        }

        blocks = compoundTag.getLongOr("vanilla_blocks", 0L);
        items = compoundTag.getLongOr("vanilla_items", 0L);
        chunks = compoundTag.getLongOr("vanilla_chunks", 0L);
        // Keep unknown fields, including data from later add-ons, when writing this state back.
        retained = compoundTag.copy();
        unresolved.clear();

        CompoundTag issues = compoundTag.getCompoundOrEmpty("unresolved");

        for (String key : issues.keySet()) {
            unresolved.put(key, issues.getLongOr(key, 0L));
        }

        modules.clear();

        CompoundTag savedModules = compoundTag.getCompoundOrEmpty("modules");

        for (String key : savedModules.keySet()) {
            CompoundTag saved = savedModules.getCompoundOrEmpty(key);
            Totals moduleTotals = totals(key);

            moduleTotals.blocks = saved.getLongOr("blocks", 0L);
            moduleTotals.items = saved.getLongOr("items", 0L);
            moduleTotals.chunks = saved.getLongOr("chunks", 0L);
        }
    }

    public CompoundTag saveTag(CompoundTag compoundTag) {
        compoundTag.merge(retained);
        compoundTag.putInt("schema_version", 1);
        compoundTag.putLong("vanilla_blocks", blocks);
        compoundTag.putLong("vanilla_items", items);
        compoundTag.putLong("vanilla_chunks", chunks);

        CompoundTag issues = new CompoundTag();

        for (java.util.Map.Entry<String, Long> entry : unresolved.entrySet()) {
            issues.putLong(entry.getKey(), entry.getValue());
        }

        compoundTag.put("unresolved", issues);

        CompoundTag savedModules = compoundTag.getCompoundOrEmpty("modules");

        for (java.util.Map.Entry<String, Totals> entry : modules.entrySet()) {
            CompoundTag saved = savedModules.getCompoundOrEmpty(entry.getKey());

            saved.putLong("blocks", entry.getValue().blocks);
            saved.putLong("items", entry.getValue().items);
            saved.putLong("chunks", entry.getValue().chunks);
            savedModules.put(entry.getKey(), saved);
        }
        if (!savedModules.isEmpty()) {
            compoundTag.put("modules", savedModules);
        }

        return compoundTag;
    }

    public static PiecesWorldState get(Level world) {
        if (world.getServer() != null) {
            world = world.getServer().getLevel(Level.OVERWORLD);
        }
        if (!(world instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Building-pieces migration state requires a server world");
        }

        relocateLegacyState(serverLevel.getServer().getWorldPath(LevelResource.ROOT));
        return serverLevel.getDataStorage().computeIfAbsent(TYPE);
    }

    static synchronized void relocateLegacyState(Path worldRoot) {
        Path source = worldRoot.resolve("data").resolve(NAME + ".dat");
        Path destination = worldRoot.resolve("dimensions/minecraft/overworld/data")
                .resolve(SkysBuildingPieces.ID).resolve(NAME + ".dat");
        if (!Files.isRegularFile(source) || Files.exists(destination)) {
            return;
        }

        Path temporary = destination.resolveSibling(destination.getFileName() + ".migration.tmp");
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            SkysBuildingPieces.LOGGER.info("Preserved the Building Pieces migration state in the namespaced data folder");
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw new LegacyBridge.RecoveryAbort("Could not preserve the Building Pieces migration state", failure);
        }
    }

    private static PiecesWorldState fromTag(CompoundTag tag) {
        PiecesWorldState state = new PiecesWorldState();
        state.load(tag);
        return state;
    }
}
