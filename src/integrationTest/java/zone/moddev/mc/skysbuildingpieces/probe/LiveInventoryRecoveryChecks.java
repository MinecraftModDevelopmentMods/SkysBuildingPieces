package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

/** Checks live storage counts as well as the early raw-NBT recovery fixtures. */
final class LiveInventoryRecoveryChecks {
    private LiveInventoryRecoveryChecks() {
    }

    static void run(ServerLevel world) {
        PieceBlock palette = Pieces.BLOCKS.values().stream()
                .filter(piece -> piece.palette.shape == Shape.CORNER
                        && piece.palette.materials.contains("minecraft:stone"))
                .findFirst().orElseThrow();
        String oldId = BuiltInRegistries.BLOCK.getKey(palette).toString();
        var oldItem = BuiltInRegistries.ITEM.getValue(BuiltInRegistries.BLOCK.getKey(palette));
        ItemStack expected = Pieces.legacyItem(oldId, 0, 1);
        require(oldItem != Items.AIR && !expected.isEmpty(), "The saved-palette fixture needs real registered items");

        for (int count : new int[] {1, 64, 99, 100, 1895, Integer.MAX_VALUE}) {
            ItemStack ordinary = new ItemStack(Items.STONE, count);
            require(MigrationEvents.convert(ordinary, world) == ordinary,
                    "Unrelated storage stacks should retain their original object");

            ItemStack current = expected.copyWithCount(count);
            require(MigrationEvents.convert(current, world) == current,
                    "Current pieces should not be migrated again");

            ItemStack saved = new ItemStack(oldItem, count);
            saved.set(DataComponents.CUSTOM_NAME, Component.literal("Saved stone corner"));
            CompoundTag custom = new CompoundTag();
            custom.putString("owner", "Keep this storage data");
            custom.putInt("unrelatedCount", 75000);
            saved.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            ItemStack before = saved.copy();
            ItemStack converted = MigrationEvents.convert(saved, world);
            require(converted != saved && converted.is(expected.getItem()) && converted.getCount() == count,
                    "Recover the material without limiting the storage count");
            require(converted.getComponentsPatch().equals(saved.getComponentsPatch()),
                    "Recovered pieces must retain their names and custom components");
            require(ItemStack.matches(before, saved), "Recovery must not mutate a handler's original stack");
            require(MigrationEvents.convert(converted, world) == converted,
                    "A recovered storage stack must be stable on the next scan");
        }

        CompoundTag oldNested = new CompoundTag();
        oldNested.putString("id", oldId);
        oldNested.putInt("count", 1895);
        oldNested.putShort("Damage", (short) 0);
        oldNested.putInt("Slot", 7);
        ListTag nestedItems = new ListTag();
        nestedItems.add(oldNested);
        CompoundTag contents = new CompoundTag();
        contents.putString("owner", "A sealed storage item");
        contents.put("Items", nestedItems);
        ItemStack outer = new ItemStack(Items.STONE, 1895);
        outer.set(DataComponents.CUSTOM_DATA, CustomData.of(contents));
        ItemStack untouchedOuter = outer.copy();
        ItemStack recoveredOuter = MigrationEvents.convert(outer, world);
        CompoundTag recoveredData = recoveredOuter.get(DataComponents.CUSTOM_DATA).copyTag();
        CompoundTag recoveredNested = recoveredData.getListOrEmpty("Items").getCompoundOrEmpty(0);
        require(recoveredOuter.getCount() == 1895 && recoveredNested.getIntOr("count", 0) == 1895
                        && recoveredNested.getIntOr("Slot", 0) == 7
                        && recoveredNested.getStringOr("id", "").equals(BuiltInRegistries.ITEM.getKey(expected.getItem()).toString())
                        && recoveredData.getStringOr("owner", "").equals(contents.getStringOr("owner", "")),
                "Nested raw storage must retain inner and outer counts, slots and unrelated data");
        require(ItemStack.matches(untouchedOuter, outer), "Nested recovery must leave the original container untouched");
        require(MigrationEvents.convert(recoveredOuter, world) == recoveredOuter, "Nested recovery must be idempotent");

        ItemStack nativeContainer = new ItemStack(Items.CHEST);
        nativeContainer.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(oldItem, 17))));
        ItemStack originalContainer = nativeContainer.copy();
        ItemStack recoveredContainer = MigrationEvents.convert(nativeContainer, world);
        ItemStack nested = recoveredContainer.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().findFirst().orElseThrow();
        require(nested.is(expected.getItem()) && nested.getCount() == 17,
                "Typed container components must still recover saved pieces");
        require(ItemStack.matches(originalContainer, nativeContainer), "Typed recovery must not mutate the source");

        CompoundTag invalid = new CompoundTag();
        invalid.putString("id", "buildingbricks:rock_step");
        invalid.putInt("count", 1895);
        CompoundTag material = new CompoundTag();
        material.putString("material", "missing:unknown");
        CompoundTag components = new CompoundTag();
        components.put("minecraft:custom_data", material);
        invalid.put("components", components);
        ListTag mixed = new ListTag();
        mixed.add(oldNested.copy());
        mixed.add(invalid);
        CompoundTag invalidContents = new CompoundTag();
        invalidContents.put("Items", mixed);
        ItemStack invalidContainer = new ItemStack(Items.CHEST);
        invalidContainer.set(DataComponents.CUSTOM_DATA, CustomData.of(invalidContents));
        ItemStack beforeFailure = invalidContainer.copy();
        boolean stopped = false;
        try {
            MigrationEvents.convert(invalidContainer, world);
        } catch (LegacyBridge.RecoveryAbort expectedFailure) {
            stopped = expectedFailure.getMessage().contains("missing:unknown");
        }
        require(stopped && ItemStack.matches(beforeFailure, invalidContainer),
                "An unsafe nested target must stop before a valid sibling or the original stack changes");
        System.out.println("BUILDING_PIECES_LIVE_STORAGE_PASS counts=1,64,99,100,1895,2147483647 nested=true atomic=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
