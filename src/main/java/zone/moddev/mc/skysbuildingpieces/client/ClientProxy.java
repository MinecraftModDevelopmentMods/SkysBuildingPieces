package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GrassColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.PieceItem;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Client listeners stay on the mod bus that posts model and colour events. */
public final class ClientProxy {
    private ClientProxy() {
    }

    public static void register(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ClientProxy::blockColors);
        modBus.addListener(ClientProxy::itemColors);
        modBus.addListener(ClientProxy::renderLayers);
        modBus.addListener(new ContinuousGlassModel.BakeHandler()::bake);
        modBus.addListener(SnowCoveredGrassModel::bake);
    }

    private static void renderLayers(FMLClientSetupEvent event) {
        for (PieceBlock block : Pieces.registeredBlocks()) {
            RenderType layer = RenderType.solid();
            if (block.palette.group.equals("grass")) {
                layer = RenderType.cutoutMipped();
            } else if (block.palette.group.equals("clear_glass")) {
                layer = RenderType.cutout();
            } else if (block.palette.group.equals("stained_glass") || block.palette.group.equals("ice")) {
                layer = RenderType.translucent();
            }
            ItemBlockRenderTypes.setRenderLayer(block, layer);
        }
    }

    private static void blockColors(RegisterColorHandlersEvent.Block event) {
        for (PieceBlock block : Pieces.registeredBlocks()) {
            if (block.palette.group.equals("grass")) {
                event.getBlockColors().register(
                        (state, world, position,
                                tint) -> tint == 0 ? event.getBlockColors()
                                        .getColor(Blocks.GRASS_BLOCK.defaultBlockState(), world, position, 0) : -1,
                        block);
            }
        }
    }

    private static void itemColors(RegisterColorHandlersEvent.Item event) {
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item instanceof PieceItem && ((PieceItem) item).piece.palette.group.equals("grass")) {
                event.getItemColors().register((stack, tint) -> tint == 0 ? GrassColor.get(0.5D, 1.0D) : -1, item);
            }
        }
    }
}
