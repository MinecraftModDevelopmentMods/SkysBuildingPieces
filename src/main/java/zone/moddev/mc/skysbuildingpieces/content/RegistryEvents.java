package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;

/** Catalogue contributions are complete before Forge opens the registries. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPieces.ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RegistryEvents {
    private RegistryEvents() {
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST)
    public static void blocks(RegistryEvent.Register<Block> event) {
        Pieces.registerBlocks(event.getRegistry());
        event.getRegistry().register(SkysBuildingPieces.HOLDER);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        Pieces.registerItems(event.getRegistry());

        BlockItem holder = new BlockItem(SkysBuildingPieces.HOLDER, new Item.Properties());

        holder.setRegistryName(SkysBuildingPieces.HOLDER.getRegistryName());
        event.getRegistry().register(holder);
    }

    @SubscribeEvent
    public static void serializers(RegistryEvent.Register<net.minecraft.world.item.crafting.RecipeSerializer<?>> event) {
        event.getRegistry().register(CuttingRecipe.SERIALIZER.setRegistryName(SkysBuildingPieces.ID, "cutting"));
        event.getRegistry()
                .register(LegacyPieceRecipe.SERIALIZER.setRegistryName(SkysBuildingPieces.ID, "legacy_piece"));
    }

    public static void missingBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            if (mapping.key.toString().equals("minecraft:grass_path")) {
                mapping.remap(Blocks.DIRT_PATH);
                continue;
            }
            Block former = MineralogyCompat.formerBlock(mapping.key.toString());

            if (former != null) {
                mapping.remap(former);
            } else if (LegacyMapping.owned(mapping.key.toString())) {
                // The early loader resolves material NBT before these old generic identities disappear. Unsafe sources
                // fail the startup preflight.
                mapping.ignore();
            }
        }
    }

    public static void missingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            if (mapping.key.toString().equals("minecraft:grass_path")) {
                mapping.remap(Items.DIRT_PATH);
                continue;
            }
            Block former = MineralogyCompat.formerBlock(mapping.key.toString());

            if (former != null) {
                mapping.remap(former.asItem());
            } else if (LegacyMapping.owned(mapping.key.toString())) {
                mapping.ignore();
            }
        }
    }

    public static void missingSounds(RegistryEvent.MissingMappings<SoundEvent> event) {
        // Keep the native rename available when Grass Slabs is not installed.
        for (RegistryEvent.MissingMappings.Mapping<SoundEvent> mapping : event.getAllMappings()) {
            if (mapping.key.toString().equals("minecraft:item.sweet_berries.pick_from_bush")) {
                mapping.remap(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES);
            }
        }
    }
}
