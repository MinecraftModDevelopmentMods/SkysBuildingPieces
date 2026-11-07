package zone.moddev.mc.skysbuildingpieces;

import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
    public static final String VERSION = "0.3.9.121012";
    public static final Logger LOGGER = LogManager.getLogger();
    public static boolean forceReplace;
    public static Block HOLDER;

    public SkysBuildingPieces(IEventBus modBus, ModContainer container) {
        BuildingPiecesConfig.register(container);
        Pieces.register();
        MineralogyCompat.register();

        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(new MigrationEvents());
        NeoForge.EVENT_BUS.register(new PathPieces());
        NeoForge.EVENT_BUS.addListener(this::afterServer);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            zone.moddev.mc.skysbuildingpieces.client.ClientProxy.register(modBus, container);
        }
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
