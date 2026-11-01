package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;

/** Optional recovery for a legacy piece obtained while its original mod is installed. */
public final class LegacyPieceRecipe extends CustomRecipe {
    public static final SimpleRecipeSerializer<LegacyPieceRecipe> SERIALIZER = new SimpleRecipeSerializer<>(
            LegacyPieceRecipe::new);

    public LegacyPieceRecipe(ResourceLocation id) {
        super(id);
    }

    private ItemStack result(CraftingContainer inventory) {
        ItemStack source = ItemStack.EMPTY;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);

            if (!stack.isEmpty()) {
                if (!source.isEmpty()) {
                    return ItemStack.EMPTY;
                }

                source = stack;
            }
        }
        if (source.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack output;

        if (Pieces.RETIRED_TEMPLATES.containsValue(source.getItem())) {
            output = new ItemStack(Items.PAPER);
        } else {
            String identity = source.getItem().getRegistryName().toString();

            if (!LegacyMapping.owned(identity) || !source.hasTag()) {
                return ItemStack.EMPTY;
            }

            String material = LegacyMapping.material(source.getTag().getString("material"));
            Shape shape = LegacyMapping.shape(identity, 0);

            if (shape == null || !Catalogue.INSTANCE.materials.containsKey(material)
                    || !LegacyMapping.allowedMaterial(material, shape)) {
                return ItemStack.EMPTY;
            }

            output = Pieces.stack(Pieces.state(material, shape, shape == Shape.SLAB ? 1 : 0), 1);

            if (shape == Shape.VERTICAL_SLAB && output != null) {
                output = Pieces.slabDrop(material, Pieces.state(material, shape, 0));
            }
        }
        if (output == null) {
            return ItemStack.EMPTY;
        }
        if (source.hasTag()) {
            output.setTag(source.getTag().copy());
        }

        return output;
    }

    @Override
    public boolean matches(CraftingContainer inventory, Level world) {
        return !result(inventory).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer inventory) {
        return result(inventory);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 1 && height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inventory) {
        return NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
    }
}
