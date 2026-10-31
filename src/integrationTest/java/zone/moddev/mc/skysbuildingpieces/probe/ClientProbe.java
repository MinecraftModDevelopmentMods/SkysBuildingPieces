package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Client resource bake check; never shipped with the mod. */
public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean startedWorld;

    public void init() {
        if (System.getProperty("skysbuildingpieces.integrationPhase",
                System.getProperty("skysbuildingpieces.bootstrapPhase", "")).startsWith("client")) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.mainWindow.getHandle());

        if (ClientStatus.failure != null) {
            minecraft.shutdown();
            return;
        }

        if (ran) {
            if (startedWorld && ClientStatus.complete && minecraft.world != null && minecraft.getConnection() != null
                    && !minecraft.getConnection().getRecipeManager().getRecipes().isEmpty()) {
                RecipePreviewModels.check(minecraft);
                colours(minecraft);
                System.out.println("BUILDING_PIECES_CLIENT_WORLD_ACCEPTED");
                minecraft.shutdown();
                startedWorld = false;
            }

            return;
        }

        ran = true;

        try {
            org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.mainWindow.getHandle());

            for (net.minecraft.item.Item retired : Pieces.RETIRED_TEMPLATES.values()) {
                if (retired.getGroup() != null) {
                    throw new AssertionError("Retired template appears in creative inventory");
                }
            }

            net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> creative = net.minecraft.util.NonNullList
                    .create();

            for (net.minecraft.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues()) {
                if (!(item instanceof PieceItem)) {
                    continue;
                }

                PieceItem piece = (PieceItem) item;
                boolean hidden = piece.piece.palette.shape == zone.moddev.mc.skysbuildingpieces.catalogue.Shape.VERTICAL_SLAB
                        || Pieces.nativeState(
                                zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.materials
                                        .get(piece.material),
                                piece.piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT)) != null;

                if (hidden ? item.getGroup() != null : item.getGroup() != PiecesTab.INSTANCE) {
                    throw new AssertionError("Incorrect creative visibility " + item.getRegistryName());
                }
            }

            PiecesTab.INSTANCE.fill(creative);

            java.util.Set<String> entries = new java.util.HashSet<String>();

            for (net.minecraft.item.ItemStack stack : creative) {
                String key = stack.getItem().getRegistryName().toString();

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
            if (!entries.contains("minecraft:oak_slab")) {
                throw new AssertionError("Reused oak slab missing from mod tab");
            }
            if (entries.contains("skysbuildingpieces:legacy_piece_holder")) {
                throw new AssertionError("Recovery holder in mod tab");
            }
            if (!net.minecraft.client.resources.I18n.format("itemGroup.skysbuildingpieces")
                    .equals("Sky's Building Pieces")) {
                throw new AssertionError("Missing creative tab name");
            }

            System.out.println("BUILDING_PIECES_CREATIVE_TAB_PASS entries=" + creative.size());

            PresentationChecks.names();

            int checked = 0;

            for (PieceBlock block : Pieces.BLOCKS.values()) {
                for (BlockState state : block.getStateContainer().getValidStates()) {
                    IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);

                    if (model == minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                            .getMissingModel()) {
                        throw new AssertionError("Missing baked model " + state);
                    }
                    for (Direction face : new Direction[] { null, Direction.DOWN, Direction.UP, Direction.NORTH,
                            Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                        for (BakedQuad quad : model.getQuads(state, face, new java.util.Random(0))) {
                            if (quad.getSprite().getName().toString().contains("missingno")) {
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
                net.minecraft.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                        .getValue(new net.minecraft.util.ResourceLocation("skysbuildingpiecesbop:" + name));

                if (block == null || block == net.minecraft.block.Blocks.AIR) {
                    continue;
                }
                for (BlockState state : block.getStateContainer().getValidStates()) {
                    IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);

                    if (model == minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                            .getMissingModel()) {
                        throw new AssertionError("Missing decoration model " + state);
                    }
                    for (BakedQuad quad : model.getQuads(state, null, new java.util.Random(0))) {
                        if (quad.getSprite().getName().toString().contains("missingno")) {
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
                net.minecraft.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                        .getValue(new net.minecraft.util.ResourceLocation(name));

                if (block == null || block == net.minecraft.block.Blocks.AIR) {
                    continue;
                }
                for (BlockState state : block.getStateContainer().getValidStates()) {
                    IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);

                    if (model == minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                            .getMissingModel()) {
                        throw new AssertionError("Missing recovery model " + state);
                    }
                    for (Direction face : new Direction[] { null, Direction.DOWN, Direction.UP, Direction.NORTH,
                            Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                        for (BakedQuad quad : model.getQuads(state, face, new java.util.Random(0))) {
                            if (quad.getSprite().getName().toString().contains("missingno")) {
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

            if (System.getProperty("skysbuildingpieces.integrationPhase",
                    System.getProperty("skysbuildingpieces.bootstrapPhase", "")).equals("client")) {
                minecraft.shutdown();
            } else {
                startedWorld = true;
                minecraft.launchIntegratedServer("building-pieces-world", "Building Pieces Test",
                        new net.minecraft.world.WorldSettings(4815162342L, net.minecraft.world.GameType.CREATIVE, true,
                                false, net.minecraft.world.WorldType.FLAT));
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            minecraft.shutdown();
        }
    }

    private static void colours(Minecraft client) {
        net.minecraft.util.math.BlockPos sample = new net.minecraft.util.math.BlockPos(client.player);
        int expected = client.getBlockColors().getColor(net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState(),
                client.world, sample, 0);
        int inventory = net.minecraft.world.GrassColors.get(.5, 1);
        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (!block.palette.group.equals("grass")) {
                continue;
            }

            BlockState state = block.getDefaultState();

            if (client.getBlockColors().getColor(state, client.world, sample, 0) != expected
                    || client.getBlockColors().getColor(state, client.world, sample, 1) != -1
                    || block.getRenderLayer() != net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED) {
                throw new AssertionError("Grass-piece biome tint or render layer: " + block.getRegistryName());
            }

            net.minecraft.item.ItemStack stack = Pieces.stack(state, 1);

            if (client.getItemColors().getColor(stack, 0) != inventory
                    || client.getItemColors().getColor(stack, 1) != -1) {
                throw new AssertionError("Grass-piece inventory tint: " + stack.getItem().getRegistryName());
            }

            checked++;
        }
        if (checked == 0) {
            throw new AssertionError("No grass pieces reached the colour checks");
        }

        System.out.println("BUILDING_PIECES_COLOURS_PASS palettes=" + checked + " biome=true inventory=true");
    }
}
