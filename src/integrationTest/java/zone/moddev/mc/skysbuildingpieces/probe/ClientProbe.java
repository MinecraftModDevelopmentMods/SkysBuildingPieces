package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Client resource bake check; never shipped with the mod. */
public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean startedWorld;

    public void init() {
        if (System.getProperty("skysbuildingpieces.integrationPhase", "").startsWith("client")) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();

        if (ran) {
            if (startedWorld && ClientStatus.complete) {
                System.out.println("BUILDING_PIECES_CLIENT_WORLD_ACCEPTED");
                minecraft.shutdown();
                startedWorld = false;
            }

            return;
        }

        ran = true;

        try {
            for (net.minecraft.item.Item retired : Pieces.RETIRED_TEMPLATES.values()) {
                if (retired.getCreativeTab() != null) {
                    throw new AssertionError("Retired template appears in creative inventory");
                }
            }

            net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> creative = net.minecraft.util.NonNullList
                    .create();

            for (PieceBlock block : Pieces.BLOCKS.values()) {
                if (net.minecraft.item.Item.getItemFromBlock(block).getCreativeTab() != PiecesTab.INSTANCE) {
                    throw new AssertionError("Piece is outside its creative tab");
                }
            }

            PiecesTab.INSTANCE.displayAllRelevantItems(creative);

            java.util.Set<String> entries = new java.util.HashSet<String>();

            for (net.minecraft.item.ItemStack stack : creative) {
                String key = stack.getItem().getRegistryName() + ":" + stack.getMetadata();

                if (!entries.add(key)) {
                    throw new AssertionError("Duplicate creative entry " + key);
                }
                if (stack.getItem() instanceof PieceItem && ((PieceItem) stack
                        .getItem()).piece.palette.shape == zone.moddev.mc.skysbuildingpieces.catalogue.Shape.VERTICAL_SLAB) {
                    throw new AssertionError("Vertical slab appears in creative inventory");
                }
                if (Pieces.RETIRED_TEMPLATES.containsValue(stack.getItem())) {
                    throw new AssertionError("Retired template in mod tab");
                }
            }
            if (!entries.contains("minecraft:wooden_slab:0")) {
                throw new AssertionError("Reused oak slab missing from mod tab");
            }
            if (entries.contains("skysbuildingpieces:legacy_piece_holder:0")) {
                throw new AssertionError("Recovery holder in mod tab");
            }
            if (!net.minecraft.client.resources.I18n.format("itemGroup.skysbuildingpieces")
                    .equals("Sky's Building Pieces")) {
                throw new AssertionError("Missing creative tab name");
            }

            System.out.println("BUILDING_PIECES_CREATIVE_TAB_PASS entries=" + creative.size());

            int checked = 0;

            for (PieceBlock block : Pieces.BLOCKS.values()) {
                for (IBlockState state : block.getBlockState().getValidStates()) {
                    if (state.getValue(PieceBlock.META) / block.palette.shape.states >= block.palette.materials
                            .size()) {
                        continue;
                    }

                    IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);

                    if (model == minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                            .getMissingModel()) {
                        throw new AssertionError("Missing baked model " + state);
                    }
                    for (EnumFacing face : new EnumFacing[] { null, EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH,
                            EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST }) {
                        for (BakedQuad quad : model.getQuads(state, face, 0)) {
                            if (quad.getSprite().getIconName().contains("missingno")) {
                                throw new AssertionError("Missing sprite " + state);
                            }
                        }
                    }

                    checked++;
                }
            }

            int recoveryModels = 0;

            for (String name : new String[] { "stone_formations", "jelled_poison", "legacy_bop_stone_formations",
                    "legacy_bop_jelled_poison", "legacy_bop_tree_moss" }) {
                net.minecraft.block.Block block = net.minecraft.block.Block
                        .getBlockFromName("skysbuildingpiecesbop:" + name);

                if (block == null) {
                    continue;
                }
                for (IBlockState state : block.getBlockState().getValidStates()) {
                    IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);

                    if (model == minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                            .getMissingModel()) {
                        throw new AssertionError("Missing decoration model " + state);
                    }
                    for (BakedQuad quad : model.getQuads(state, null, 0)) {
                        if (quad.getSprite().getIconName().contains("missingno")) {
                            throw new AssertionError("Missing decoration texture " + state);
                        }
                    }

                    recoveryModels++;
                }
            }
            for (String name : new String[] { "skysgrassslabs:grass_slab", "skysgrassslabs:dirt_slab",
                    "skysgrassslabs:path_slab", "skysgrassslabs:turf", "buildingbricks:grass_slab",
                    "buildingbricks:dirt_slab", "buildingbrickscompatvanilla:grass_slab",
                    "skysbuildingpiecesbop:retired_rocks", "skysbuildingpiecesbop:legacy_bop_stone",
                    "skysbuildingpiecesbop:legacy_bop_crag_rock" }) {
                net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromName(name);

                if (block == null) {
                    continue;
                }
                for (IBlockState state : block.getBlockState().getValidStates()) {
                    IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);

                    if (model == minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                            .getMissingModel()) {
                        throw new AssertionError("Missing recovery model " + state);
                    }
                    for (EnumFacing face : new EnumFacing[] { null, EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH,
                            EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST }) {
                        for (BakedQuad quad : model.getQuads(state, face, 0)) {
                            if (quad.getSprite().getIconName().contains("missingno")) {
                                throw new AssertionError("Missing recovery sprite " + state);
                            }
                        }
                    }

                    recoveryModels++;
                }
            }
            try (PrintWriter writer = new PrintWriter(new File(minecraft.gameDir, "building-pieces-client-pass.txt"),
                    "UTF-8")) {
                writer.println("baked_states=" + checked);
                writer.println("recovery_models=" + recoveryModels);
            }

            System.out.println("BUILDING_PIECES_CLIENT_PASS " + checked);

            if (System.getProperty("skysbuildingpieces.integrationPhase").equals("client")) {
                minecraft.shutdown();
            } else {
                startedWorld = true;
                minecraft.launchIntegratedServer("building-pieces-world", "Building Pieces Test",
                        new net.minecraft.world.WorldSettings(4815162342L, net.minecraft.world.GameType.CREATIVE, true,
                                false, net.minecraft.world.WorldType.FLAT));
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            net.minecraftforge.fml.common.FMLCommonHandler.instance().exitJava(2, true);
        }
    }
}
