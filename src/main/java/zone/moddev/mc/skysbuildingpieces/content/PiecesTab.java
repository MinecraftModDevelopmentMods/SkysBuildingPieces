package zone.moddev.mc.skysbuildingpieces.content;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Keep new pieces and the native shapes they reuse together in one tab. */
@EventBusSubscriber(modid = SkysBuildingPieces.ID, bus = EventBusSubscriber.Bus.MOD)
public final class PiecesTab {
    public static CreativeModeTab INSTANCE;

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, registry -> {
            INSTANCE = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skysbuildingpieces"))
                    .icon(() -> new ItemStack(Blocks.BRICK_STAIRS))
                    .displayItems((parameters, output) -> entries().values().forEach(output::accept))
                    .build();
            registry.register(ResourceLocation.fromNamespaceAndPath(SkysBuildingPieces.ID, "pieces"), INSTANCE);
        });
    }

    public static Map<ResourceLocation, ItemStack> entries() {
        Map<ResourceLocation, ItemStack> entries = new LinkedHashMap<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof PieceItem && ((PieceItem) item).visibleInCreativeTab()) {
                entries.put(BuiltInRegistries.ITEM.getKey(item), new ItemStack(item));
            }
        }

        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            if (material.replacement() != null) {
                continue;
            }

            for (Shape shape : new Shape[] { Shape.SLAB, Shape.STAIRS, Shape.WALL, Shape.PANE }) {
                if (material.block(shape.name().toLowerCase(Locale.ROOT)) == null) {
                    continue;
                }

                ItemStack nativeItem = Pieces.stack(Pieces.state(material.id, shape, shape == Shape.SLAB ? 1 : 0), 1);
                if (nativeItem != null) {
                    entries.putIfAbsent(BuiltInRegistries.ITEM.getKey(nativeItem.getItem()), nativeItem);
                }
            }
        }
        return entries;
    }

    private PiecesTab() {
    }
}
