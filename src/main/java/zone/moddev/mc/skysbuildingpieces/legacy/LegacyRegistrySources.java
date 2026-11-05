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
        ListTag ids = registry.getCompoundOrEmpty("Registries").getCompoundOrEmpty("minecraft:blocks").getListOrEmpty("ids");

        for (int index = 0; index < ids.size(); index++) {
            CompoundTag entry = ids.getCompoundOrEmpty(index);

            if (!entry.getInt("V").isPresent() || !entry.getString("K").isPresent()) {
                throw new LegacyBridge.RecoveryAbort("Malformed saved registry entry: " + entry, null);
            }

            int number = entry.getIntOr("V", 0);
            String name = entry.getStringOr("K", "");
            String duplicate = result.put(number, name);

            if (number < 0 || number >= 4096 || name.isEmpty() || duplicate != null && !duplicate.equals(name)) {
                throw new LegacyBridge.RecoveryAbort("Invalid or conflicting saved block number: " + number, null);
            }
        }

        return result;
    }
}
