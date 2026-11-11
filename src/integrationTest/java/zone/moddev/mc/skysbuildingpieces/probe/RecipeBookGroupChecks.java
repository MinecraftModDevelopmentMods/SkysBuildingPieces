package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.List;
import java.util.Set;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** Check the collections used by recipe-book buttons, not just individual recipe JSON. */
public final class RecipeBookGroupChecks {
    private static final Set<String> SKY = Set.of("skysgrassslabs", "skysbuildingpieces",
            "skysbuildingpiecesbop", "skysterrainsmoother", "skysterrainsmootherbop");

    public static void run(Minecraft client) {
        int checked = 0;
        for (var collection : client.player.getRecipeBook().getCollections()) {
            if (collection.getRecipes().stream().anyMatch(holder -> SKY.contains(holder.id().getNamespace()))) {
                require(collection.hasSingleResultItem(),
                        "A Sky recipe-book button must not cycle between different output items: "
                                + collection.getRecipes().stream().map(holder -> holder.id().toString()).toList());
                checked++;
            }
        }
        require(checked > 0, "Sky recipes reached the client book collections");

        var recipes = client.getConnection().getRecipeManager();
        var access = client.level.registryAccess();
        var slab = BuiltInRegistries.ITEM.get(ResourceLocation.parse("skysbuildingpieces:snow_slab"));
        CraftingInput diagonal = CraftingInput.of(2, 2, List.of(new ItemStack(Items.SNOW_BLOCK),
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.SNOW_BLOCK)));
        var matches = recipes.getRecipesFor(RecipeType.CRAFTING, diagonal, client.level);
        require(matches.size() == 1, "Two diagonal snow blocks have one crafting result");
        ItemStack result = matches.getFirst().value().assemble(diagonal, access);
        require(result.is(slab) && result.getCount() == 4, "Two snow blocks make four snow slabs");

        ClientRecipeBook book = new ClientRecipeBook();
        book.setupCollections(recipes.getRecipes(), access);
        for (var holder : recipes.getRecipes()) {
            if (holder.value().getResultItem(access).is(slab)) {
                book.add(holder);
            }
        }
        StackedContents inventory = new StackedContents();
        inventory.accountStack(new ItemStack(Items.SNOW_BLOCK, 2));
        var buttons = book.getCollections().stream().filter(collection -> collection.getRecipes().stream()
                .anyMatch(holder -> holder.value().getResultItem(access).is(slab))).toList();
        require(buttons.size() == 1, "Snow slabs have one stable result button");
        var button = buttons.getFirst();
        button.updateKnownRecipes(book);
        button.canCraft(inventory, 2, 2, book);
        require(button.hasSingleResultItem() && button.hasKnownRecipes() && button.hasCraftable()
                        && !button.getDisplayRecipes(true).isEmpty(),
                "The craftable-only 2x2 recipe book shows the snow slab recipe");
        org.apache.logging.log4j.LogManager.getLogger().info(
                "SKY_RECIPE_BOOK_GROUPS_PASS buttons=" + checked
                        + " distinct_results=true snow_slab_visible=true snow_slab_crafting=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private RecipeBookGroupChecks() {
    }
}
