package zone.moddev.mc.skysbuildingpieces.content;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.ICriterionInstance;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;

/** Unlock the next cut even when a crafted piece is still on the table cursor. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPieces.ID)
public final class RecipeBookDiscovery {
    private static final Set<String> OWNERS = new HashSet<>(Arrays.asList("skysbuildingpieces", "skysbuildingpiecesbop",
            "skysterrainsmoother", "skysterrainsmootherbop"));

    private RecipeBookDiscovery() {
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity) || event.getCrafting().isEmpty()) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        PlayerInventory ingredient = new PlayerInventory(player);

        ingredient.setInventorySlotContents(0, event.getCrafting().copy());

        for (IRecipe<?> recipe : player.getServer().getRecipeManager().getRecipes()) {
            ResourceLocation id = recipe.getId();

            if (!OWNERS.contains(id.getNamespace()) || recipe.isDynamic()
                    || recipe.getIngredients().stream().noneMatch(input -> input.test(event.getCrafting()))) {
                continue;
            }

            Advancement advancement = player.getServer().getAdvancementManager()
                    .getAdvancement(new ResourceLocation(id.getNamespace(), "recipes/" + id.getPath()));

            if (advancement == null) {
                continue;
            }

            Criterion recipeCriterion = advancement.getCriteria().get("has_the_recipe");

            if (recipeCriterion == null
                    || !(recipeCriterion.getCriterionInstance() instanceof RecipeUnlockedTrigger.Instance)
                    || !((RecipeUnlockedTrigger.Instance) recipeCriterion.getCriterionInstance()).test(recipe)) {
                continue;
            }
            for (Map.Entry<String, Criterion> criterion : advancement.getCriteria().entrySet()) {
                ICriterionInstance predicate = criterion.getValue().getCriterionInstance();

                if (predicate instanceof InventoryChangeTrigger.Instance
                        && ((InventoryChangeTrigger.Instance) predicate).test(ingredient)) {
                    player.getAdvancements().grantCriterion(advancement, criterion.getKey());
                }
            }
        }
    }
}
