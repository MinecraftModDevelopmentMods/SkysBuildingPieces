package zone.moddev.mc.skysbuildingpieces.content;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

/** Unlock the next cut even when a crafted piece is still on the table cursor. */
@EventBusSubscriber(
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

        for (RecipeHolder<?> holder : player.getServer().getRecipeManager().getRecipes()) {
            ResourceLocation id = holder.id();
            Recipe<?> recipe = holder.value();

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

            AdvancementHolder advancement = player.getServer().getAdvancements()
                    .get(new ResourceLocation(owner, "recipes/" + id.getPath()));

            if (advancement == null) {
                continue;
            }

            Criterion<?> recipeCriterion = advancement.value().criteria().get("has_the_recipe");

            if (recipeCriterion == null
                    || !(recipeCriterion.triggerInstance() instanceof RecipeUnlockedTrigger.TriggerInstance trigger)
                    || !trigger.matches(holder)) {
                continue;
            }
            for (Map.Entry<String, Criterion<?>> criterion : advancement.value().criteria().entrySet()) {
                CriterionTriggerInstance predicate = criterion.getValue().triggerInstance();

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
