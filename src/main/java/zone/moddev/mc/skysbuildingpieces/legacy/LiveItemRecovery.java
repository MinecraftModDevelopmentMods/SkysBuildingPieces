package zone.moddev.mc.skysbuildingpieces.legacy;

import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/** Inventory recovery after a container has created its live item stacks. */
final class LiveItemRecovery {
    private LiveItemRecovery() {
    }

    static ItemStack convert(ItemStack original, HolderLookup.Provider registries) {
        return convert(original, registries, Runnable::run);
    }

    static ItemStack convert(ItemStack original, HolderLookup.Provider registries, Consumer<Runnable> account) {
        String registryId = BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
        if (original.getComponentsPatch().isEmpty() && !HistoricalItems.hasContributors()
                && !LegacyBridge.mightRecoverItem(registryId)) {
            return original;
        }

        // Storage handlers can hold more items than the ordinary stack codec.
        // Inspect a one-item copy and keep the handler's real count intact.
        int storedCount = original.getCount();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag before = (CompoundTag) ItemStack.CODEC.encodeStart(ops, original.copyWithCount(1)).getOrThrow();
        before.putInt("count", storedCount);
        CompoundTag recovered = before.copy();
        LegacyBridge.prepareStack(recovered, account);
        if (recovered.equals(before)) {
            return original;
        }

        if (HistoricalItems.stackCount(recovered) != storedCount) {
            throw new LegacyBridge.RecoveryAbort("Item recovery changed the stored count for " + registryId, null);
        }
        // Decode the recovered identity and components at the codec's normal
        // count, then restore the full storage count on the resulting stack.
        recovered.putInt("count", 1);
        ItemStack result = ItemStack.CODEC.parse(ops, recovered).getOrThrow(message ->
                new LegacyBridge.RecoveryAbort("Cannot decode recovered item "
                        + recovered.getStringOr("id", "") + ": " + message, null));
        result.setCount(storedCount);
        return result;
    }
}
