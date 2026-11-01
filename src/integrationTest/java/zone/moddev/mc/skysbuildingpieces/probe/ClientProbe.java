package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.GrassColor;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Client resource bake check; never shipped with the mod. */
public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean startedWorld;
    private long worldStarted;

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

        org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.getWindow().getWindow());

        if (ClientStatus.failure != null) {
            minecraft.stop();
            return;
        }

        if (ran) {
            if (startedWorld && minecraft.level == null) {
                confirmDisposableWorld(minecraft);
                if (System.nanoTime() - worldStarted > java.util.concurrent.TimeUnit.SECONDS.toNanos(180)) {
                    ClientStatus.failure = new AssertionError("Disposable client world did not start; screen="
                            + (minecraft.screen == null ? "none" : minecraft.screen.getClass().getName()));
                    org.apache.logging.log4j.LogManager.getLogger().error("Client runtime check failed", ClientStatus.failure);
                    minecraft.stop();
                    return;
                }
            }
            if (startedWorld && ClientStatus.complete && minecraft.level != null && minecraft.getConnection() != null
                    && !minecraft.getConnection().getRecipeManager().getRecipes().isEmpty()) {
                RecipePreviewModels.check(minecraft);
                colours(minecraft);
                org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_CLIENT_WORLD_ACCEPTED");
                minecraft.stop();
                startedWorld = false;
            }

            return;
        }

        ran = true;

        try {
            org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.getWindow().getWindow());

            for (net.minecraft.world.item.Item retired : Pieces.RETIRED_TEMPLATES.values()) {
                if (retired.getItemCategory() != null) {
                    throw new AssertionError("Retired template appears in creative inventory");
                }
            }

            net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> creative = net.minecraft.core.NonNullList
                    .create();

            for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues()) {
                if (!(item instanceof PieceItem)) {
                    continue;
                }

                PieceItem piece = (PieceItem) item;
                boolean hidden = zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.materials
                        .get(piece.material).replacement() != null
                        || piece.piece.palette.shape == zone.moddev.mc.skysbuildingpieces.catalogue.Shape.VERTICAL_SLAB
                        || Pieces.nativeState(
                                zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.materials
                                        .get(piece.material),
                                piece.piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT)) != null;

                if (hidden ? item.getItemCategory() != null : item.getItemCategory() != PiecesTab.INSTANCE) {
                    throw new AssertionError("Incorrect creative visibility " + item.getRegistryName());
                }
            }

            PiecesTab.INSTANCE.fillItemList(creative);

            java.util.Set<String> entries = new java.util.HashSet<String>();

            for (net.minecraft.world.item.ItemStack stack : creative) {
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
            if (!net.minecraft.client.resources.language.I18n.get("itemGroup.skysbuildingpieces")
                    .equals("Sky's Building Pieces")) {
                throw new AssertionError("Missing creative tab name");
            }

            org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_CREATIVE_TAB_PASS entries=" + creative.size());

            PresentationChecks.names();

            int checked = 0;

            for (PieceBlock block : Pieces.BLOCKS.values()) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);

                    if (model == minecraft.getBlockRenderer().getBlockModelShaper().getModelManager()
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
                net.minecraft.world.level.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                        .getValue(new net.minecraft.resources.ResourceLocation("skysbuildingpiecesbop:" + name));

                if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                    continue;
                }
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);

                    if (model == minecraft.getBlockRenderer().getBlockModelShaper().getModelManager()
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
                net.minecraft.world.level.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                        .getValue(new net.minecraft.resources.ResourceLocation(name));

                if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                    continue;
                }
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);

                    if (model == minecraft.getBlockRenderer().getBlockModelShaper().getModelManager()
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
            try (PrintWriter writer = new PrintWriter(new File(minecraft.gameDirectory, "building-pieces-client-pass.txt"),
                    "UTF-8")) {
                writer.println("baked_states=" + checked);
                writer.println("recovery_models=" + recoveryModels);
            }

            org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_CLIENT_PASS " + checked);

            if (System.getProperty("skysbuildingpieces.integrationPhase",
                    System.getProperty("skysbuildingpieces.bootstrapPhase", "")).equals("client")) {
                minecraft.stop();
            } else {
                startedWorld = true;
                worldStarted = System.nanoTime();
                if (new File(minecraft.gameDirectory, "saves/building-pieces-world/level.dat").isFile()) {
                    minecraft.loadLevel("building-pieces-world");
                } else {
                    net.minecraft.core.RegistryAccess.RegistryHolder registries =
                            net.minecraft.core.RegistryAccess.builtin();
                    net.minecraft.core.Registry<net.minecraft.world.level.dimension.DimensionType> dimensions =
                            registries.registryOrThrow(net.minecraft.core.Registry.DIMENSION_TYPE_REGISTRY);
                    net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomes =
                            registries.registryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY);
                    net.minecraft.world.level.levelgen.WorldGenSettings defaults =
                            net.minecraft.world.level.levelgen.WorldGenSettings.makeDefault(dimensions, biomes,
                                    registries.registryOrThrow(net.minecraft.core.Registry.NOISE_GENERATOR_SETTINGS_REGISTRY));
                    net.minecraft.world.level.levelgen.WorldGenSettings generator =
                            new net.minecraft.world.level.levelgen.WorldGenSettings(4815162342L, false, false,
                                    net.minecraft.world.level.levelgen.WorldGenSettings.withOverworld(dimensions,
                                            defaults.dimensions(), new net.minecraft.world.level.levelgen.FlatLevelSource(
                                                    net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings.getDefault(biomes))));
                    net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(
                            "Building Pieces Test", net.minecraft.world.level.GameType.CREATIVE, false,
                            net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.level.GameRules(),
                            net.minecraft.world.level.DataPackConfig.DEFAULT);
                    minecraft.createLevel("building-pieces-world", settings, registries, generator);
                }
            }
        } catch (Throwable failure) {
            org.apache.logging.log4j.LogManager.getLogger().error("Runtime check failed", failure);
            minecraft.stop();
        }
    }

    private static void confirmDisposableWorld(Minecraft client) {
        net.minecraft.client.gui.screens.Screen screen = client.screen;
        if (screen == null || !(screen.getTitle() instanceof net.minecraft.network.chat.TranslatableComponent)
                || !((net.minecraft.network.chat.TranslatableComponent) screen.getTitle()).getKey()
                        .equals("selectWorld.backupQuestion.experimental")) {
            return;
        }
        net.minecraft.network.chat.Component proceed;
        if (screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
            proceed = new net.minecraft.network.chat.TranslatableComponent("selectWorld.backupJoinSkipButton");
        } else if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
            proceed = net.minecraft.network.chat.CommonComponents.GUI_PROCEED;
        } else {
            return;
        }
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button) {
                net.minecraft.client.gui.components.Button button =
                        (net.minecraft.client.gui.components.Button) child;
                if (button.getMessage().equals(proceed)) {
                    // Only the marked probe's disposable world reaches this confirmation.
                    button.onPress();
                    return;
                }
            }
        }
        throw new AssertionError("The native experimental-world confirmation has no proceed action");
    }

    private static void colours(Minecraft client) {
        net.minecraft.core.BlockPos sample = client.player.blockPosition();
        int expected = client.getBlockColors().getColor(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),
                client.level, sample, 0);
        int inventory = net.minecraft.world.level.GrassColor.get(.5, 1);
        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (!block.palette.group.equals("grass")) {
                continue;
            }

            BlockState state = block.defaultBlockState();

            if (client.getBlockColors().getColor(state, client.level, sample, 0) != expected
                    || client.getBlockColors().getColor(state, client.level, sample, 1) != -1
                    || !net.minecraft.client.renderer.ItemBlockRenderTypes.canRenderInLayer(state,
                            net.minecraft.client.renderer.RenderType.cutoutMipped())
                    || net.minecraft.client.renderer.ItemBlockRenderTypes.canRenderInLayer(state,
                            net.minecraft.client.renderer.RenderType.solid())) {
                throw new AssertionError("Grass-piece biome tint or render layer: " + block.getRegistryName());
            }

            net.minecraft.world.item.ItemStack stack = Pieces.stack(state, 1);

            if (client.getItemColors().getColor(stack, 0) != inventory
                    || client.getItemColors().getColor(stack, 1) != -1) {
                throw new AssertionError("Grass-piece inventory tint: " + stack.getItem().getRegistryName());
            }

            checked++;
        }
        if (checked == 0) {
            throw new AssertionError("No grass pieces reached the colour checks");
        }

        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_COLOURS_PASS palettes=" + checked + " biome=true inventory=true");
    }
}
