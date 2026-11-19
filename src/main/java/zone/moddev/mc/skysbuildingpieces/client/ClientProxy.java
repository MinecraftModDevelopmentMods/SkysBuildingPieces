package zone.moddev.mc.skysbuildingpieces.client;

import java.util.List;
import net.minecraft.client.color.block.BlockTintSources;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Keeps model and colour code out of dedicated servers. */
@Mod.EventBusSubscriber(modid = SkysBuildingPieces.ID, value = Dist.CLIENT)
public final class ClientProxy {
    private ClientProxy() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(BuildingPiecesConfigScreen::new));
        ModelEvent.ModifyBakingResult.BUS.addListener(ContinuousGlassModel::bake);
        ModelEvent.ModifyBakingResult.BUS.addListener(SnowCoveredGrassModel::bake);
    }

    @SubscribeEvent
    public static void mineralogyResources(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !ModList.isLoaded("mineralogy")) {
            return;
        }
        var path = ModList.getModFileById(SkysBuildingPieces.ID).getFile()
                .findResource("optional", "mineralogy");
        var location = new PackLocationInfo("skysbuildingpieces/mineralogy",
                Component.literal("Sky's Building Pieces: Mineralogy models"), PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(location, new PathPackResources.PathResourcesSupplier(path),
                PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
        if (pack == null) {
            throw new IllegalStateException("The Mineralogy model pack is missing or invalid");
        }
        event.addRepositorySource(accept -> accept.accept(pack));
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        for (PieceBlock block : Pieces.registeredBlocks()) {
            if (block.palette.group.equals("grass")) {
                event.register(List.of(BlockTintSources.grassBlock()), block);
            }
        }
    }
}
