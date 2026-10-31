package zone.moddev.mc.skysbuildingpieces.content;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Keep new pieces and the native shapes they reuse together in one tab. */
public final class PiecesTab extends ItemGroup {
    public static final PiecesTab INSTANCE = new PiecesTab();

    private PiecesTab() {
        super("skysbuildingpieces");
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public ItemStack createIcon() {
        return new ItemStack(Blocks.BRICK_STAIRS);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void fill(NonNullList<ItemStack> items) {
        super.fill(items);

        Set<String> present = new HashSet<>();

        for (ItemStack stack : items) {
            present.add(stack.getItem().getRegistryName().toString());
        }
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            for (Shape shape : new Shape[] { Shape.SLAB, Shape.STAIRS, Shape.WALL, Shape.PANE }) {
                if (material.block(shape.name().toLowerCase(Locale.ROOT)) == null) {
                    continue;
                }

                ItemStack nativeItem = Pieces.stack(Pieces.state(material.id, shape, shape == Shape.SLAB ? 1 : 0), 1);

                if (nativeItem != null && present.add(nativeItem.getItem().getRegistryName().toString())) {
                    items.add(nativeItem);
                }
            }
        }
    }
}
