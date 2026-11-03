package zone.moddev.mc.skysbuildingpieces.content;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

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
        if (!(event.getEntity() instanceof ServerPlayer) || event.getCrafting().isEmpty()) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        Inventory ingredient = new Inventory(player);

        ingredient.setItem(0, event.getCrafting().copy());

        for (Recipe<?> recipe : player.getServer().getRecipeManager().getRecipes()) {
            ResourceLocation id = recipe.getId();

            if (!OWNERS.contains(id.getNamespace()) || recipe.isSpecial()
                    || recipe.getIngredients().stream().noneMatch(input -> input.test(event.getCrafting()))) {
                continue;
            }

            String owner = id.getNamespace();

            if (owner.equals(SkysBuildingPieces.ID)) {
                Catalogue.Module module = Catalogue.INSTANCE.modules.get(id.getPath().split("/", 2)[0]);

                if (module != null) {
                    owner = module.namespace;
                }
            }

            Advancement advancement = player.getServer().getAdvancements()
                    .getAdvancement(new ResourceLocation(owner, "recipes/" + id.getPath()));

            if (advancement == null) {
                continue;
            }

            Criterion recipeCriterion = advancement.getCriteria().get("has_the_recipe");

            if (recipeCriterion == null
                    || !(recipeCriterion.getTrigger() instanceof RecipeUnlockedTrigger.TriggerInstance)
                    || !((RecipeUnlockedTrigger.TriggerInstance) recipeCriterion.getTrigger()).matches(recipe)) {
                continue;
            }
            for (Map.Entry<String, Criterion> criterion : advancement.getCriteria().entrySet()) {
                CriterionTriggerInstance predicate = criterion.getValue().getTrigger();

                if (predicate instanceof InventoryChangeTrigger.TriggerInstance
                        && ((InventoryChangeTrigger.TriggerInstance) predicate).matches(ingredient, event.getCrafting(),
                                event.getCrafting().getCount() >= event.getCrafting().getMaxStackSize() ? 1 : 0,
                                ingredient.getContainerSize() - 1, 1)) {
                    player.getAdvancements().award(advancement, criterion.getKey());
                }
            }
        }
    }
}
