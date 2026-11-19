package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HistoricalBiomesTest {
    @Test
    void sourceNumbersUseIdentitiesAndAllowTargetIdsAbove255() {
        Map<Integer, String> source = new LinkedHashMap<>();

        source.put(200, "test:old_swamp");
        source.put(201, "test:retained");

        AtomicInteger lookups = new AtomicInteger();
        HistoricalBiomes.Mappings mappings = new HistoricalBiomes.Mappings(source,
                Collections.singletonMap("test:old_swamp", "test:new_swamp"), name -> {
                    lookups.incrementAndGet();
                    return name.equals("test:new_swamp") ? 301 : name.equals("test:retained") ? 401 : -1;
                });
        byte[] original = { (byte) 200, 1, (byte) 201, (byte) 200 };

        assertArrayEquals(new int[] { 301, 1, 401, 301 }, mappings.translate(original));
        assertArrayEquals(new byte[] { (byte) 200, 1, (byte) 201, (byte) 200 }, original);
        assertEquals(2, lookups.get(), "Resolve each source identity once");
    }

    @Test
    void unusedMissingBiomesDoNotBlockAnUnrelatedChunk() {
        HistoricalBiomes.Mappings mappings = new HistoricalBiomes.Mappings(
                Collections.singletonMap(200, "test:unavailable"), Collections.emptyMap(), name -> -1);

        assertNull(mappings.translate(new byte[] { 1, 2, 3 }));
    }

    @Test
    void aMissingUsedBiomeStopsWithoutChangingTheSource() {
        HistoricalBiomes.Mappings mappings = new HistoricalBiomes.Mappings(
                Collections.singletonMap(200, "test:unavailable"), Collections.emptyMap(), name -> -1);
        byte[] source = { 1, (byte) 200 };

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> mappings.translate(source));
        assertArrayEquals(new byte[] { 1, (byte) 200 }, source);
    }
}
