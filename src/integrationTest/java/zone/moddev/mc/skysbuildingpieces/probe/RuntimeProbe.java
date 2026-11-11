package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Build-only gameplay and migration checks, excluded from ordinary launches. */
@Mod("buildingpiecesprobe")
public final class RuntimeProbe {
    private MinecraftServer server;
    private boolean ran;
    private int startupTicks;
    private boolean needsGameplay;

    public RuntimeProbe(net.neoforged.bus.api.IEventBus modBus) {
        CompatibilityChecks.registerHandler(modBus);
        PathChecks.register(modBus,
                "buildingpiecesprobe");
        PersistenceRegistrationChecks.register(
                modBus);
        NeoForge.EVENT_BUS.register(this);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            new ClientProbe().init();
        }
    }

    @SubscribeEvent
    public void started(ServerStartedEvent event) {
        server = event.getServer();
        String phase = System.getProperty("skysbuildingpieces.integrationPhase", "");
        needsGameplay = !(phase.startsWith("sylvester") || phase.startsWith("retired-rocks-")
                || phase.startsWith("bop-legacy-") || phase.equals("path-upgrade")
                || phase.startsWith("recipe-book-") || phase.startsWith("legacy-") || phase.startsWith("fuel-"));
        if (needsGameplay) {
            GameplayChecks.prepare(server.getLevel(Level.OVERWORLD));
        }
    }

    @SubscribeEvent
    public void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (server == null || ran) {
            return;
        }

        if (needsGameplay && (++startupTicks < 20 || !GameplayChecks.ready(server.getLevel(Level.OVERWORLD)))) {
            if (startupTicks > 1200) {
                org.apache.logging.log4j.LogManager.getLogger().error("The gameplay fixture never finished loading");
                ran = true;
                server.halt(false);
            }
            return;
        }

        ran = true;

        try {
            ServerLevel world = server.getLevel(Level.OVERWORLD);
            String phase = System.getProperty("skysbuildingpieces.integrationPhase");

            if (phase == null) {
                throw new AssertionError("Unconfigured runtime probe");
            }

            // Use the same server commands as an operator; the bulk-copy helper is client-only.
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "gamerule randomTickSpeed 0");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "gamerule doFireTick false");
            if (world.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING) != 0
                    || world.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)) {
                throw new AssertionError("The disposable runtime rules were not applied");
            }

            if (phase.startsWith("sylvester")) {
                new WorldAuditProbe(world, phase.equals("sylvester-reload"));
                return;
            }
            if (phase.startsWith("retired-rocks-") || phase.startsWith("bop-legacy-")) {
                String helper = phase.startsWith("retired-rocks-") ? "RetiredRockUpgrade" : "BopLegacyFixture";

                Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe." + helper)
                        .getMethod("run", ServerLevel.class, String.class).invoke(null, world, phase);
            } else if (phase.equals("path-upgrade")) {
                PathChecks.savedPieces(world, true);
                org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_PATH_UPGRADE_PASS");
            } else if (phase.startsWith("recipe-book-")) {
                RecipeBookChecks.run(world);

                try (java.io.FileWriter writer = new java.io.FileWriter(
                        new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), phase + ".pass"))) {
                    writer.write("RECIPE_BOOK_PHASE_PASS " + phase + "\n");
                }

                org.apache.logging.log4j.LogManager.getLogger().info("RECIPE_BOOK_PHASE_PASS " + phase);
            } else if (phase.startsWith("legacy-")) {
                LegacyFixture.run(world, phase);
            } else if (phase.startsWith("fuel-")) {
                FuelChecks.run();
            } else if (phase.equals("compat")) {
                NativeLegacyChecks.run(world);
                CompatibilityChecks.run(world);
            } else {
                RecipeChecks.run(world);
                NativeLegacyChecks.run(world);
                StandaloneLifecycleChecks.run(world);
                SurvivalDropChecks.run(world);
                FuelChecks.run();
                GameplayChecks.run(world);
                WaterloggingChecks.run(world);
                PathChecks.run(world);
                SharedGrassChecks.run(world);
                MineralogyChecks.run();
                RecombinationChecks.run(world);
                RecipeBookChecks.run(world);
                PresentationChecks.run(world);

                if (ModList.get().isLoaded("skysbuildingpiecesbop") && !phase.startsWith("client")) {
                    Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe.BopChecks")
                            .getMethod("run", ServerLevel.class).invoke(null, world);
                }
                if (phase.startsWith("client")) {
                    grid(world, phase.endsWith("reload"));
                    PresentationChecks.connectedGlass(world);
                    ClientStatus.complete = true;
                    return;
                }
            }

            File marker = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                    "skysbuildingpieces-integration.properties");
            Properties result = new Properties();

            if (marker.isFile()) {
                try (FileInputStream input = new FileInputStream(marker)) {
                    result.load(input);
                }
            }

            result.setProperty(phase + "_complete", "true");
            result.setProperty("gameplay_checks", "placement, recipes, drops, paths, grass and walls");
            result.setProperty("migration_checks",
                    "source registry, all 16 step codes, raw inventories and native data fixing");

            try (FileOutputStream output = new FileOutputStream(marker)) {
                result.store(output, "Disposable runtime checks");
            }

            org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_PROBE_PASS " + phase);
            server.halt(false);
        } catch (Throwable failure) {
            ClientStatus.failure = failure;
            org.apache.logging.log4j.LogManager.getLogger().error("Runtime check failed", failure);
            server.halt(false);
        } finally {
            if (needsGameplay) {
                GameplayChecks.release(server.getLevel(Level.OVERWORLD));
            }
        }
    }

    private static void grid(ServerLevel world, boolean reload) throws Exception {
        File file = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "runtime-grid.dat");
        CompoundTag saved = new CompoundTag();

        if (reload) {
            try (FileInputStream input = new FileInputStream(file)) {
                saved = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
        }

        int number = 0;

        for (PieceBlock block : Pieces.playableBlocks()) {
            int assigned = block.palette.materials.size() * block.palette.shape.states;

            for (int metadata = 0; metadata < assigned; metadata++) {
                BlockPos position = new BlockPos(500 + (number & 63), 180 + (number >> 6 & 15), 500 + (number >> 10));

                if (reload) {
                    if (!NbtUtils.writeBlockState(world.getBlockState(position))
                            .equals(saved.getCompound("" + number))) {
                        throw new AssertionError(
                                "Saved client grid lost " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block) + " / " + metadata);
                    }
                } else {
                    world.setBlock(position, block.getStateFromMeta(metadata), 18);
                }

                number++;
            }
        }
        if (!reload) {
            for (int index = 0; index < number; index++) {
                BlockPos position = new BlockPos(500 + (index & 63), 180 + (index >> 6 & 15), 500 + (index >> 10));
                BlockState actual = world.getBlockState(position);

                actual = ((PieceBlock) actual.getBlock()).getActualState(actual, world, position);
                world.setBlock(position, actual, 18);
                saved.put("" + index, NbtUtils.writeBlockState(actual));
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                NbtIo.writeCompressed(saved, output);
            }
        }

        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_CLIENT_WORLD_PASS reload=" + reload + " states=" + number);
    }
}
