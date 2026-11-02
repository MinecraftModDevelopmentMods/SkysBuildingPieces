package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.IdMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.chunk.MissingPaletteEntryException;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.junit.jupiter.api.Test;

class SparseBiomeIdsTest {
    @Test
    void denseRegistryStorageKeepsItsNativeWidth() {
        Map<Integer, String> values = new LinkedHashMap<>();
        for (int number = 0; number < 127; number++) {
            values.put(number, "biome-" + number);
        }
        assertEquals(127, SparseBiomeIds.storageSize(ids(values, false)));
    }

    @Test
    void actualNativeCodecPreservesHighNumbersWhenRemovedBiomesLeaveGaps() {
        Map<Integer, String> values = new LinkedHashMap<>();
        for (int entry = 0; entry < 127; entry++) {
            int number = entry < 64 ? entry : entry + 75;
            values.put(number, "biome-" + number);
        }
        List<String> names = new ArrayList<>();
        ListTag palette = new ListTag();
        for (int number = 193; number <= 201; number++) {
            names.add(values.get(number));
            palette.add(StringTag.valueOf(values.get(number)));
        }
        CompoundTag disk = new CompoundTag();
        disk.put("palette", palette);
        long[] packed = new long[4];
        for (int cell = 0; cell < 64; cell++) {
            packed[cell / 16] |= (long) (cell % 9) << (cell % 16 * 4);
        }
        disk.putLongArray("data", packed);

        // The unit runner does not apply coremods. This exposes the native
        // truncation, then checks the same codec with the corrected width.
        PalettedContainer<String> control = read(ids(values, false), disk);
        assertThrows(MissingPaletteEntryException.class, () -> control.get(0, 0, 0));
        assertEquals(202, SparseBiomeIds.storageSize(ids(values, false)));
        PalettedContainer<String> fixed = read(ids(values, true), disk);
        for (int cell = 0; cell < 64; cell++) {
            assertEquals(names.get(cell % 9), fixed.get(cell & 3, cell >> 4, cell >> 2 & 3));
        }
    }

    @Test
    void invalidOrEmptyRegistriesStopWithAnExplanation() {
        assertThrows(IllegalStateException.class, () -> SparseBiomeIds.storageSize(ids(Map.of(), false)));
        assertThrows(IllegalStateException.class,
                () -> SparseBiomeIds.storageSize(ids(Map.of(-1, "invalid"), false)));
    }

    private static PalettedContainer<String> read(IdMap<String> registry, CompoundTag disk) {
        return PalettedContainer.codec(registry, Codec.STRING, PalettedContainer.Strategy.SECTION_BIOMES, "biome-0")
                .parse(NbtOps.INSTANCE, disk).result().orElseThrow();
    }

    private static IdMap<String> ids(Map<Integer, String> values, boolean expanded) {
        IdMap<String> original = new IdMap<>() {
            @Override
            public int getId(String value) {
                return values.entrySet().stream().filter(entry -> entry.getValue().equals(value))
                        .mapToInt(Map.Entry::getKey).findFirst().orElse(-1);
            }

            @Override
            public String byId(int number) {
                return values.get(number);
            }

            @Override
            public int size() {
                return values.size();
            }

            @Override
            public Iterator<String> iterator() {
                return values.values().iterator();
            }
        };
        if (!expanded) {
            return original;
        }
        return new IdMap<>() {
            @Override
            public int getId(String value) {
                return original.getId(value);
            }

            @Override
            public String byId(int number) {
                return original.byId(number);
            }

            @Override
            public int size() {
                return SparseBiomeIds.storageSize(original);
            }

            @Override
            public Iterator<String> iterator() {
                return original.iterator();
            }
        };
    }
}
