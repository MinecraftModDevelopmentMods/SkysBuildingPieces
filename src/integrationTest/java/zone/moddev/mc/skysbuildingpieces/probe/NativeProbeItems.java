package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.mojang.serialization.Dynamic;

public final class NativeProbeItems {
    private NativeProbeItems() {
    }

    static ServerPlayer player(ServerLevel world) {
        return player(world, new GameProfile(UUID.fromString("fa99c347-23f8-4b9c-8c1a-eac02ef1b891"), "PiecesProbe"));
    }

    static ServerPlayer player(ServerLevel world, GameProfile profile) {
        return new ServerPlayer(world.getServer(), world, profile, ClientInformation.createDefault());
    }

    public static void setCustom(ItemStack stack, CompoundTag data) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static CompoundTag custom(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean hasCustom(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    public static CompoundTag save(ItemStack stack) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(ServerLifecycleHooks.getCurrentServer().registryAccess()
                .createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static ItemStack read(CompoundTag tag) {
        if ("minecraft:grass_path".equals(tag.getStringOr("id", ""))) {
            // Older fixture descriptions retained this alias after the saved item had been renamed.
            tag = tag.copy();
            tag.putString("id", "minecraft:dirt_path");
        }
        if (tag.getInt("Count").isPresent()) {
            // These fixture stacks use the post-flattening layout, before item components.
            tag = (CompoundTag) DataFixers.getDataFixer()
                    .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, tag), 3700,
                            net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version())
                    .getValue();
        }
        return ItemStack.CODEC.parse(ServerLifecycleHooks.getCurrentServer().registryAccess()
                .createSerializationContext(NbtOps.INSTANCE), tag)
                .getOrThrow(message -> new IllegalStateException("Fixture item could not be read: " + message));
    }
}
