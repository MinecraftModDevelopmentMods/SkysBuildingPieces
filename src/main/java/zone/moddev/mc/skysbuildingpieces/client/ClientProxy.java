package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.block.Blocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.item.Item;
import net.minecraft.world.GrassColors;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.PieceItem;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Client listeners stay on the mod bus that posts model and colour events. */
public final class ClientProxy {
    private ClientProxy() {
    }

    public static void register() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener(ClientProxy::blockColors);
        modBus.addListener(ClientProxy::itemColors);
        modBus.addListener(ClientProxy::renderLayers);
        modBus.addListener(new ContinuousGlassModel.BakeHandler()::bake);
    }

    private static void renderLayers(FMLClientSetupEvent event) {
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            RenderType layer = RenderType.solid();
            if (block.palette.group.equals("grass")) {
                layer = RenderType.cutoutMipped();
            } else if (block.palette.group.equals("clear_glass")) {
                layer = RenderType.cutout();
            } else if (block.palette.group.equals("stained_glass") || block.palette.group.equals("ice")) {
                layer = RenderType.translucent();
            }
            RenderTypeLookup.setRenderLayer(block, layer);
        }
    }

    private static void blockColors(ColorHandlerEvent.Block event) {
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.group.equals("grass")) {
                event.getBlockColors().register(
                        (state, world, position,
                                tint) -> tint == 0 ? event.getBlockColors()
                                        .getColor(Blocks.GRASS_BLOCK.defaultBlockState(), world, position, 0) : -1,
                        block);
            }
        }
    }

    private static void itemColors(ColorHandlerEvent.Item event) {
        for (Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof PieceItem && ((PieceItem) item).piece.palette.group.equals("grass")) {
                event.getItemColors().register((stack, tint) -> tint == 0 ? GrassColors.get(0.5D, 1.0D) : -1, item);
            }
        }
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.group.equals("grass")) {
                event.getItemColors().register((stack, tint) -> tint == 0 ? GrassColors.get(0.5D, 1.0D) : -1,
                        block.asItem());
            }
        }
    }
}
