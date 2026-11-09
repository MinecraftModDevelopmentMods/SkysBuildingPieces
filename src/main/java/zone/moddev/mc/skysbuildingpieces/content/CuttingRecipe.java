package zone.moddev.mc.skysbuildingpieces.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/** Rotating a piece keeps its name and any other custom components. */
public final class CuttingRecipe extends ShapedRecipe {
    private static final MapCodec<CuttingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.common),
                    CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.book),
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.output),
                    Codec.BOOL.optionalFieldOf("preserve_tag", false).forGetter(recipe -> recipe.preserveComponents)
            ).apply(instance, CuttingRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, CuttingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    CommonInfo.STREAM_CODEC, recipe -> recipe.common,
                    CraftingBookInfo.STREAM_CODEC, recipe -> recipe.book,
                    ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
                    ItemStackTemplate.STREAM_CODEC, recipe -> recipe.output,
                    ByteBufCodecs.BOOL, recipe -> recipe.preserveComponents,
                    CuttingRecipe::new);

    // Vanilla's shaped recipe exposes a concrete serializer type. This serializer
    // still reads and writes only cutting recipes, using the native shaped layout.
    public static final RecipeSerializer<ShapedRecipe> SERIALIZER = new RecipeSerializer<>(
            CODEC.xmap(recipe -> recipe, CuttingRecipe.class::cast),
            STREAM_CODEC.map(recipe -> recipe, CuttingRecipe.class::cast));

    private final CommonInfo common;
    private final CraftingBookInfo book;
    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate output;
    private final boolean preserveComponents;

    private CuttingRecipe(CommonInfo common, CraftingBookInfo book, ShapedRecipePattern pattern,
            ItemStackTemplate output, boolean preserveComponents) {
        super(common, book, pattern, output);
        this.common = common;
        this.book = book;
        this.pattern = pattern;
        this.output = output;
        this.preserveComponents = preserveComponents;
    }

    @Override
    public ItemStack assemble(CraftingInput inventory) {
        ItemStack result = super.assemble(inventory);
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
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return SERIALIZER;
    }
}
