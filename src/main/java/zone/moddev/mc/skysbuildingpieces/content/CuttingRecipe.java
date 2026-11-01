package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.JsonObject;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistryEntry;

/** A one-piece rotation keeps the original name and other custom stack data. */
public final class CuttingRecipe extends ShapedRecipe {
    public static final Serializer SERIALIZER = new Serializer();
    private final boolean preserveTag;

    private CuttingRecipe(ShapedRecipe recipe, boolean preserveTag) {
        super(recipe.getId(), recipe.getGroup(), recipe.getRecipeWidth(), recipe.getRecipeHeight(),
                recipe.getIngredients(), recipe.getResultItem());
        this.preserveTag = preserveTag;
    }

    @Override
    public ItemStack assemble(CraftingContainer inventory) {
        ItemStack result = super.assemble(inventory);

        if (preserveTag) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack input = inventory.getItem(slot);

                if (!input.isEmpty()) {
                    if (input.hasTag()) {
                        result.setTag(input.getTag().copy());
                    }

                    break;
                }
            }
        }

        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    public static final class Serializer extends ForgeRegistryEntry<RecipeSerializer<?>>
            implements RecipeSerializer<CuttingRecipe> {
        private final ShapedRecipe.Serializer shaped = new ShapedRecipe.Serializer();

        @Override
        public CuttingRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new CuttingRecipe(shaped.fromJson(id, json),
                    json.has("preserve_tag") && json.get("preserve_tag").getAsBoolean());
        }

        @Override
        public CuttingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            ShapedRecipe recipe = shaped.fromNetwork(id, buffer);

            return new CuttingRecipe(recipe, buffer.readBoolean());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CuttingRecipe recipe) {
            shaped.toNetwork(buffer, recipe);
            buffer.writeBoolean(recipe.preserveTag);
        }
    }
}
