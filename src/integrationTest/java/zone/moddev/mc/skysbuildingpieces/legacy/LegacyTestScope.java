package zone.moddev.mc.skysbuildingpieces.legacy;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.server.ServerWorld;

/** Keeps synthetic source registries separate from the running disposable world. */
public final class LegacyTestScope implements AutoCloseable {
    public static final int STEP_ID = 3500;
    public static final int DIRT_ID = 3501;
    private final Map<Integer, String> originalIds;
    private final ListNBT originalStaging;
    private final boolean originalValidation;
    private final PiecesWorldState totals;
    private final CompoundNBT originalTotals;
    private final ServerWorld world;

    @SuppressWarnings("unchecked")
    public LegacyTestScope(ServerWorld world) throws Exception {
        this.world = world;
        LegacyBridge.report(world);
        totals = PiecesWorldState.get(world);
        originalTotals = totals.save(new CompoundNBT());

        Map<Integer, String> ids = (Map<Integer, String>) field("savedIds").get(null);

        originalIds = new LinkedHashMap<>(ids);
        originalStaging = NumericStateBridge.saveStaging().copy();
        originalValidation = field("registryValidated").getBoolean(null);
        field("registryValidated").setBoolean(null, false);
        ids.clear();
        ids.put(STEP_ID, "buildingbricks:rock_step");
        ids.put(DIRT_ID, "buildingbricks:dirt_vertical_slab");
        // These are source-world numbers, deliberately unrelated to today's registry.
        ids.put(256, "othermod:occupied_source_number");
        NumericStateBridge.begin(ids, new ListNBT());
    }

    public static BlockState staged(CompoundNBT section, int index) {
        int number = section.getByteArray("Blocks")[index] & 255
                | LegacyBridge.nibble(section.getByteArray("Add"), index) << 8;
        int encoded = number << 4 | LegacyBridge.nibble(section.getByteArray("Data"), index);

        return NumericStateBridge.stagedState(encoded);
    }

    private static Field field(String name) throws Exception {
        Field field = LegacyBridge.class.getDeclaredField(name);

        field.setAccessible(true);
        return field;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void close() throws Exception {
        LegacyBridge.report(world);
        totals.load(originalTotals);
        totals.setDirty();

        Map<Integer, String> ids = (Map<Integer, String>) field("savedIds").get(null);

        ids.clear();
        ids.putAll(originalIds);
        NumericStateBridge.begin(originalIds, originalStaging);
        field("registryValidated").setBoolean(null, originalValidation);
    }
}
