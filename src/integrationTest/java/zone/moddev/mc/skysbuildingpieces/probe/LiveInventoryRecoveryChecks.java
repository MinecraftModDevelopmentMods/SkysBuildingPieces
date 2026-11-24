package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

/** Live storage counts must not be narrowed to the saved byte-sized Count. */
final class LiveInventoryRecoveryChecks {
    private LiveInventoryRecoveryChecks() {
    }

    static void run(ServerLevel world) {
        PieceBlock palette = Pieces.BLOCKS.values().stream()
                .filter(piece -> piece.palette.shape == Shape.CORNER
                        && piece.palette.materials.contains("minecraft:stone"))
                .findFirst().orElseThrow();
        String oldId = BuiltInRegistries.BLOCK.getKey(palette).toString();
        var oldItem = BuiltInRegistries.ITEM.get(BuiltInRegistries.BLOCK.getKey(palette));
        ItemStack expected = Pieces.legacyItem(oldId, 0, 1);
        require(oldItem != Items.AIR && !expected.isEmpty(), "Use real registered legacy and current pieces");

        for (int count : new int[] {1, 64, 99, 100, 127, 128, 255, 256, 1895, Integer.MAX_VALUE}) {
            ItemStack ordinary = new ItemStack(Items.STONE, count);
            require(MigrationEvents.convert(ordinary, world) == ordinary, "Leave unrelated storage stacks alone");
            ItemStack current = expected.copyWithCount(count);
            require(MigrationEvents.convert(current, world) == current, "Current pieces are already recovered");
            ItemStack saved = new ItemStack(oldItem, count);
            saved.setHoverName(Component.literal("Stored stone corner"));
            saved.getOrCreateTag().putString("owner", "Keep this storage data");
            ItemStack before = saved.copy();
            ItemStack converted = MigrationEvents.convert(saved, world);
            require(converted != saved && converted.is(expected.getItem()) && converted.getCount() == count,
                    "Recover the piece without narrowing its storage count: " + count);
            require(converted.getTag().equals(before.getTag()), "Keep names and unrelated item NBT");
            require(ItemStack.matches(before, saved), "Never mutate the handler's original stack");
            require(MigrationEvents.convert(converted, world) == converted, "Recovered storage remains stable");
        }

        CompoundTag nested = new CompoundTag();
        nested.putString("id", oldId);
        nested.putByte("Count", (byte) 17);
        nested.putShort("Damage", (short) 0);
        nested.putInt("Slot", 7);
        ListTag contents = new ListTag();
        contents.add(nested);
        ItemStack container = new ItemStack(Items.CHEST, 1895);
        container.getOrCreateTag().put("Items", contents);
        container.getOrCreateTag().putString("owner", "A sealed storage item");
        ItemStack before = container.copy();
        ItemStack recovered = MigrationEvents.convert(container, world);
        CompoundTag recoveredItem = recovered.getTag().getList("Items", 10).getCompound(0);
        require(recovered.getCount() == 1895 && recoveredItem.getByte("Count") == 17
                        && recoveredItem.getInt("Slot") == 7
                        && recoveredItem.getString("id").equals(BuiltInRegistries.ITEM.getKey(expected.getItem()).toString())
                        && recovered.getTag().getString("owner").equals(before.getTag().getString("owner")),
                "Keep outer counts, nested counts, slots and custom data");
        require(ItemStack.matches(before, container), "Nested recovery leaves its source unchanged");
        require(MigrationEvents.convert(recovered, world) == recovered, "Nested recovery is idempotent");

        CompoundTag invalid = nested.copy();
        invalid.putString("id", "buildingbricks:rock_step");
        CompoundTag material = new CompoundTag();
        material.putString("material", "missing:unknown");
        invalid.put("tag", material);
        contents.add(invalid);
        ItemStack beforeFailure = container.copy();
        boolean stopped = false;
        try {
            MigrationEvents.convert(container, world);
        } catch (LegacyBridge.RecoveryAbort expectedFailure) {
            stopped = true;
        }
        require(stopped && ItemStack.matches(beforeFailure, container), "Unknown content stops without changing its source");
        System.out.println("BUILDING_PIECES_LIVE_STORAGE_PASS counts=1,64,99,100,127,128,255,256,1895,2147483647 nested=true atomic=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
