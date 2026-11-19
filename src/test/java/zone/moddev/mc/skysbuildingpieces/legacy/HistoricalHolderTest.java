package zone.moddev.mc.skysbuildingpieces.legacy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalHolderTest {
    @Test
    void holdersAreRecognisedAndCanReceiveIndependentFlatteningCoverage() {
        String source = "coverage_test:old_decoration";
        String holder = "coverage_test:recovery_decoration";

        HistoricalFullBlocks.registerStates(source, holder, "old_coverage", metadata -> null);
        assertTrue(HistoricalFullBlocks.historical(source));
        assertTrue(HistoricalFullBlocks.historical(holder));
        assertFalse(HistoricalFullBlocks.recoveryHolder(source));
        assertTrue(HistoricalFullBlocks.recoveryHolder(holder));
        assertEquals("old_coverage", HistoricalFullBlocks.module(holder));
        HistoricalFullBlocks.registerHolderCoverage(holder, "new_flattening");
        assertEquals("new_flattening", HistoricalFullBlocks.module(holder));
        assertEquals("old_coverage", HistoricalFullBlocks.module(source));
        assertThrows(IllegalStateException.class,
                () -> HistoricalFullBlocks.registerHolderCoverage(holder, "another_module"));
        assertEquals("new_flattening", HistoricalFullBlocks.module(holder));
    }

    @Test
    void unknownAndLateHolderContributionsCannotChangeMappings() {
        String unknown = "coverage_test:missing_holder";

        assertThrows(IllegalStateException.class,
                () -> HistoricalFullBlocks.registerHolderCoverage(unknown, "new_flattening"));
        assertFalse(HistoricalFullBlocks.historical(unknown));
        assertNull(HistoricalFullBlocks.module(unknown));

        String source = "coverage_test:another_decoration";
        String holder = "coverage_test:another_holder";

        HistoricalFullBlocks.registerStates(source, holder, "original_module", metadata -> null);

        boolean ready = LegacyBridge.ready;

        try {
            LegacyBridge.ready = true;
            assertThrows(IllegalStateException.class,
                    () -> HistoricalFullBlocks.registerHolderCoverage(holder, "late_module"));
            assertEquals("original_module", HistoricalFullBlocks.module(holder));
        } finally {
            LegacyBridge.ready = ready;
        }
    }
}
