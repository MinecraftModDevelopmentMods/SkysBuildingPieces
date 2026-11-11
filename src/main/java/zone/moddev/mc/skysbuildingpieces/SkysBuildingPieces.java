package zone.moddev.mc.skysbuildingpieces;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
import zone.moddev.mc.skysbuildingpieces.content.MissingMappingEvents;
import zone.moddev.mc.skysbuildingpieces.content.PiecesTab;
import zone.moddev.mc.skysbuildingpieces.content.RecipeBookDiscovery;
import zone.moddev.mc.skysbuildingpieces.content.RegistryEvents;
import net.minecraftforge.eventbus.api.listener.Priority;
import zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpieces.legacy.MigrationEvents;

@Mod(SkysBuildingPieces.ID)
public final class SkysBuildingPieces {
    public static final String ID = "skysbuildingpieces";
    public static final String VERSION = "0.3.10.2603001";
    public static final Logger LOGGER = LogManager.getLogger();
    public static boolean forceReplace;
    public static Block HOLDER;

    public SkysBuildingPieces(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        BuildingPiecesConfig.register(context);
        Pieces.register();
        MineralogyCompat.register();

        FMLCommonSetupEvent.getBus(modBus).addListener(this::setup);
        RegisterEvent.getBus(modBus).addListener(Priority.LOWEST, RegistryEvents::register);
        RegisterEvent.getBus(modBus).addListener(PiecesTab::register);
        MissingMappingsEvent.BUS.addListener(MissingMappingEvents::missing);
        PlayerEvent.ItemCraftedEvent.BUS.addListener(RecipeBookDiscovery::crafted);
        MigrationEvents.register();
        PathPieces.register();
        ServerStoppedEvent.BUS.addListener(this::afterServer);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            zone.moddev.mc.skysbuildingpieces.client.ClientProxy.register(context);
        }
    }

    private void setup(FMLCommonSetupEvent event) {
        // Install the legacy-world startup listener after parallel mod setup.
        event.enqueueWork(LegacyBridge::registerPersistence);
        forceReplace = BuildingPiecesConfig.forceReplacement();
        BuildingPiecesConfig.activate();
        Pieces.validateMaterials();
        GrassLifecycle.initialize();
        NativeSlabPlacement.initialize();
        LegacyBridge.ready = true;
    }

    private void afterServer(ServerStoppedEvent event) {
        LegacyBridge.stop();
    }
}
