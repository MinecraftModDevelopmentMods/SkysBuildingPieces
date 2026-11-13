package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.item.ItemStack;

/** Keep different materials, shapes and yields in separate recipe-book buttons. */
final class RecipeGroups {
    private RecipeGroups() {
    }

    static String result(ItemStack output) {
        return "skysbuildingpieces/result/" + output.getItem().getRegistryName()
                + "/" + output.getMetadata() + "/" + output.getCount();
    }
}
