package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/** Check the actual buttons shown after the server has sent the Sky recipes. */
public final class RecipeBookGroupChecks {
    public static void run(Minecraft client, Set<RecipeDisplayId> expected) {
        var context = SlotDisplayContext.fromLevel(client.level);
        int buttons = 0;
        boolean snowSlab = false;
        for (var collection : client.player.getRecipeBook().getCollections()) {
            if (collection.getRecipes().stream().noneMatch(entry -> expected.contains(entry.id()))) {
                continue;
            }
            var outputs = collection.getRecipes().stream().flatMap(entry ->
                    entry.display().result().resolveForStacks(context).stream())
                    .map(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    .collect(Collectors.toSet());
            if (outputs.size() != 1) {
                throw new AssertionError("A recipe-book button cycles between different output items: " + outputs);
            }
            snowSlab |= outputs.iterator().next().toString().equals("skysbuildingpieces:snow_slab");
            buttons++;
        }
        if (buttons == 0 || !snowSlab) {
            throw new AssertionError("Sky recipes or the snow slab recipe are missing from the book");
        }
        org.apache.logging.log4j.LogManager.getLogger().info(
                "SKY_RECIPE_BOOK_GROUPS_PASS buttons={} distinct_results=true snow_slab_visible=true", buttons);
    }

    private RecipeBookGroupChecks() {
    }
}
