package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.GrassColor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.fml.ModList;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Keeps model and colour code out of dedicated servers. */
public final class ClientProxy {
    private ClientProxy() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientProxy::mineralogyResources);
        modBus.addListener(ClientProxy::blockColors);
        modBus.addListener(ContinuousGlassModel::bake);
        modBus.addListener(ClientProxy::renderLayers);
    }

    private static void mineralogyResources(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !ModList.get().isLoaded("mineralogy")) {
            return;
        }
        event.addPackFinders(Identifier.fromNamespaceAndPath(SkysBuildingPieces.ID, "optional/mineralogy"),
                PackType.CLIENT_RESOURCES, Component.literal("Sky's Building Pieces: Mineralogy models"),
                PackSource.BUILT_IN, true, Pack.Position.BOTTOM);
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
