package zone.moddev.mc.skysbuildingpieces.content;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.ICriterionInstance;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Unlock the next cut while its ingredient is still on the crafting-table cursor. */
@Mod.EventBusSubscriber(modid = "skysbuildingpieces")
public final class RecipeBookDiscovery {
    private static final Set<String> OWNERS = new HashSet<>(Arrays.asList(
            "skysbuildingpieces", "skysbuildingpiecesbop",
            "skysterrainsmoother", "skysterrainsmootherbop"));

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.crafting.isEmpty()) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        InventoryPlayer ingredient = new InventoryPlayer(player);
        ingredient.setInventorySlotContents(0, event.crafting.copy());

        for (IRecipe recipe : CraftingManager.REGISTRY) {
            ResourceLocation id = recipe.getRegistryName();
            if (id == null || !OWNERS.contains(id.getNamespace()) || recipe.isDynamic()) {
                continue;
            }
            if (recipe.getIngredients().stream().noneMatch(input -> input.apply(event.crafting))) {
                continue;
            }

            for (String owner : OWNERS) {
                Advancement advancement = player.getServer().getAdvancementManager()
                        .getAdvancement(new ResourceLocation(owner, "recipes/" + id.getPath()));
                if (advancement == null) {
                    continue;
                }

                Criterion recipeCriterion = advancement.getCriteria().get("has_the_recipe");
                if (recipeCriterion == null || !(recipeCriterion.getCriterionInstance()
                        instanceof RecipeUnlockedTrigger.Instance)) {
                    continue;
                }
                if (!((RecipeUnlockedTrigger.Instance) recipeCriterion.getCriterionInstance()).test(recipe)) {
                    continue;
                }

                // Evaluate the actual criterion, including metadata, rather than guessing its material.
                for (Map.Entry<String, Criterion> criterion : advancement.getCriteria().entrySet()) {
                    ICriterionInstance instance = criterion.getValue().getCriterionInstance();
                    if (instance instanceof InventoryChangeTrigger.Instance
                            && ((InventoryChangeTrigger.Instance) instance).test(ingredient)) {
                        player.getAdvancements().grantCriterion(advancement, criterion.getKey());
                    }
                }
            }
        }
    }

    private RecipeBookDiscovery() {
    }
}
