package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.fml.WorldPersistenceHooks;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/** Exercises the real persistence callbacks without changing production registration. */
final class PersistenceOrderChecks {
    static void ready(FMLLoadCompleteEvent event) {
        String order = System.getProperty("skysbuildingpieces.persistenceOrder", "");
        if (!order.isEmpty()) {
            event.enqueueWork(() -> arrange(order));
        }
    }

    private static void arrange(String order) {
        if (!order.equals("forward") && !order.equals("reverse")) {
            throw new AssertionError("Unknown persistence order: " + order);
        }
        try {
            Field field = WorldPersistenceHooks.class.getDeclaredField("worldPersistenceHooks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<WorldPersistenceHooks.WorldPersistenceHook> current =
                    (List<WorldPersistenceHooks.WorldPersistenceHook>) field.get(null);
            List<WorldPersistenceHooks.WorldPersistenceHook> hooks = new ArrayList<>(current);
            List<Integer> positions = new ArrayList<>();
            List<WorldPersistenceHooks.WorldPersistenceHook> selected = new ArrayList<>();
            for (int index = 0; index < hooks.size(); index++) {
                String name = hooks.get(index).getClass().getName();
                if (name.startsWith("zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge$")
                        || name.startsWith("zone.moddev.mc.skysgrassslabs.compat.LegacyWorldDataHook$")
                        || name.startsWith("zone.moddev.mc.mineralogy.patching.LegacyWorldDataHook$")) {
                    positions.add(index);
                    selected.add(hooks.get(index));
                }
            }
            require(selected.size() == 3, "All three actual persistence hooks must be installed");
            selected.sort(Comparator.comparing(hook -> hook.getClass().getName()));
            if (order.equals("reverse")) {
                java.util.Collections.reverse(selected);
            }
            for (int index = 0; index < positions.size(); index++) {
                hooks.set(positions.get(index), selected.get(index));
            }
            field.set(null, hooks);
            System.out.println("BUILDING_PIECES_PERSISTENCE_ORDER_SET " + order + " actual_hooks=3");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot arrange the native persistence callbacks", failure);
        }
    }

    static void verify(ServerWorld world) throws Exception {
        File original = new File("source-level.dat");
        if (!original.isFile() || System.getProperty("skysbuildingpieces.persistenceOrder", "").isEmpty()) {
            return;
        }
        CompoundNBT expected = read(original).getCompound("FML");
        if (expected.isEmpty()) {
            return; // Named source fixtures do not need a pre-flattening registry snapshot.
        }
        File directory = world.getServer().getWorldPath(FolderName.ROOT).toFile();
        world.getServer().saveAllChunks(true, true, true);
        CompoundNBT saved = read(new File(directory, "level.dat")).getCompound("FML");
        require(saved.equals(expected), "The complete uppercase registry snapshot changed");
        CompoundNBT blocks = expected.getCompound("Registries").getCompound("minecraft:blocks");
        require(!blocks.isEmpty(), "The source must contain a real numeric registry");
        for (String name : new String[] { "mineralogy", "skysgrassslabs" }) {
            CompoundNBT sidecar = read(new File(directory, "data/" + name + "_legacy_registry.dat"));
            require(sidecar.getCompound("Blocks").equals(blocks), name + " lost its independent sidecar");
        }
        CompoundNBT core = read(new File(directory, "data/skysbuildingpieces_legacy_registry.dat"));
        require(core.getCompound("FML").equals(expected), "Building Pieces lost its complete sidecar");
        System.out.println("BUILDING_PIECES_PERSISTENCE_ORDER_PASS "
                + System.getProperty("skysbuildingpieces.persistenceOrder") + " actual_hooks=3 sidecars=3 ids="
                + blocks.getList("ids", 10).size() + " complete_uppercase_snapshot=true");
    }

    private static CompoundNBT read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(input);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
