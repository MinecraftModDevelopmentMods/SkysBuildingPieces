package zone.moddev.mc.skysbuildingpieces.content;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
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

/** Show the pieces supplied by this catalogue and its installed add-ons. */
@EventBusSubscriber(modid = SkysBuildingPieces.ID, bus = EventBusSubscriber.Bus.MOD)
public final class PiecesTab {
    public static CreativeModeTab INSTANCE;

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, registry -> {
            INSTANCE = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skysbuildingpieces"))
                    .icon(() -> new ItemStack(Pieces.pieceItem(SkysBuildingPieces.ID + ":gold_block_wall")))
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

        return entries;
    }

    private PiecesTab() {
    }
}
