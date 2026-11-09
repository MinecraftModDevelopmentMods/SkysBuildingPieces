package zone.moddev.mc.skysbuildingpieces.content;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;

/** Show pieces supplied by this catalogue and its installed add-ons. */
public final class PiecesTab {
    public static CreativeModeTab INSTANCE;

    public static void register(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, registry -> {
            INSTANCE = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skysbuildingpieces"))
                    .icon(() -> new ItemStack(Pieces.pieceItem(SkysBuildingPieces.ID + ":gold_block_wall")))
                    .displayItems((parameters, output) -> entries().values().forEach(output::accept))
                    .build();
            registry.register(Identifier.fromNamespaceAndPath(SkysBuildingPieces.ID, "pieces"), INSTANCE);
        });
    }

    public static Map<Identifier, ItemStack> entries() {
        Map<Identifier, ItemStack> entries = new LinkedHashMap<>();

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof PieceItem && ((PieceItem) item).visibleInCreativeTab()) {
                entries.put(ForgeRegistries.ITEMS.getKey(item), new ItemStack(item));
            }
        }

        return entries;
    }

    private PiecesTab() {
    }
}
