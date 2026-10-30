package zone.moddev.mc.skysbuildingpieces.content;

import com.google.gson.JsonObject;
import java.util.function.BooleanSupplier;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

/** Only load an optional recipe advancement when its recipe survived registration. */
public final class RecipeExistsCondition implements IConditionFactory {
    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        ResourceLocation recipe = new ResourceLocation(JsonUtils.getString(json, "recipe"));
        return () -> CraftingManager.getRecipe(recipe) != null;
    }
}
