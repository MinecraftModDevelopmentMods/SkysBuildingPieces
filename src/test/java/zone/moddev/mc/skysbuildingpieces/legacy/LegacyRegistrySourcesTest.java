package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.Map;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRegistrySourcesTest {
    @Test
    void laterSkyPalettesDoNotDisappearBehindAnOlderSidecar() {
        CompoundNBT original = registry(400, "older_mod:stone");
        CompoundNBT later = original.copy();

        add(later, 900, "skysbuildingpieces:rock_corner_00");

        CompoundNBT beforeOriginal = original.copy();
        CompoundNBT beforeLater = later.copy();
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
        CompoundNBT original = registry(777, "older_mod:unrelated_block");
        CompoundNBT later = registry(777, "skysbuildingpieces:rock_corner_00");

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> LegacyRegistrySources.merge(original, later));
    }

    @Test
    void conflictingAndMalformedSnapshotEntriesStopBeforePublication() {
        CompoundNBT duplicate = registry(600, "older_mod:first");

        add(duplicate, 600, "older_mod:second");
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> LegacyRegistrySources.merge(duplicate, new CompoundNBT()));

        CompoundNBT malformed = registry(601, "older_mod:stone");

        malformed.getCompound("Registries").getCompound("minecraft:blocks").getList("ids", 10).getCompound(0)
                .putString("V", "not a block number");
        assertThrows(LegacyBridge.RecoveryAbort.class, () -> LegacyRegistrySources.merge(malformed, new CompoundNBT()));
    }

    private CompoundNBT registry(int number, String name) {
        CompoundNBT root = new CompoundNBT();
        CompoundNBT registries = new CompoundNBT();
        CompoundNBT blocks = new CompoundNBT();

        blocks.put("ids", new ListNBT());
        registries.put("minecraft:blocks", blocks);
        root.put("Registries", registries);
        add(root, number, name);
        return root;
    }

    private void add(CompoundNBT root, int number, String name) {
        CompoundNBT entry = new CompoundNBT();

        entry.putInt("V", number);
        entry.putString("K", name);
        root.getCompound("Registries").getCompound("minecraft:blocks").getList("ids", 10).add(entry);
    }
}
