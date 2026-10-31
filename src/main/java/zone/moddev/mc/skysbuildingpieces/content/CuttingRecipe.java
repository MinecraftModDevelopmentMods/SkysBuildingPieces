package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.JsonObject;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistryEntry;

/** A one-piece rotation keeps the original name and other custom stack data. */
public final class CuttingRecipe extends ShapedRecipe {
    public static final Serializer SERIALIZER = new Serializer();
    private final boolean preserveTag;

    private CuttingRecipe(ShapedRecipe recipe, boolean preserveTag) {
        super(recipe.getId(), recipe.getGroup(), recipe.getRecipeWidth(), recipe.getRecipeHeight(),
                recipe.getIngredients(), recipe.getRecipeOutput());
        this.preserveTag = preserveTag;
    }

    @Override
    public ItemStack getCraftingResult(CraftingInventory inventory) {
        ItemStack result = super.getCraftingResult(inventory);

        if (preserveTag) {
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                ItemStack input = inventory.getStackInSlot(slot);

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
    public IRecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    public static final class Serializer extends ForgeRegistryEntry<IRecipeSerializer<?>>
            implements IRecipeSerializer<CuttingRecipe> {
        private final ShapedRecipe.Serializer shaped = new ShapedRecipe.Serializer();

        @Override
        public CuttingRecipe read(ResourceLocation id, JsonObject json) {
            return new CuttingRecipe(shaped.read(id, json),
                    json.has("preserve_tag") && json.get("preserve_tag").getAsBoolean());
        }

        @Override
        public CuttingRecipe read(ResourceLocation id, PacketBuffer buffer) {
            ShapedRecipe recipe = shaped.read(id, buffer);

            return new CuttingRecipe(recipe, buffer.readBoolean());
        }

        @Override
        public void write(PacketBuffer buffer, CuttingRecipe recipe) {
            shaped.write(buffer, recipe);
            buffer.writeBoolean(recipe.preserveTag);
        }
    }
}
