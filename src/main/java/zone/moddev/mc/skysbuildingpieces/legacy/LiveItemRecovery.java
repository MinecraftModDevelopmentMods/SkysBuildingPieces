package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Recover live storage identities without narrowing the container's item count. */
final class LiveItemRecovery {
    private LiveItemRecovery() {
    }

    static ItemStack convert(ItemStack original) {
        int storedCount = original.getCount();
        CompoundTag before = original.save(new CompoundTag());
        // Keep the live count as an integer only while resolving identities.
        // Vanilla saves and nested item tags retain their original Count format.
        before.putInt("Count", storedCount);
        CompoundTag recovered = before.copy();
        LegacyBridge.prepareStack(recovered);
        if (recovered.equals(before)) {
            return original;
        }
        if (LegacyBridge.stackCount(recovered) != storedCount) {
            throw new LegacyBridge.RecoveryAbort("Item recovery changed the stored count for "
                    + before.getString("id"), null);
        }

        recovered.putByte("Count", (byte) 1);
        ItemStack result = ItemStack.of(recovered);
        if (result.isEmpty()) {
            throw new LegacyBridge.RecoveryAbort("Cannot decode recovered item " + recovered.getString("id"), null);
        }
        result.setCount(storedCount);
        return result;
    }
}
