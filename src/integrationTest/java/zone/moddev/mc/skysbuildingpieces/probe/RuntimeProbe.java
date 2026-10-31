package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Build-only gameplay and migration checks, excluded from ordinary launches. */
@Mod("buildingpiecesprobe")
public final class RuntimeProbe {
    private MinecraftServer server;
    private boolean ran;

    public RuntimeProbe() {
        MinecraftForge.EVENT_BUS.register(this);
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> new ClientProbe().init());
    }

    @SubscribeEvent
    public void started(FMLServerStartedEvent event) {
        server = event.getServer();
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (server == null || ran || event.phase != TickEvent.Phase.END) {
            return;
        }

        ran = true;

        try {
            ServerWorld world = server.getWorld(DimensionType.OVERWORLD);
            String phase = System.getProperty("skysbuildingpieces.integrationPhase");

            if (phase == null) {
                throw new AssertionError("Unconfigured runtime probe");
            }

            CompoundNBT rules = world.getGameRules().write();

            rules.putString("randomTickSpeed", "0");
            rules.putString("doFireTick", "false");
            world.getGameRules().read(rules);

            if (phase.startsWith("sylvester")) {
                new WorldAuditProbe(world, phase.equals("sylvester-reload"));
                return;
            }
            if (phase.startsWith("retired-rocks-") || phase.startsWith("bop-legacy-")) {
                String helper = phase.startsWith("retired-rocks-") ? "RetiredRockUpgrade" : "BopLegacyFixture";

                Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe." + helper)
                        .getMethod("run", ServerWorld.class, String.class).invoke(null, world, phase);
            } else if (phase.equals("path-upgrade")) {
                PathChecks.savedPieces(world, true);
                System.out.println("BUILDING_PIECES_PATH_UPGRADE_PASS");
            } else if (phase.startsWith("recipe-book-")) {
                RecipeBookChecks.run(world);

                try (java.io.FileWriter writer = new java.io.FileWriter(
                        new File(world.getSaveHandler().getWorldDirectory(), phase + ".pass"))) {
                    writer.write("RECIPE_BOOK_PHASE_PASS " + phase + "\n");
                }

                System.out.println("RECIPE_BOOK_PHASE_PASS " + phase);
            } else if (phase.startsWith("legacy-")) {
                LegacyFixture.run(world, phase);
            } else {
                RecipeChecks.run(world);
                NativeLegacyChecks.run(world);
                GameplayChecks.run(world);
                WaterloggingChecks.run(world);
                PathChecks.run(world);
                SharedGrassChecks.run(world);
                MineralogyChecks.run();
                RecombinationChecks.run(world);
                RecipeBookChecks.run(world);
                PresentationChecks.run(world);

                if (phase.equals("compat")) {
                    CompatibilityChecks.run(world);
                }
                if (ModList.get().isLoaded("skysbuildingpiecesbop") && !phase.startsWith("client")) {
                    Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe.BopChecks")
                            .getMethod("run", ServerWorld.class).invoke(null, world);
                }
                if (phase.startsWith("client")) {
                    grid(world, phase.endsWith("reload"));
                    PresentationChecks.connectedGlass(world);
                    ClientStatus.complete = true;
                    return;
                }
            }

            File marker = new File(world.getSaveHandler().getWorldDirectory(),
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

            System.out.println("BUILDING_PIECES_PROBE_PASS " + phase);
            server.initiateShutdown(false);
        } catch (Throwable failure) {
            ClientStatus.failure = failure;
            failure.printStackTrace();
            server.initiateShutdown(false);
        }
    }

    private static void grid(ServerWorld world, boolean reload) throws Exception {
        File file = new File(world.getSaveHandler().getWorldDirectory(), "runtime-grid.dat");
        CompoundNBT saved = new CompoundNBT();

        if (reload) {
            try (FileInputStream input = new FileInputStream(file)) {
                saved = CompressedStreamTools.readCompressed(input);
            }
        }

        int number = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            int assigned = block.palette.materials.size() * block.palette.shape.states;

            for (int metadata = 0; metadata < assigned; metadata++) {
                BlockPos position = new BlockPos(500 + (number & 63), 180 + (number >> 6 & 15), 500 + (number >> 10));

                if (reload) {
                    if (!NBTUtil.writeBlockState(world.getBlockState(position))
                            .equals(saved.getCompound("" + number))) {
                        throw new AssertionError(
                                "Saved client grid lost " + block.getRegistryName() + " / " + metadata);
                    }
                } else {
                    world.setBlockState(position, block.getStateFromMeta(metadata), 18);
                }

                number++;
            }
        }
        if (!reload) {
            for (int index = 0; index < number; index++) {
                BlockPos position = new BlockPos(500 + (index & 63), 180 + (index >> 6 & 15), 500 + (index >> 10));
                BlockState actual = world.getBlockState(position);

                actual = ((PieceBlock) actual.getBlock()).getActualState(actual, world, position);
                world.setBlockState(position, actual, 18);
                saved.put("" + index, NBTUtil.writeBlockState(actual));
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                CompressedStreamTools.writeCompressed(saved, output);
            }
        }

        System.out.println("BUILDING_PIECES_CLIENT_WORLD_PASS reload=" + reload + " states=" + number);
    }
}
