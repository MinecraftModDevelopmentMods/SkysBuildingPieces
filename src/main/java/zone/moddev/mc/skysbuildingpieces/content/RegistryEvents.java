package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
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
    public static void serializers(RegistryEvent.Register<net.minecraft.item.crafting.IRecipeSerializer<?>> event) {
        event.getRegistry().register(CuttingRecipe.SERIALIZER.setRegistryName(SkysBuildingPieces.ID, "cutting"));
        event.getRegistry()
                .register(LegacyPieceRecipe.SERIALIZER.setRegistryName(SkysBuildingPieces.ID, "legacy_piece"));
    }

    @SubscribeEvent
    public static void missingBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
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

    @SubscribeEvent
    public static void missingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            Block former = MineralogyCompat.formerBlock(mapping.key.toString());

            if (former != null) {
                mapping.remap(former.asItem());
            } else if (LegacyMapping.owned(mapping.key.toString())) {
                mapping.ignore();
            }
        }
    }
}
