package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRegistrySourcesTest {
    @Test
    void laterSkyPalettesDoNotDisappearBehindAnOlderSidecar() {
        CompoundTag original = registry(400, "older_mod:stone");
        CompoundTag later = original.copy();

        add(later, 900, "skysbuildingpieces:rock_corner_00");

        CompoundTag beforeOriginal = original.copy();
        CompoundTag beforeLater = later.copy();
        Map<Integer, String> merged = LegacyRegistrySources.merge(original, later);

        assertEquals("older_mod:stone", merged.get(400));
        assertEquals("skysbuildingpieces:rock_corner_00", merged.get(900));
        assertEquals(beforeOriginal, original);
        assertEquals(beforeLater, later);
    }

    @Test
    void separateRecoveryHoldersKeepTheOriginalNumericMeaning() {
        String originalId = "registry_test:retired_stone";
        String holder = "skysbuildingpiecesbop:registry_test_holder";

        HistoricalFullBlocks.registerStates(originalId, holder, "registry_test", metadata -> null);
        assertEquals(originalId,
                LegacyRegistrySources.merge(registry(450, originalId), registry(450, holder)).get(450));
    }

    @Test
    void ambiguousSkyNumbersStopInsteadOfGuessing() {
        CompoundTag original = registry(777, "older_mod:unrelated_block");
        CompoundTag later = registry(777, "skysbuildingpieces:rock_corner_00");

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> LegacyRegistrySources.merge(original, later));
    }

    @Test
    void conflictingAndMalformedSnapshotEntriesStopBeforePublication() {
        CompoundTag duplicate = registry(600, "older_mod:first");

        add(duplicate, 600, "older_mod:second");
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> LegacyRegistrySources.merge(duplicate, new CompoundTag()));

        CompoundTag malformed = registry(601, "older_mod:stone");

        malformed.getCompoundOrEmpty("Registries").getCompoundOrEmpty("minecraft:blocks").getListOrEmpty("ids").getCompoundOrEmpty(0)
                .putString("V", "not a block number");
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> LegacyRegistrySources.merge(malformed, new CompoundTag()));
    }

    private CompoundTag registry(int number, String name) {
        CompoundTag root = new CompoundTag();
        CompoundTag registries = new CompoundTag();
        CompoundTag blocks = new CompoundTag();

        blocks.put("ids", new ListTag());
        registries.put("minecraft:blocks", blocks);
        root.put("Registries", registries);
        add(root, number, name);
        return root;
    }

    private void add(CompoundTag root, int number, String name) {
        CompoundTag entry = new CompoundTag();

        entry.putInt("V", number);
        entry.putString("K", name);
        root.getCompoundOrEmpty("Registries").getCompoundOrEmpty("minecraft:blocks").getListOrEmpty("ids").add(entry);
    }
}
