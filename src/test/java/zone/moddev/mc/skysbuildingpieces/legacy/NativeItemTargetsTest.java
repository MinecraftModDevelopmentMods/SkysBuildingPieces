package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.jupiter.api.Test;

class NativeItemTargetsTest {
    @Test
    void finalIdentitySurvivesVanillaRenamingWithoutChangingTheStack() {
        CompoundNBT item = item();
        CompoundNBT custom = new CompoundNBT();

        custom.putString("unrelated", "Keep the saved item data");
        item.put("tag", custom);

        CompoundNBT expected = item.copy();

        expected.putString("id", "minecraft:grass");
        expected.remove(HistoricalItems.NATIVE_ITEM_ID);
        HistoricalItems.restoreNativeTargets(item);
        assertEquals(expected, item);
        HistoricalItems.restoreNativeTargets(item);
        assertEquals(expected, item);
    }

    @Test
    void malformedNestedMarkerCannotPartiallyChangeAnInventory() {
        CompoundNBT root = new CompoundNBT();
        ListNBT inventory = new ListNBT();

        inventory.add(item());

        CompoundNBT invalid = item();

        invalid.putString(HistoricalItems.NATIVE_ITEM_ID, "minecraft:stone");
        inventory.add(invalid);
        root.put("Inventory", inventory);

        CompoundNBT before = root.copy();

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> HistoricalItems.restoreNativeTargets(root));
        assertEquals(before, root);
    }

    @Test
    void roughStoneItemRetainsItsCurrentIdentityAndCustomData() {
        CompoundNBT slab = item();
        slab.putString("id", "minecraft:smooth_stone_slab");
        slab.putString(HistoricalItems.NATIVE_ITEM_ID, "minecraft:stone_slab");
        CompoundNBT expected = slab.copy();
        expected.putString("id", "minecraft:stone_slab");
        expected.remove(HistoricalItems.NATIVE_ITEM_ID);

        HistoricalItems.restoreNativeTargets(slab);
        assertEquals(expected, slab);
        HistoricalItems.restoreNativeTargets(slab);
        assertEquals(expected, slab);
    }

    @Test
    void roughStoneStairItemKeepsItsSelectedMaterial() {
        CompoundNBT stairs = item();
        stairs.putString("id", "minecraft:cobblestone_stairs");
        stairs.putString(HistoricalItems.NATIVE_ITEM_ID, "minecraft:stone_stairs");
        CompoundNBT expected = stairs.copy();
        expected.putString("id", "minecraft:stone_stairs");
        expected.remove(HistoricalItems.NATIVE_ITEM_ID);

        HistoricalItems.restoreNativeTargets(stairs);
        assertEquals(expected, stairs);
        HistoricalItems.restoreNativeTargets(stairs);
        assertEquals(expected, stairs);
    }

    @Test
    void roughStoneStateRetainsItsHalfAndWaterWithoutChangingOrdinaryVanillaStates() {
        CompoundNBT marked = state();
        CompoundNBT vanilla = state();
        vanilla.remove(NumericStateBridge.NATIVE_STATE_ID);
        CompoundNBT vanillaBefore = vanilla.copy();
        ListNBT palette = new ListNBT();
        palette.add(marked);
        palette.add(vanilla);
        CompoundNBT section = new CompoundNBT();
        section.put("Palette", palette);

        HistoricalItems.restoreNativeTargets(section);
        assertEquals("minecraft:stone_slab", marked.getString("Name"));
        assertEquals("top", marked.getCompound("Properties").getString("type"));
        assertEquals("true", marked.getCompound("Properties").getString("waterlogged"));
        assertFalse(marked.contains(NumericStateBridge.NATIVE_STATE_ID));
        assertEquals(vanillaBefore, vanilla);
        CompoundNBT once = section.copy();
        HistoricalItems.restoreNativeTargets(section);
        assertEquals(once, section);
    }

    @Test
    void malformedStateCannotPartiallyRestoreAnEarlierItem() {
        CompoundNBT root = new CompoundNBT();
        root.put("item", item());
        CompoundNBT invalid = state();
        invalid.getCompound("Properties").putString("type", "sideways");
        root.put("state", invalid);
        CompoundNBT before = root.copy();

        assertThrows(LegacyBridge.RecoveryAbort.class, () -> HistoricalItems.restoreNativeTargets(root));
        assertEquals(before, root);
    }

    @Test
    void intermediateDatafixKeepsTheSelectedNameUntilTheLaterVanillaRenameHasRun() {
        CompoundNBT pending = state();
        pending.putString("Name", "minecraft:stone_slab");
        NumericStateBridge.preserveNativeName(pending);
        CompoundNBT before = pending.copy();

        HistoricalItems.finish(pending, 1493);
        assertEquals(before, pending);
        pending.putString("Name", "minecraft:smooth_stone_slab");
        HistoricalItems.finish(pending, 1976);
        assertEquals("minecraft:stone_slab", pending.getString("Name"));
        assertFalse(pending.contains(NumericStateBridge.NATIVE_STATE_ID));
        HistoricalItems.finish(pending, 1976);
        assertEquals("minecraft:stone_slab", pending.getString("Name"));
    }

    private CompoundNBT state() {
        CompoundNBT state = new CompoundNBT();
        state.putString("Name", "minecraft:smooth_stone_slab");
        state.putString(NumericStateBridge.NATIVE_STATE_ID, "minecraft:stone_slab");
        CompoundNBT properties = new CompoundNBT();
        properties.putString("type", "top");
        properties.putString("waterlogged", "true");
        state.put("Properties", properties);
        return state;
    }

    private CompoundNBT item() {
        CompoundNBT item = new CompoundNBT();

        item.putString("id", "minecraft:grass_block");
        item.putByte("Count", (byte) 37);
        item.putByte("Slot", (byte) 17);
        item.putString(HistoricalItems.NATIVE_ITEM_ID, "minecraft:grass");
        return item;
    }
}
