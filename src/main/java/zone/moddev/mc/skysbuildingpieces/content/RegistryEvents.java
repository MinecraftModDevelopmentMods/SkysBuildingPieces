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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyRegistryAliases;

/** Catalogue contributions are complete before NeoForge opens the registries. */
@EventBusSubscriber(modid = SkysBuildingPieces.ID)
public final class RegistryEvents {
    private static final Identifier HOLDER_ID =
            Identifier.fromNamespaceAndPath(SkysBuildingPieces.ID, "legacy_piece_holder");

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
                    Identifier.parse("minecraft:item.sweet_berries.pick_from_bush"),
                    BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES));
        } else if (event.getRegistryKey().equals(Registries.RECIPE_SERIALIZER)) {
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                    Identifier.fromNamespaceAndPath(SkysBuildingPieces.ID, "cutting"), CuttingRecipe.SERIALIZER);
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                    Identifier.fromNamespaceAndPath(SkysBuildingPieces.ID, "legacy_piece"), LegacyPieceRecipe.SERIALIZER);
        }
    }

    private static void blocks() {
        SkysBuildingPieces.HOLDER = new Block(Block.Properties.of().mapColor(MapColor.STONE)
                .strength(-1.0F, 3_600_000.0F).setId(ResourceKey.create(Registries.BLOCK, HOLDER_ID)));
        Pieces.registerBlocks(BuiltInRegistries.BLOCK);
        LegacyRegistryAliases.register(BuiltInRegistries.BLOCK, Identifier.parse("minecraft:grass_path"),
                Identifier.parse("minecraft:dirt_path"));
        MineralogyCompat.registerFormerAliases(BuiltInRegistries.BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, HOLDER_ID, SkysBuildingPieces.HOLDER);
    }

    private static void items() {
        Pieces.registerItems(BuiltInRegistries.ITEM);
        LegacyRegistryAliases.register(BuiltInRegistries.ITEM, Identifier.parse("minecraft:grass_path"),
                Identifier.parse("minecraft:dirt_path"));
        MineralogyCompat.registerFormerAliases(BuiltInRegistries.ITEM);

        BlockItem holder = new BlockItem(SkysBuildingPieces.HOLDER, new Item.Properties().useBlockDescriptionPrefix()
                .setId(ResourceKey.create(Registries.ITEM, HOLDER_ID)));

        Registry.register(BuiltInRegistries.ITEM, HOLDER_ID, holder);
    }

}
