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

@Mod(modid=SkysBuildingPieces.ID,name="Sky's Building Pieces",version=SkysBuildingPieces.VERSION,
    acceptedMinecraftVersions="[1.10.2]",dependencies="after:skysgrassslabs@[1.1.0.110021,);before:buildingbricks")
public final class SkysBuildingPieces {
    public static final String ID="skysbuildingpieces", VERSION="0.3.0.110021";
    public static boolean forceReplace;
    public static Block HOLDER;
    @SidedProxy(clientSide="zone.moddev.mc.skysbuildingpieces.client.ClientProxy",serverSide="zone.moddev.mc.skysbuildingpieces.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        Configuration c=new Configuration(event.getSuggestedConfigurationFile());c.load();
        forceReplace=c.getBoolean("forceReplaceBuildingBricksPieces","compat",false,
            "Replace supported BuildingBricks pieces as they load. Back up the world first. Requires restart.");
        if(c.hasChanged())c.save();
        Pieces.register();
        HOLDER=new Block(Material.ROCK).setBlockUnbreakable().setRegistryName(ID,"legacy_piece_holder");
        GameRegistry.register(HOLDER);GameRegistry.register(new ItemBlock(HOLDER).setRegistryName(HOLDER.getRegistryName()));
        MinecraftForge.EVENT_BUS.register(new MigrationEvents());proxy.preInit();
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) { zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.freeze();zone.moddev.mc.skysbuildingpieces.content.GrassLifecycle.initialize();zone.moddev.mc.skysbuildingpieces.content.CuttingRecipe.register();LegacyBridge.ready=true;proxy.init(); }
    @Mod.EventHandler public void beforeServer(FMLServerAboutToStartEvent event) { LegacyBridge.start(event.getServer()); }
    @Mod.EventHandler public void afterServer(FMLServerStoppedEvent event) { LegacyBridge.stop(); }
    @Mod.EventHandler public void mappings(FMLMissingMappingsEvent event) {
        for(FMLMissingMappingsEvent.MissingMapping m:event.getAll())if(LegacyMapping.owned(m.name.toString())) {
            // Forge 1.10 cannot map many generic IDs to one registered object.
            // IGNORE reserves the old numeric slot; the validated raw loader
            // hooks recover its material before Forge can deserialize it as air.
            // Unknown covered material/shape pairs fail closed in those hooks.
            m.ignore();
        }
    }
}
