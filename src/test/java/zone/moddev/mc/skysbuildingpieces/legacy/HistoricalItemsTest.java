package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.jupiter.api.Test;

final class HistoricalItemsTest {
    @Test
    void standaloneAndUnrelatedDataKeepTheirOriginalObject() {
        HistoricalItems.Registry registry = new HistoricalItems.Registry();
        CompoundNBT original = stack("minecraft:stone");

        assertSame(original, registry.prepareCopy(original));
        registry.register("test_material", root -> root.getString("id").equals("old:basket"), root -> fail());
        assertSame(original, registry.prepareCopy(original));
    }

    @Test
    void nestedInventoryRecoveryKeepsCountsSlotsAndUnrelatedData() {
        HistoricalItems.Registry registry = new HistoricalItems.Registry();

        registry.register("test_material", root -> root.contains("Inventory"),
                root -> root.getList("Inventory", 10).getCompound(0).putString("id", "new:basket"));

        CompoundNBT original = new CompoundNBT();
        ListNBT inventory = new ListNBT();
        CompoundNBT item = stack("old:basket");

        item.putByte("Slot", (byte) 17);
        inventory.add(item);
        original.put("Inventory", inventory);

        CompoundNBT before = original.copy();
        CompoundNBT recovered = registry.prepareCopy(original);

        assertEquals(before, original);
        assertEquals("new:basket", recovered.getList("Inventory", 10).getCompound(0).getString("id"));

        CompoundNBT expected = before.copy();

        expected.getList("Inventory", 10).getCompound(0).putString("id", "new:basket");
        assertEquals(expected, recovered);
    }

    @Test
    void laterFailureCannotLeaveHalfOfAnEarlierRecoveryApplied() {
        HistoricalItems.Registry registry = new HistoricalItems.Registry();

        registry.register("first", root -> true, root -> root.putString("id", "new:basket"));
        registry.register("second", root -> true, root -> {
            throw new IllegalStateException("duplicate basket slot 4");
        });

        CompoundNBT original = stack("old:basket");
        CompoundNBT before = original.copy();
        LegacyBridge.RecoveryAbort failure = assertThrows(LegacyBridge.RecoveryAbort.class,
                () -> registry.prepareCopy(original));

        assertTrue(failure.getMessage().contains("second"));
        assertTrue(failure.getMessage().contains("duplicate basket slot 4"));
        assertEquals(before, original);
    }

    @Test
    void invalidAndDuplicateContributorsAreRejected() {
        HistoricalItems.Registry registry = new HistoricalItems.Registry();

        assertThrows(IllegalArgumentException.class, () -> registry.register("Bad ID", root -> true, root -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> registry.register("valid", null, root -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> registry.register("valid", root -> true, null));
        registry.register("valid", root -> false, root -> {
        });
        assertThrows(IllegalArgumentException.class, () -> registry.register("valid", root -> true, root -> {
        }));
    }

    @Test
    void malformedInputFailsBeforeDeserializationWithoutChangingItsData() {
        HistoricalItems.Registry registry = new HistoricalItems.Registry();

        registry.register("plants", root -> {
            throw new IllegalStateException("unknown nested plant");
        }, root -> fail());

        CompoundNBT original = stack("old:basket");
        CompoundNBT before = original.copy();

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> registry.prepareCopy(original));
        assertEquals(before, original);
    }

    private static CompoundNBT stack(String identifier) {
        CompoundNBT result = new CompoundNBT();

        result.putString("id", identifier);
        result.putByte("Count", (byte) 1);

        CompoundNBT tag = new CompoundNBT();

        tag.putString("CustomName", "A saved basket");
        tag.putString("Unrelated", "Keep this");
        result.put("tag", tag);
        return result;
    }
}
