package zone.moddev.mc.skysbuildingpieces.content;

import java.util.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Keeps the pieces together, including the vanilla items reused by the catalogue. */
public final class PiecesTab extends CreativeTabs {
    public static final PiecesTab INSTANCE = new PiecesTab();

    private PiecesTab() {
        super("skysbuildingpieces");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Item getTabIconItem() {
        return Item.getItemFromBlock(Blocks.BRICK_STAIRS);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void displayAllRelevantItems(List<ItemStack> items) {
        super.displayAllRelevantItems(items);

        Set<String> present = new HashSet<String>();

        for (ItemStack stack : items) {
            present.add(key(stack));
        }
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            for (Shape shape : new Shape[] { Shape.SLAB, Shape.STAIRS, Shape.WALL, Shape.PANE }) {
                String nativeShape = shape.name().toLowerCase(Locale.ROOT);

                if (!Catalogue.supportsShape(material, shape) || material.block(nativeShape) == null) {
                    continue;
                }

                ItemStack stack = Pieces.stack(Pieces.nativeState(material, nativeShape), 1);

                if (stack != null && present.add(key(stack))) {
                    items.add(stack);
                }
            }
        }
    }

    private static String key(ItemStack stack) {
        return stack.getItem().getRegistryName() + ":" + stack.getMetadata();
    }
}
