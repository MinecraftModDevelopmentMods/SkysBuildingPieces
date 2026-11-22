package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Keeps migration totals and preserves saved fields this version does not interpret. */
public final class PiecesWorldState extends SavedData {
    public static final String NAME = "skysbuildingpieces_world_state";
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
        int schema = compoundTag.getInt("schema_version");

        if (schema != 1) {
            throw new IllegalStateException("Unsupported building-pieces state schema " + schema);
        }

        blocks = compoundTag.getLong("vanilla_blocks");
        items = compoundTag.getLong("vanilla_items");
        chunks = compoundTag.getLong("vanilla_chunks");
        // Keep unknown fields, including data from later add-ons, when writing this state back.
        retained = compoundTag.copy();
        unresolved.clear();

        CompoundTag issues = compoundTag.getCompound("unresolved");

        for (String key : issues.getAllKeys()) {
            unresolved.put(key, issues.getLong(key));
        }

        modules.clear();

        CompoundTag savedModules = compoundTag.getCompound("modules");

        for (String key : savedModules.getAllKeys()) {
            CompoundTag saved = savedModules.getCompound(key);
            Totals moduleTotals = totals(key);

            moduleTotals.blocks = saved.getLong("blocks");
            moduleTotals.items = saved.getLong("items");
            moduleTotals.chunks = saved.getLong("chunks");
        }
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
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

        CompoundTag savedModules = compoundTag.getCompound("modules");

        for (java.util.Map.Entry<String, Totals> entry : modules.entrySet()) {
            CompoundTag saved = savedModules.getCompound(entry.getKey());

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
        if (!(world instanceof net.minecraft.server.level.ServerLevel)) {
            throw new IllegalStateException("Building-pieces migration state requires a server world");
        }

        return ((net.minecraft.server.level.ServerLevel) world).getDataStorage()
                .computeIfAbsent(PiecesWorldState::fromTag, PiecesWorldState::new, NAME);
    }

    private static PiecesWorldState fromTag(CompoundTag tag) {
        PiecesWorldState state = new PiecesWorldState();
        state.load(tag);
        return state;
    }
}
