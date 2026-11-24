package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Recover live inventory identities without using the save format's byte-sized count. */
final class LiveItemRecovery {
    private LiveItemRecovery() {
    }

    static ItemStack convert(ItemStack original) {
        int storedCount = original.stackSize;
        ItemStack inspection = original.copy();
        inspection.stackSize = 1;
        NBTTagCompound before = inspection.writeToNBT(new NBTTagCompound());
        // This integer exists only during recovery. Saved vanilla stacks retain
        // their usual byte Count, including stacks nested inside item NBT.
        before.setInteger("Count", storedCount);
        NBTTagCompound recovered = before.copy();
        LegacyBridge.prepareStack(recovered);
        if (recovered.equals(before)) {
            return original;
        }
        if (LegacyBridge.stackCount(recovered) != storedCount) {
            throw new LegacyBridge.RecoveryAbort("Item recovery changed the stored count for "
                    + before.getString("id"), null);
        }

        recovered.setByte("Count", (byte) 1);
        ItemStack result = ItemStack.loadItemStackFromNBT(recovered);
        if (result == null) {
            throw new LegacyBridge.RecoveryAbort("Cannot decode recovered item " + recovered.getString("id"), null);
        }
        result.stackSize = storedCount;
        return result;
    }
}
