package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import net.minecraft.world.level.GrassColor;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Client resource bake check; never shipped with the mod. */
public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean startedWorld;
    private boolean confirmedWorld;
    private long worldStarted;
    private boolean requestedRecipes;
    private volatile java.util.Set<net.minecraft.world.item.crafting.display.RecipeDisplayId> expectedRecipes;

    public void init() {
        if (System.getProperty("skysbuildingpieces.integrationPhase",
                System.getProperty("skysbuildingpieces.bootstrapPhase", "")).startsWith("client")) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(this::tick);
        }
    }

    public void tick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        org.lwjgl.sdl.SDLVideo.SDL_HideWindow(minecraft.getWindow().handle());

        if (ClientStatus.failure != null) {
            minecraft.stop();
            return;
        }

        if (ran) {
            if (startedWorld && minecraft.level == null) {
                confirmDisposableWorld(minecraft);
                if (System.nanoTime() - worldStarted > java.util.concurrent.TimeUnit.SECONDS.toNanos(180)) {
                    ClientStatus.failure = new AssertionError("Disposable client world did not start; screen="
                            + (minecraft.gui.screen() == null ? "none" : minecraft.gui.screen().getClass().getName()));
                    org.apache.logging.log4j.LogManager.getLogger().error("Client runtime check failed", ClientStatus.failure);
                    minecraft.stop();
                    return;
                }
            }
            if (startedWorld && ClientStatus.complete && minecraft.level != null
                    && minecraft.getConnection() != null && minecraft.player != null) {
                if (!requestedRecipes) {
                    requestedRecipes = true;
                    var server = minecraft.getSingleplayerServer();
                    server.execute(() -> expectedRecipes = RecipePreviewModels.unlock(server));
                    return;
                }
                if (expectedRecipes == null || !RecipePreviewModels.ready(minecraft, expectedRecipes)) {
                    if (System.nanoTime() - worldStarted > java.util.concurrent.TimeUnit.SECONDS.toNanos(180)) {
                        throw new AssertionError("The client never received the qualified recipe displays");
                    }
                    return;
                }
                RecipePreviewModels.check(minecraft, expectedRecipes);
                RecipeBookGroupChecks.run(minecraft, expectedRecipes);
                SnowCoverClientChecks.run(minecraft);
                // Item components are bound when the world is ready, after the first menu bake.
                checkCreativeEntries();
                colours(minecraft);
                net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(),
                        true, minecraft.level.registryAccess());
                for (var entry : PiecesTab.entries().values()) {
                    long count = PiecesTab.INSTANCE.getDisplayItems().stream().filter(stack -> stack.is(entry.getItem())).count();
                    if (count != 1) throw new AssertionError("Missing or duplicate native creative entry: " + entry);
                }
                org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_CLIENT_WORLD_ACCEPTED");
                minecraft.stop();
                startedWorld = false;
            }

            return;
        }

        if (minecraft.gui.overlay() != null
                || !(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen)) {
            return;
        }
        ran = true;

        try {
            org.lwjgl.sdl.SDLVideo.SDL_HideWindow(minecraft.getWindow().handle());

            ConfigScreenChecks.run(minecraft);
            int checked = 0;

            for (PieceBlock block : Pieces.registeredBlocks()) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);

                    if (model == minecraft.getModelManager().getBlockStateModelSet().missingModel()) {
                        throw new AssertionError("Missing baked model " + state);
                    }
                    for (Direction face : new Direction[] { null, Direction.DOWN, Direction.UP, Direction.NORTH,
                            Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                        for (BakedQuad quad : quads(model, face)) {
                            if (quad.materialInfo().sprite().contents().name().toString().contains("missingno")) {
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
                        .getValue(net.minecraft.resources.Identifier.parse("skysbuildingpiecesbop:" + name));

                if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                    continue;
                }
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);

                    if (model == minecraft.getModelManager().getBlockStateModelSet().missingModel()) {
                        throw new AssertionError("Missing decoration model " + state);
                    }
                    for (BakedQuad quad : quads(model, null)) {
                        if (quad.materialInfo().sprite().contents().name().toString().contains("missingno")) {
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
                        .getValue(net.minecraft.resources.Identifier.parse(name));

                if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                    continue;
                }
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);

                    if (model == minecraft.getModelManager().getBlockStateModelSet().missingModel()) {
                        throw new AssertionError("Missing recovery model " + state);
                    }
                    for (Direction face : new Direction[] { null, Direction.DOWN, Direction.UP, Direction.NORTH,
                            Direction.SOUTH, Direction.WEST, Direction.EAST }) {
                        for (BakedQuad quad : quads(model, face)) {
                            if (quad.materialInfo().sprite().contents().name().toString().contains("missingno")) {
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
                    minecraft.createWorldOpenFlows().openWorld("building-pieces-world", () -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
                } else {
                    net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(
                            "Building Pieces Test", net.minecraft.world.level.GameType.CREATIVE,
                            net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                            net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                    minecraft.createWorldOpenFlows().createFreshLevel("building-pieces-world", settings,
                            new net.minecraft.world.level.levelgen.WorldOptions(4815162342L, false, false),
                            net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, minecraft.gui.screen());
                }
            }
        } catch (Throwable failure) {
            org.apache.logging.log4j.LogManager.getLogger().error("Runtime check failed", failure);
            minecraft.stop();
        }
    }

    private static void checkCreativeEntries() {
        var availableCreative = PiecesTab.entries();

        for (net.minecraft.world.item.Item retired : Pieces.RETIRED_TEMPLATES.values()) {
            if (availableCreative.containsKey(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(retired))) {
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

            if (hidden == availableCreative.containsKey(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item))) {
                throw new AssertionError("Incorrect creative visibility " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item));
            }
        }

        creative.addAll(availableCreative.values());

        java.util.Set<String> entries = new java.util.HashSet<String>();

        for (net.minecraft.world.item.ItemStack stack : creative) {
            String key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();

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
        if (entries.stream().anyMatch(key -> key.startsWith("minecraft:"))) {
            throw new AssertionError("Vanilla content appears in the Building Pieces tab");
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
    }

    private void confirmDisposableWorld(Minecraft minecraft) {
        if (confirmedWorld) return;
        boolean backup = minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen;
        boolean confirmation = minecraft.gui.screen() != null
                && minecraft.gui.screen().getClass() == net.minecraft.client.gui.screens.ConfirmScreen.class;
        if (!backup && !confirmation) {
            return;
        }
        java.nio.file.Path save = minecraft.gameDirectory.toPath().resolve("saves")
                .resolve("building-pieces-world").resolve("level.dat");
        if (!java.nio.file.Files.isRegularFile(save)) {
            throw new IllegalStateException("The load prompt has no saved probe world");
        }
        String experimentalTitle = net.minecraft.network.chat.Component
                .translatable("selectWorld.backupQuestion.experimental").getString();
        if (confirmation && !experimentalTitle.equals(minecraft.gui.screen().getTitle().getString())) {
            throw new IllegalStateException("Unexpected world-load confirmation: "
                    + minecraft.gui.screen().getTitle().getString());
        }
        String proceed = backup
                ? net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinConfirmButton").getString()
                : net.minecraft.network.chat.CommonComponents.GUI_PROCEED.getString();
        for (var child : minecraft.gui.screen().children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button
                    && proceed.equals(button.getMessage().getString())) {
                if (button.active) {
                    // Confirm the known load prompt for this generated fixture.
                    confirmedWorld = true;
                    button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
                    System.out.println("BUILDING_PIECES_CLIENT_LOAD_PROMPT_CONFIRMED owned_world=true");
                }
                return;
            }
        }
        throw new IllegalStateException("The expected world-load confirmation button is missing");
    }


    private static java.util.List<BakedQuad> quads(BlockStateModel model, Direction face) {
        var parts = new java.util.ArrayList<BlockStateModelPart>();
        model.collectParts(net.minecraft.util.RandomSource.create(0), parts);
        return parts.stream()
                .flatMap(part -> part.getQuads(face).stream()).toList();
    }

    private static java.util.List<BakedQuad> tintedQuads(BlockStateModel model) {
        return java.util.stream.Stream.concat(quads(model, null).stream(),
                java.util.Arrays.stream(Direction.values()).flatMap(face -> quads(model, face).stream()))
                .filter(quad -> quad.materialInfo().isTinted()).toList();
    }

    private static void colours(Minecraft client) {
        net.minecraft.core.BlockPos sample = client.player.blockPosition();
        var vanillaGrass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        int expected = client.getBlockColors().getTintSource(vanillaGrass, 0)
                .colorInWorld(vanillaGrass, client.level, sample);
        var models = client.getModelManager().getBlockStateModelSet();
        var vanillaLayers = new java.util.HashMap<net.minecraft.resources.Identifier,
                net.minecraft.client.renderer.chunk.ChunkSectionLayer>();
        for (BakedQuad quad : tintedQuads(models.get(vanillaGrass))) {
            vanillaLayers.put(quad.materialInfo().sprite().contents().name(), quad.materialInfo().layer());
        }
        if (vanillaLayers.size() < 2) {
            throw new AssertionError("Expected vanilla grass top and side overlay textures");
        }
        int inventory = net.minecraft.world.level.GrassColor.get(.5, 1);
        int checked = 0;

        for (PieceBlock block : Pieces.registeredBlocks()) {
            if (!block.palette.group.equals("grass")) {
                continue;
            }

            BlockState state = block.defaultBlockState();

            var model = client.getModelManager().getBlockStateModelSet().get(state);
            var colour = client.getBlockColors().getTintSource(state, 0);
            var shaded = tintedQuads(model);
            var layers = new java.util.HashMap<net.minecraft.resources.Identifier,
                    net.minecraft.client.renderer.chunk.ChunkSectionLayer>();
            for (BakedQuad quad : shaded) {
                layers.put(quad.materialInfo().sprite().contents().name(), quad.materialInfo().layer());
            }
            // Forge 64 chooses a layer per face. Match vanilla's solid top and
            // cutout overlay instead of requiring a single layer for the whole piece.
            if (colour == null || colour.colorInWorld(state, client.level, sample) != expected
                    || client.getBlockColors().getTintSource(state, 1) != null
                    || !vanillaLayers.equals(layers)) {
                throw new AssertionError("Grass-piece biome tint or render layer: "
                        + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block)
                        + ", expected layers " + vanillaLayers + ", actual layers " + layers
                        + ", tint " + (colour == null ? "unregistered" : colour.colorInWorld(state, client.level, sample))
                        + ", vanilla tint " + expected);
            }

            net.minecraft.world.item.ItemStack stack = Pieces.stack(state, 1);

            RecipePreviewModels.checkGrassTint(client, stack, inventory);

            checked++;
        }
        if (checked == 0) {
            throw new AssertionError("No grass pieces reached the colour checks");
        }

        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_COLOURS_PASS palettes=" + checked + " biome=true inventory=true");
    }
}
