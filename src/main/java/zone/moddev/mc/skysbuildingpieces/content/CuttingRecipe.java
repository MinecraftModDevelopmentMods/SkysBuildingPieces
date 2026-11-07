package zone.moddev.mc.skysbuildingpieces.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/** Rotating a piece keeps its name and any other custom components. */
public final class CuttingRecipe extends ShapedRecipe {
    public static final Serializer SERIALIZER = new Serializer();
    private final ShapedRecipePattern pattern;
    private final ItemStack output;
    private final boolean preserveComponents;

    private CuttingRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
            ItemStack output, boolean showNotification, boolean preserveComponents) {
        super(group, category, pattern, output, showNotification);
        this.pattern = pattern;
        this.output = output;
        this.preserveComponents = preserveComponents;
    }

    @Override
    public ItemStack assemble(CraftingInput inventory, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(inventory, registries);
        if (preserveComponents) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack input = inventory.getItem(slot);
                if (!input.isEmpty()) {
                    result.applyComponents(input.getComponentsPatch());
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

    public static final class Serializer implements RecipeSerializer<CuttingRecipe> {
        private static final MapCodec<CuttingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(CuttingRecipe::getGroup),
                        CraftingBookCategory.CODEC.optionalFieldOf("category", CraftingBookCategory.MISC)
                                .forGetter(CuttingRecipe::category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.output),
                        Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(CuttingRecipe::showNotification),
                        Codec.BOOL.optionalFieldOf("preserve_tag", false).forGetter(recipe -> recipe.preserveComponents)
                ).apply(instance, CuttingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, CuttingRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::encode, Serializer::decode);

        @Override
        public MapCodec<CuttingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CuttingRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static CuttingRecipe decode(RegistryFriendlyByteBuf buffer) {
            return new CuttingRecipe(buffer.readUtf(), CraftingBookCategory.STREAM_CODEC.decode(buffer),
                    ShapedRecipePattern.STREAM_CODEC.decode(buffer), ItemStack.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(), buffer.readBoolean());
        }

        private static void encode(RegistryFriendlyByteBuf buffer, CuttingRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            CraftingBookCategory.STREAM_CODEC.encode(buffer, recipe.category());
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.output);
            buffer.writeBoolean(recipe.showNotification());
            buffer.writeBoolean(recipe.preserveComponents);
        }
    }
}
