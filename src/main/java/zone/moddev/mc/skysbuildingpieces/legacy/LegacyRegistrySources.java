package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Keep the old registry while adding IDs introduced during a later 1.12 save. */
final class LegacyRegistrySources {
    private LegacyRegistrySources() {
    }

    static Map<Integer, String> merge(CompoundTag original, CompoundTag later) {
        Map<Integer, String> result = read(original);

        for (Map.Entry<Integer, String> entry : read(later).entrySet()) {
            String prior = result.get(entry.getKey());

            if (prior == null) {
                result.put(entry.getKey(), entry.getValue());
            } else if (!prior.equals(entry.getValue()) && LegacyPreflight.skyOwned(entry.getValue())
                    && !HistoricalFullBlocks.sameSource(prior, entry.getValue())) {
                throw new LegacyBridge.RecoveryAbort("World upgrade stopped: saved block number " + entry.getKey()
                        + " means both " + prior + " and " + entry.getValue()
                        + ". Restore the source modpack and finish its recovery before trying again.", null);
            }
        }

        return result;
    }

    private static Map<Integer, String> read(CompoundTag registry) {
        Map<Integer, String> result = new LinkedHashMap<>();
        ListTag ids = registry.getCompound("Registries").getCompound("minecraft:blocks").getList("ids", 10);

        for (int index = 0; index < ids.size(); index++) {
            CompoundTag entry = ids.getCompound(index);

            if (!entry.contains("V", 99) || !entry.contains("K", 8)) {
                throw new LegacyBridge.RecoveryAbort("Malformed saved registry entry: " + entry, null);
            }

            int number = entry.getInt("V");
            String name = entry.getString("K");
            String duplicate = result.put(number, name);

            if (number < 0 || number >= 4096 || name.isEmpty() || duplicate != null && !duplicate.equals(name)) {
                throw new LegacyBridge.RecoveryAbort("Invalid or conflicting saved block number: " + number, null);
            }
        }

        return result;
    }
}
