package zone.moddev.mc.skysbuildingpieces;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.moddev.mc.skysbuildingpieces.config.BuildingPiecesConfig;
import zone.moddev.mc.skysbuildingpieces.content.GrassLifecycle;
import zone.moddev.mc.skysbuildingpieces.content.NativeSlabPlacement;
import zone.moddev.mc.skysbuildingpieces.content.PathPieces;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

@Mod(SkysBuildingPieces.ID)
public final class SkysBuildingPieces {
    public static final String ID = "skysbuildingpieces";
    public static final String VERSION = "0.3.8.120011";
    public static final Logger LOGGER = LogManager.getLogger();
    public static boolean forceReplace;
    public static Block HOLDER;

    public SkysBuildingPieces() {
        BuildingPiecesConfig.register();
        Pieces.register();
        MineralogyCompat.register();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(new MigrationEvents());
        MinecraftForge.EVENT_BUS.register(new PathPieces());
        MinecraftForge.EVENT_BUS.addListener(this::afterServer);
        DistExecutor.runWhenOn(Dist.CLIENT, () -> zone.moddev.mc.skysbuildingpieces.client.ClientProxy::register);
    }

    private void setup(FMLCommonSetupEvent event) {
        // Install the legacy-world startup listener after parallel mod setup.
        event.enqueueWork(LegacyBridge::registerPersistence);
        forceReplace = BuildingPiecesConfig.forceReplacement();
        Pieces.validateMaterials();
        GrassLifecycle.initialize();
        NativeSlabPlacement.initialize();
        LegacyBridge.ready = true;
    }

    private void afterServer(ServerStoppedEvent event) {
        LegacyBridge.stop();
    }
}
