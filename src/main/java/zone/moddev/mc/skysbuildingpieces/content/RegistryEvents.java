package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyRegistryAliases;

/** Catalogue contributions are complete before NeoForge opens the registries. */
@EventBusSubscriber(
        modid = SkysBuildingPieces.ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class RegistryEvents {
    private RegistryEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            blocks();
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            items();
        } else if (event.getRegistryKey().equals(Registries.SOUND_EVENT)) {
            LegacyRegistryAliases.register(BuiltInRegistries.SOUND_EVENT,
                    new ResourceLocation("minecraft:item.sweet_berries.pick_from_bush"),
                    BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES));
        } else if (event.getRegistryKey().equals(Registries.RECIPE_SERIALIZER)) {
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                    new ResourceLocation(SkysBuildingPieces.ID, "cutting"), CuttingRecipe.SERIALIZER);
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                    new ResourceLocation(SkysBuildingPieces.ID, "legacy_piece"), LegacyPieceRecipe.SERIALIZER);
        }
    }

    private static void blocks() {
        SkysBuildingPieces.HOLDER = new Block(Block.Properties.of().mapColor(MapColor.STONE)
                .strength(-1.0F, 3_600_000.0F));
        Pieces.registerBlocks(BuiltInRegistries.BLOCK);
        LegacyRegistryAliases.register(BuiltInRegistries.BLOCK, new ResourceLocation("minecraft:grass_path"),
                new ResourceLocation("minecraft:dirt_path"));
        MineralogyCompat.registerFormerAliases(BuiltInRegistries.BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(SkysBuildingPieces.ID, "legacy_piece_holder"),
                SkysBuildingPieces.HOLDER);
    }

    private static void items() {
        Pieces.registerItems(BuiltInRegistries.ITEM);
        LegacyRegistryAliases.register(BuiltInRegistries.ITEM, new ResourceLocation("minecraft:grass_path"),
                new ResourceLocation("minecraft:dirt_path"));
        MineralogyCompat.registerFormerAliases(BuiltInRegistries.ITEM);

        BlockItem holder = new BlockItem(SkysBuildingPieces.HOLDER, new Item.Properties());

        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(SkysBuildingPieces.ID, "legacy_piece_holder"), holder);
    }

}
