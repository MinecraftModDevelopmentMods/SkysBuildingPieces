package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.GrassColor;
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
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Keeps model and colour code out of dedicated servers. */
public final class ClientProxy {
    private ClientProxy() {
    }

    public static void register(BusGroup modBus) {
        AddPackFindersEvent.BUS.addListener(ClientProxy::mineralogyResources);
        RegisterColorHandlersEvent.Block.BUS.addListener(ClientProxy::blockColors);
        ModelEvent.ModifyBakingResult.BUS.addListener(ContinuousGlassModel::bake);
        FMLClientSetupEvent.getBus(modBus).addListener(ClientProxy::renderLayers);
    }

    private static void mineralogyResources(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !ModList.get().isLoaded("mineralogy")) {
            return;
        }
        var path = ModList.get().getModFileById(SkysBuildingPieces.ID).getFile()
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

    private static void renderLayers(FMLClientSetupEvent event) {
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            ChunkSectionLayer layer = switch (block.palette.group) {
                case "grass", "clear_glass" -> ChunkSectionLayer.CUTOUT;
                case "stained_glass", "ice" -> ChunkSectionLayer.TRANSLUCENT;
                default -> ChunkSectionLayer.SOLID;
            };
            ItemBlockRenderTypes.setRenderLayer(block, layer);
        }
    }

    private static void blockColors(RegisterColorHandlersEvent.Block event) {
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.group.equals("grass")) {
                event.register((state, world, position, tint) -> {
                    if (tint != 0) {
                        return -1;
                    }
                    return world != null && position != null
                            ? BiomeColors.getAverageGrassColor(world, position)
                            : GrassColor.get(0.5D, 1.0D);
                }, block);
            }
        }
    }
}
