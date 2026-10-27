package zone.moddev.mc.skysbuildingpieces;

import net.minecraftforge.common.*;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

@Mod(
        modid = SkysBuildingPieces.ID,
        name = "Sky's Building Pieces",
        version = SkysBuildingPieces.VERSION,
        acceptedMinecraftVersions = "[1.10.2]",
        dependencies = "after:skysgrassslabs@[1.1.0.110021,);after:mineralogy;before:buildingbricks")
public final class SkysBuildingPieces {
    public static final String ID = "skysbuildingpieces", VERSION = "0.3.7.110021";
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
        zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.register();
        if (zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.modules.containsKey("mineralogy")) {
            event.getModLog().info("Mineralogy building pieces enabled: 27 rocks in 35 fixed palettes.");
        }
        HOLDER = new Block(Material.ROCK).setBlockUnbreakable().setRegistryName(ID, "legacy_piece_holder");
        GameRegistry.register(HOLDER);
        GameRegistry.register(new ItemBlock(HOLDER).setRegistryName(HOLDER.getRegistryName()));
        MinecraftForge.EVENT_BUS.register(new MigrationEvents());
        MinecraftForge.EVENT_BUS.register(new zone.moddev.mc.skysbuildingpieces.content.PathPieces());
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.freeze();
        zone.moddev.mc.skysbuildingpieces.content.GrassLifecycle.initialize();
        zone.moddev.mc.skysbuildingpieces.content.CuttingRecipe.register();
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

    @Mod.EventHandler
    public void mappings(FMLMissingMappingsEvent event) {
        for (FMLMissingMappingsEvent.MissingMapping mapping : event.getAll()) {
            Block formerPiece = zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat
                    .formerBlock(mapping.name.toString());
            if (formerPiece != null) {
                if (mapping.type == GameRegistry.Type.BLOCK) {
                    mapping.remap(formerPiece);
                } else if (mapping.type == GameRegistry.Type.ITEM) {
                    mapping.remap(net.minecraft.item.Item.getItemFromBlock(formerPiece));
                }
                continue;
            }
            if (LegacyMapping.owned(mapping.name.toString())) {
                // Forge 1.10 cannot map many generic IDs to one registered object. IGNORE reserves the old numeric
                // slot; the validated raw loader hooks recover its material before Forge can deserialize it as air.
                // Those hooks stop loading if a piece has no safe replacement.
                mapping.ignore();
            }
        }
    }
}
