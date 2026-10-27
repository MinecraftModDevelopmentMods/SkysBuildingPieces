package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.*;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import zone.moddev.mc.skysbuildingpieces.CommonProxy;
import zone.moddev.mc.skysbuildingpieces.content.*;

public final class ClientProxy extends CommonProxy {
    public void preInit() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new WallHighlight());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ContinuousGlassModel.BakeHandler());
        registerModels("skysbuildingpieces");

        for (java.util.Map.Entry<String, Item> entry : Pieces.RETIRED_TEMPLATES.entrySet()) {
            ModelLoader.setCustomModelResourceLocation(entry.getValue(), 0,
                    new ModelResourceLocation("skysbuildingpieces:template_" + entry.getKey(), "inventory"));
        }
    }

    public void registerModels(String namespace) {
        for (PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            if (!pieceBlock.palette.namespace.equals(namespace)) {
                continue;
            }

            net.minecraft.client.renderer.block.statemap.StateMap.Builder map = new net.minecraft.client.renderer.block.statemap.StateMap.Builder();

            if (pieceBlock.palette.shape != zone.moddev.mc.skysbuildingpieces.catalogue.Shape.WALL
                    && pieceBlock.palette.shape != zone.moddev.mc.skysbuildingpieces.catalogue.Shape.PANE) {
                map.ignore(PieceBlock.CONNECTIONS);
            }
            if (pieceBlock.palette.shape != zone.moddev.mc.skysbuildingpieces.catalogue.Shape.STAIRS) {
                map.ignore(PieceBlock.STAIR_SHAPE);
            }
            if (!pieceBlock.palette.group.equals("grass") && !pieceBlock.palette.group.equals("dirt")) {
                map.ignore(net.minecraft.block.BlockGrass.SNOWY);
            }

            ModelLoader.setCustomStateMapper(pieceBlock, map.build());
        }
        for (PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            for (int slot = 0; slot < pieceBlock.palette.materials.size(); slot++) {
                if (!pieceBlock.palette.namespace.equals(namespace)) {
                    continue;
                }

                int meta = slot * pieceBlock.palette.shape.states;

                ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(pieceBlock), meta,
                        new ModelResourceLocation(
                                (pieceBlock.palette.module.equals("mineralogy")
                                        ? zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.ASSETS : namespace) + ":"
                                        + new net.minecraft.util.ResourceLocation(
                                                pieceBlock.palette.materials.get(slot)).getResourcePath()
                                        + "_" + pieceBlock.palette.shape.name().toLowerCase(java.util.Locale.ROOT),
                                "inventory"));
            }
        }
    }

    public void init() {
        final BlockColors colors = Minecraft.getMinecraft().getBlockColors();

        for (final PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            if (pieceBlock.palette.group.equals("grass")) {
                colors.registerBlockColorHandler((state, world, pos,
                        tint) -> tint == 0 ? colors.colorMultiplier(Blocks.GRASS.getDefaultState(), world, pos, 0) : -1,
                        pieceBlock);
                Minecraft.getMinecraft().getItemColors().registerItemColorHandler(
                        (stack, tint) -> tint == 0 ? 0x7CBD6B : -1, Item.getItemFromBlock(pieceBlock));
            }
        }
    }
}
