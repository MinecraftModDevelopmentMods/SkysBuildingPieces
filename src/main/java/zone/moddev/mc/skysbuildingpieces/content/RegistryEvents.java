package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS)) {
            blocks();
        } else if (event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            items();
        } else if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
            ForgeRegistries.RECIPE_SERIALIZERS.register(
                    new ResourceLocation(SkysBuildingPieces.ID, "cutting"), CuttingRecipe.SERIALIZER);
            ForgeRegistries.RECIPE_SERIALIZERS.register(
                    new ResourceLocation(SkysBuildingPieces.ID, "legacy_piece"), LegacyPieceRecipe.SERIALIZER);
        }
    }

    private static void blocks() {
        SkysBuildingPieces.HOLDER = new Block(Block.Properties.of(net.minecraft.world.level.material.Material.STONE)
                .strength(-1.0F, 3_600_000.0F));
        Pieces.registerBlocks(ForgeRegistries.BLOCKS);
        ForgeRegistries.BLOCKS.register(new ResourceLocation(SkysBuildingPieces.ID, "legacy_piece_holder"),
                SkysBuildingPieces.HOLDER);
    }

    private static void items() {
        Pieces.registerItems(ForgeRegistries.ITEMS);

        BlockItem holder = new BlockItem(SkysBuildingPieces.HOLDER, new Item.Properties());

        ForgeRegistries.ITEMS.register(new ResourceLocation(SkysBuildingPieces.ID, "legacy_piece_holder"), holder);
    }

    public static void missingBlocks(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Block> mapping : event.getAllMappings(ForgeRegistries.Keys.BLOCKS)) {
            if (mapping.getKey().toString().equals("minecraft:grass_path")) {
                mapping.remap(Blocks.DIRT_PATH);
                continue;
            }
            Block former = MineralogyCompat.formerBlock(mapping.getKey().toString());

            if (former != null) {
                mapping.remap(former);
            } else if (LegacyMapping.owned(mapping.getKey().toString())) {
                // The early loader resolves material NBT before these old generic identities disappear. Unsafe sources
                // fail the startup preflight.
                mapping.ignore();
            }
        }
    }

    public static void missingItems(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getAllMappings(ForgeRegistries.Keys.ITEMS)) {
            if (mapping.getKey().toString().equals("minecraft:grass_path")) {
                mapping.remap(Items.DIRT_PATH);
                continue;
            }
            Block former = MineralogyCompat.formerBlock(mapping.getKey().toString());

            if (former != null) {
                mapping.remap(former.asItem());
            } else if (LegacyMapping.owned(mapping.getKey().toString())) {
                mapping.ignore();
            }
        }
    }

    public static void missingSounds(MissingMappingsEvent event) {
        // Keep the native rename available when Grass Slabs is not installed.
        for (MissingMappingsEvent.Mapping<SoundEvent> mapping : event.getAllMappings(ForgeRegistries.Keys.SOUND_EVENTS)) {
            if (mapping.getKey().toString().equals("minecraft:item.sweet_berries.pick_from_bush")) {
                mapping.remap(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES);
            }
        }
    }
}
