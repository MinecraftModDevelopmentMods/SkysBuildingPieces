package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class HistoricalItemsTest {
    @Test
    void standaloneAndUnrelatedDataKeepTheirOriginalObject() {
        HistoricalItems.Registry registry = new HistoricalItems.Registry();
        CompoundTag original = stack("minecraft:stone");

        assertSame(original, registry.prepareCopy(original));
        registry.register("test_material", root -> root.getString("id").equals("old:basket"), root -> fail());
        assertSame(original, registry.prepareCopy(original));
    }

    @Test
    void nestedInventoryRecoveryKeepsCountsSlotsAndUnrelatedData() {
        HistoricalItems.Registry registry = new HistoricalItems.Registry();

        registry.register("test_material", root -> root.contains("Inventory"),
                root -> root.getList("Inventory", 10).getCompound(0).putString("id", "new:basket"));

        CompoundTag original = new CompoundTag();
        ListTag inventory = new ListTag();
        CompoundTag item = stack("old:basket");

        item.putByte("Slot", (byte) 17);
        inventory.add(item);
        original.put("Inventory", inventory);

        CompoundTag before = original.copy();
        CompoundTag recovered = registry.prepareCopy(original);

        assertEquals(before, original);
        assertEquals("new:basket", recovered.getList("Inventory", 10).getCompound(0).getString("id"));

        CompoundTag expected = before.copy();

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

        CompoundTag original = stack("old:basket");
        CompoundTag before = original.copy();
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

        CompoundTag original = stack("old:basket");
        CompoundTag before = original.copy();

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> registry.prepareCopy(original));
        assertEquals(before, original);
    }

    private static CompoundTag stack(String identifier) {
        CompoundTag result = new CompoundTag();

        result.putString("id", identifier);
        result.putByte("Count", (byte) 1);

        CompoundTag tag = new CompoundTag();

        tag.putString("CustomName", "A saved basket");
        tag.putString("Unrelated", "Keep this");
        result.put("tag", tag);
        return result;
    }
}
