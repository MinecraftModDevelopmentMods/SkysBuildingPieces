package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;

/** Catalogue contributions are complete before Forge opens the registries. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPieces.ID)
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
        event.getRegistry().register(
                new ItemBlock(SkysBuildingPieces.HOLDER).setRegistryName(SkysBuildingPieces.HOLDER.getRegistryName()));
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST)
    public static void recipes(RegistryEvent.Register<IRecipe> event) {
        CuttingRecipe.register(event.getRegistry());
    }

    @SubscribeEvent
    public static void missingBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            if (LegacyMapping.owned(mapping.key.toString())) {
                mapping.ignore();
            }
        }
    }

    @SubscribeEvent
    public static void missingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            if (LegacyMapping.owned(mapping.key.toString())) {
                mapping.ignore();
            }
        }
    }
}
