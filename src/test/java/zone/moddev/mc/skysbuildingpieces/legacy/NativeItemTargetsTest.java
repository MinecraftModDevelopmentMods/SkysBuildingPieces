package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class NativeItemTargetsTest {
    @Test
    void finalIdentitySurvivesVanillaRenamingWithoutChangingTheStack() {
        CompoundTag item = item();
        CompoundTag custom = new CompoundTag();

        custom.putString("unrelated", "Keep the saved item data");
        item.put("tag", custom);

        CompoundTag expected = item.copy();

        expected.putString("id", "minecraft:grass");
        expected.remove(HistoricalItems.NATIVE_ITEM_ID);
        HistoricalItems.restoreNativeTargets(item);
        assertEquals(expected, item);
        HistoricalItems.restoreNativeTargets(item);
        assertEquals(expected, item);
    }

    @Test
    void malformedNestedMarkerCannotPartiallyChangeAnInventory() {
        CompoundTag root = new CompoundTag();
        ListTag inventory = new ListTag();

        inventory.add(item());

        CompoundTag invalid = item();

        invalid.putString(HistoricalItems.NATIVE_ITEM_ID, "minecraft:stone");
        inventory.add(invalid);
        root.put("Inventory", inventory);

        CompoundTag before = root.copy();

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> HistoricalItems.restoreNativeTargets(root));
        assertEquals(before, root);
    }

    @Test
    void roughStoneItemRetainsItsCurrentIdentityAndCustomData() {
        CompoundTag slab = item();
        slab.putString("id", "minecraft:smooth_stone_slab");
        slab.putString(HistoricalItems.NATIVE_ITEM_ID, "minecraft:stone_slab");
        CompoundTag expected = slab.copy();
        expected.putString("id", "minecraft:stone_slab");
        expected.remove(HistoricalItems.NATIVE_ITEM_ID);

        HistoricalItems.restoreNativeTargets(slab);
        assertEquals(expected, slab);
        HistoricalItems.restoreNativeTargets(slab);
        assertEquals(expected, slab);
    }

    @Test
    void roughStoneStairItemKeepsItsSelectedMaterial() {
        CompoundTag stairs = item();
        stairs.putString("id", "minecraft:cobblestone_stairs");
        stairs.putString(HistoricalItems.NATIVE_ITEM_ID, "minecraft:stone_stairs");
        CompoundTag expected = stairs.copy();
        expected.putString("id", "minecraft:stone_stairs");
        expected.remove(HistoricalItems.NATIVE_ITEM_ID);

        HistoricalItems.restoreNativeTargets(stairs);
        assertEquals(expected, stairs);
        HistoricalItems.restoreNativeTargets(stairs);
        assertEquals(expected, stairs);
    }

    @Test
    void roughStoneStateRetainsItsHalfAndWaterWithoutChangingOrdinaryVanillaStates() {
        CompoundTag marked = state();
        CompoundTag vanilla = state();
        vanilla.remove(NumericStateBridge.NATIVE_STATE_ID);
        CompoundTag vanillaBefore = vanilla.copy();
        ListTag palette = new ListTag();
        palette.add(marked);
        palette.add(vanilla);
        CompoundTag section = new CompoundTag();
        section.put("Palette", palette);

        HistoricalItems.restoreNativeTargets(section);
        assertEquals("minecraft:stone_slab", marked.getString("Name"));
        assertEquals("top", marked.getCompound("Properties").getString("type"));
        assertEquals("true", marked.getCompound("Properties").getString("waterlogged"));
        assertFalse(marked.contains(NumericStateBridge.NATIVE_STATE_ID));
        assertEquals(vanillaBefore, vanilla);
        CompoundTag once = section.copy();
        HistoricalItems.restoreNativeTargets(section);
        assertEquals(once, section);
    }

    @Test
    void malformedStateCannotPartiallyRestoreAnEarlierItem() {
        CompoundTag root = new CompoundTag();
        root.put("item", item());
        CompoundTag invalid = state();
        invalid.getCompound("Properties").putString("type", "sideways");
        root.put("state", invalid);
        CompoundTag before = root.copy();

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> HistoricalItems.restoreNativeTargets(root));
        assertEquals(before, root);
    }

    @Test
    void intermediateDatafixKeepsTheSelectedNameUntilTheLaterVanillaRenameHasRun() {
        CompoundTag pending = state();
        pending.putString("Name", "minecraft:stone_slab");
        NumericStateBridge.preserveNativeName(pending);
        CompoundTag before = pending.copy();

        HistoricalItems.finish(pending, 1493);
        assertEquals(before, pending);
        pending.putString("Name", "minecraft:smooth_stone_slab");
        HistoricalItems.finish(pending, 1976);
        assertEquals("minecraft:stone_slab", pending.getString("Name"));
        assertFalse(pending.contains(NumericStateBridge.NATIVE_STATE_ID));
        HistoricalItems.finish(pending, 1976);
        assertEquals("minecraft:stone_slab", pending.getString("Name"));
    }

    private CompoundTag state() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:smooth_stone_slab");
        state.putString(NumericStateBridge.NATIVE_STATE_ID, "minecraft:stone_slab");
        CompoundTag properties = new CompoundTag();
        properties.putString("type", "top");
        properties.putString("waterlogged", "true");
        state.put("Properties", properties);
        return state;
    }

    private CompoundTag item() {
        CompoundTag item = new CompoundTag();

        item.putString("id", "minecraft:grass_block");
        item.putByte("Count", (byte) 37);
        item.putByte("Slot", (byte) 17);
        item.putString(HistoricalItems.NATIVE_ITEM_ID, "minecraft:grass");
        return item;
    }
}
