package zone.moddev.mc.skysbuildingpieces;

import net.minecraftforge.common.*;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

@Mod(
        modid = SkysBuildingPieces.ID,
        name = "Sky's Building Pieces",
        version = SkysBuildingPieces.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "after:skysgrassslabs@[1.1.0.112021,);after:basemetals;after:mineralogy;before:buildingbricks")
public final class SkysBuildingPieces {
    public static final String ID = "skysbuildingpieces", VERSION = "0.3.6.112021";
    public static boolean forceReplace;
    public static Block HOLDER;
    @SidedProxy(
            clientSide = "zone.moddev.mc.skysbuildingpieces.client.ClientProxy",
            serverSide = "zone.moddev.mc.skysbuildingpieces.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration configuration = new Configuration(event.getSuggestedConfigurationFile());

        configuration.load();
        forceReplace = configuration.getBoolean("forceReplaceBuildingBricksPieces", "compat", false,
                "Replace supported BuildingBricks pieces as they load. Back up the world first. Requires restart.");

        if (configuration.hasChanged()) {
            configuration.save();
        }

        Pieces.register();
        HOLDER = new Block(Material.ROCK).setBlockUnbreakable().setRegistryName(ID, "legacy_piece_holder");
        MinecraftForge.EVENT_BUS.register(new MigrationEvents());
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        zone.moddev.mc.skysbuildingpieces.content.GrassLifecycle.initialize();
        zone.moddev.mc.skysbuildingpieces.content.NativeSlabPlacement.initialize();
        LegacyBridge.ready = true;
        proxy.init();
    }

    @Mod.EventHandler
    public void beforeServer(FMLServerAboutToStartEvent event) {
        LegacyBridge.start(event.getServer());
    }

    @Mod.EventHandler
    public void afterServer(FMLServerStoppedEvent event) {
        LegacyBridge.stop();
    }
}
