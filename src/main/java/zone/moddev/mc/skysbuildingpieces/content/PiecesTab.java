package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Shows the pieces registered by the mod and its add-ons. */
public final class PiecesTab extends CreativeTabs {
    public static final PiecesTab INSTANCE = new PiecesTab();

    private PiecesTab() {
        super("skysbuildingpieces");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ItemStack createIcon() {
        return new ItemStack(Blocks.BRICK_STAIRS);
    }

}
