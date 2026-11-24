package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Inventory recovery after a container has created its live item stacks. */
final class LiveItemRecovery {
    private LiveItemRecovery() {
    }

    static ItemStack convert(ItemStack original, HolderLookup.Provider registries) {
        String registryId = BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
        if (original.getComponentsPatch().isEmpty() && !HistoricalItems.hasContributors()
                && !LegacyBridge.mightRecoverItem(registryId)) {
            return original;
        }

        // Storage handlers can legitimately hold more than the ordinary item
        // codec's 99-item limit. Inspect a copy, keeping the real count intact.
        int storedCount = original.getCount();
        CompoundTag before = (CompoundTag) original.copyWithCount(1).save(registries);
        before.putInt("count", storedCount);
        CompoundTag recovered = before.copy();
        LegacyBridge.prepareStack(recovered);
        if (recovered.equals(before)) {
            return original;
        }

        if (HistoricalItems.stackCount(recovered) != storedCount) {
            throw new LegacyBridge.RecoveryAbort("Item recovery changed the stored count for " + registryId, null);
        }
        // Decode the recovered identity and components without imposing the
        // ordinary codec's count limit on the storage mod's inventory.
        recovered.putInt("count", 1);
        ItemStack result = ItemStack.parse(registries, recovered).orElseThrow(() ->
                new LegacyBridge.RecoveryAbort("Cannot decode recovered item " + recovered.getString("id"), null));
        result.setCount(storedCount);
        return result;
    }
}
