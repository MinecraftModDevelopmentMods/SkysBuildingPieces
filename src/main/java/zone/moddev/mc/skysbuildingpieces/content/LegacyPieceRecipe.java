package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.Level;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyMapping;

/** Optional recovery for a legacy piece obtained while its original mod is installed. */
public final class LegacyPieceRecipe extends CustomRecipe {
    public static final SimpleCraftingRecipeSerializer<LegacyPieceRecipe> SERIALIZER = new SimpleCraftingRecipeSerializer<>(
            LegacyPieceRecipe::new);

    public LegacyPieceRecipe(CraftingBookCategory category) {
        super(category);
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
            String identity = ForgeRegistries.ITEMS.getKey(source.getItem()).toString();

            CustomData data = source.get(DataComponents.CUSTOM_DATA);
            if (!LegacyMapping.owned(identity) || data == null) {
                return ItemStack.EMPTY;
            }

            String material = LegacyMapping.material(data.copyTag().getString("material"));
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
        output.applyComponents(source.getComponentsPatch());

        return output;
    }

    @Override
    public boolean matches(CraftingContainer inventory, Level world) {
        return !result(inventory).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer inventory, HolderLookup.Provider registries) {
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
