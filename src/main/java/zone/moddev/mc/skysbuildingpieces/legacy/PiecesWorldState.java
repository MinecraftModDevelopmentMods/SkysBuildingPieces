package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

/** Keeps migration totals and preserves saved fields this version does not interpret. */
public final class PiecesWorldState extends WorldSavedData {
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

    private NBTTagCompound retained = new NBTTagCompound();

    public PiecesWorldState() {
        super(NAME);
    }

    public PiecesWorldState(String name) {
        super(name);
    }

    public void readFromNBT(NBTTagCompound compoundTag) {
        int schema = compoundTag.getInteger("schema_version");

        if (schema != 1) {
            throw new IllegalStateException("Unsupported building-pieces state schema " + schema);
        }

        blocks = compoundTag.getLong("vanilla_blocks");
        items = compoundTag.getLong("vanilla_items");
        chunks = compoundTag.getLong("vanilla_chunks");
        // Keep unknown fields, including data from later add-ons, when writing this state back.
        retained = compoundTag.copy();
        unresolved.clear();

        NBTTagCompound issues = compoundTag.getCompoundTag("unresolved");

        for (String key : issues.getKeySet()) {
            unresolved.put(key, issues.getLong(key));
        }

        modules.clear();

        NBTTagCompound savedModules = compoundTag.getCompoundTag("modules");

        for (String key : savedModules.getKeySet()) {
            NBTTagCompound saved = savedModules.getCompoundTag(key);
            Totals moduleTotals = totals(key);

            moduleTotals.blocks = saved.getLong("blocks");
            moduleTotals.items = saved.getLong("items");
            moduleTotals.chunks = saved.getLong("chunks");
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compoundTag) {
        compoundTag.merge(retained);
        compoundTag.setInteger("schema_version", 1);
        compoundTag.setLong("vanilla_blocks", blocks);
        compoundTag.setLong("vanilla_items", items);
        compoundTag.setLong("vanilla_chunks", chunks);

        NBTTagCompound issues = new NBTTagCompound();

        for (java.util.Map.Entry<String, Long> entry : unresolved.entrySet()) {
            issues.setLong(entry.getKey(), entry.getValue());
        }

        compoundTag.setTag("unresolved", issues);

        NBTTagCompound savedModules = compoundTag.getCompoundTag("modules");

        for (java.util.Map.Entry<String, Totals> entry : modules.entrySet()) {
            NBTTagCompound saved = savedModules.getCompoundTag(entry.getKey());

            saved.setLong("blocks", entry.getValue().blocks);
            saved.setLong("items", entry.getValue().items);
            saved.setLong("chunks", entry.getValue().chunks);
            savedModules.setTag(entry.getKey(), saved);
        }
        if (!savedModules.isEmpty()) {
            compoundTag.setTag("modules", savedModules);
        }

        return compoundTag;
    }

    public static PiecesWorldState get(World world) {
        if (world.getMinecraftServer() != null) {
            world = world.getMinecraftServer().getWorld(0);
        }

        PiecesWorldState worldState = (PiecesWorldState) world.getMapStorage().getOrLoadData(PiecesWorldState.class,
                NAME);

        if (worldState == null) {
            worldState = new PiecesWorldState();
            world.getMapStorage().setData(NAME, worldState);
            worldState.markDirty();
        }

        return worldState;
    }
}
