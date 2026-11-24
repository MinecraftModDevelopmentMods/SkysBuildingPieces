package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

/** Real storage stacks may hold more items than the saved byte-sized Count. */
final class LiveInventoryRecoveryChecks {
    private LiveInventoryRecoveryChecks() {
    }

    static void run(WorldServer world) {
        for (int count : new int[] {1, 64, 99, 100, 127, 128, 255, 256, 1895, Integer.MAX_VALUE}) {
            ItemStack ordinary = new ItemStack(Blocks.STONE, count);
            require(MigrationEvents.convert(ordinary, world) == ordinary,
                    "Leave unrelated storage stacks unchanged");

            ItemStack saved = historical(count);
            ItemStack original = saved.copy();
            ItemStack converted = MigrationEvents.convert(saved, world);
            require(converted != saved && converted.getItem().getRegistryName().getResourceDomain()
                            .equals("skysbuildingpieces") && converted.stackSize == count,
                    "Recover the piece without narrowing its storage count: " + count);
            require(converted.getTagCompound().equals(original.getTagCompound()),
                    "Keep names and unrelated item NBT");
            require(saved.stackSize == count && saved.getItem() == original.getItem()
                            && saved.getTagCompound().equals(original.getTagCompound()),
                    "Never mutate the handler's original stack");
            require(MigrationEvents.convert(converted, world) == converted,
                    "Recovered pieces remain stable on subsequent scans");
        }

        ItemStack container = new ItemStack(Blocks.CHEST, 1895);
        NBTTagCompound data = new NBTTagCompound();
        data.setString("owner", "Keep this storage data");
        NBTTagList contents = new NBTTagList();
        NBTTagCompound nested = historical(17).writeToNBT(new NBTTagCompound());
        nested.setByte("Slot", (byte) 7);
        contents.appendTag(nested);
        data.setTag("Items", contents);
        container.setTagCompound(data);
        ItemStack recovered = MigrationEvents.convert(container, world);
        NBTTagCompound recoveredItem = recovered.getTagCompound().getTagList("Items", 10).getCompoundTagAt(0);
        require(recovered.stackSize == 1895 && recoveredItem.getByte("Count") == 17
                        && recoveredItem.getByte("Slot") == 7
                        && recoveredItem.getString("id").startsWith("skysbuildingpieces:")
                        && recovered.getTagCompound().getString("owner").equals(data.getString("owner")),
                "Keep outer counts, nested counts, slots and custom data");
        require(container.stackSize == 1895 && container.getTagCompound().equals(data),
                "Nested recovery leaves the original container unchanged");
        require(MigrationEvents.convert(recovered, world) == recovered, "Nested recovery is idempotent");

        NBTTagCompound unsafe = nested.copy();
        unsafe.getCompoundTag("tag").setString("material", "missing:unknown");
        contents.appendTag(unsafe);
        NBTTagCompound beforeFailure = data.copy();
        boolean stopped = false;
        try {
            MigrationEvents.convert(container, world);
        } catch (LegacyBridge.RecoveryAbort expected) {
            stopped = true;
        }
        require(stopped && container.getTagCompound().equals(beforeFailure),
                "An unknown nested material stops before any source data changes");
        System.out.println("BUILDING_PIECES_LIVE_STORAGE_PASS counts=1,64,99,100,127,128,255,256,1895,2147483647 nested=true atomic=true");
    }

    private static ItemStack historical(int count) {
        ItemStack stack = new ItemStack(Item.getByNameOrId("buildingbricks:rock_step"), count, 327);
        NBTTagCompound data = new NBTTagCompound();
        data.setString("material", "minecraft:stone");
        data.setString("owner", "Keep this piece");
        stack.setTagCompound(data);
        stack.setStackDisplayName("Stored stone step");
        return stack;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
