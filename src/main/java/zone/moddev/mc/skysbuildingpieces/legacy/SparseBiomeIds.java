package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.core.IdMap;

/** Storage width for biome registries which retain gaps from an older save. */
public final class SparseBiomeIds {
    private SparseBiomeIds() {
    }

    public static <T> int storageSize(IdMap<T> biomes) {
        int slots = biomes.size();
        for (T biome : biomes) {
            int number = biomes.getId(biome);
            if (number < 0 || number == Integer.MAX_VALUE) {
                throw new IllegalStateException("Cannot store invalid registered biome number " + number);
            }
            slots = Math.max(slots, number + 1);
        }
        if (slots == 0) {
            throw new IllegalStateException("Cannot store an empty biome registry");
        }
        return slots;
    }
}
