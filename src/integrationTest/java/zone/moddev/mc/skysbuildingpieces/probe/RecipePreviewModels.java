package zone.moddev.mc.skysbuildingpieces.probe;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/** Inspects the actual ingredient previews sent to the disposable client. */
final class RecipePreviewModels {
    private static final Set<String> OWNERS = Set.of("skysbuildingpieces", "skysbuildingpiecesbop",
            "skysgrassslabs", "skysterrainsmoother", "skysterrainsmootherbop");

    static Set<RecipeDisplayId> unlock(MinecraftServer server) {
        var recipes = server.getRecipeManager().getRecipes().stream().filter(holder ->
                OWNERS.contains(holder.id().identifier().getNamespace()) && !holder.value().isSpecial()).toList();
        Set<RecipeDisplayId> expected = new HashSet<>();
        for (var recipe : recipes) {
            server.getRecipeManager().listDisplaysForRecipe(recipe.id(), entry -> expected.add(entry.id()));
        }
        if (expected.size() < 1000 || server.getPlayerList().getPlayers().size() != 1) {
            throw new AssertionError("The disposable client is missing its recipe displays or player");
        }
        server.getPlayerList().getPlayers().getFirst().awardRecipes(recipes);
        return Set.copyOf(expected);
    }

    static boolean ready(Minecraft minecraft, Set<RecipeDisplayId> expected) {
        return entries(minecraft).stream().map(RecipeDisplayEntry::id).collect(java.util.stream.Collectors.toSet())
                .containsAll(expected);
    }

    static void check(Minecraft minecraft, Set<RecipeDisplayId> expected) {
        var context = SlotDisplayContext.fromLevel(minecraft.level);
        int recipes = 0;
        int previews = 0;
        for (RecipeDisplayEntry entry : entries(minecraft)) {
            if (!expected.contains(entry.id())) {
                continue;
            }
            var display = entry.display();
            var slots = switch (display) {
                case ShapedCraftingRecipeDisplay shaped -> shaped.ingredients();
                case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients();
                default -> throw new AssertionError("Unexpected piece recipe display: " + display);
            };
            for (var slot : slots) {
                for (ItemStack preview : slot.resolveForStacks(context)) {
                    checkModel(minecraft, preview);
                    previews++;
                }
            }
            ItemStack result = display.result().resolveForFirstStack(context);
            checkModel(minecraft, result);
            previews++;
            recipes++;
        }
        if (recipes != expected.size()) {
            throw new AssertionError("Client received " + recipes + " recipe displays; expected " + expected.size());
        }
        org.apache.logging.log4j.LogManager.getLogger().info(
                "SKY_RECIPE_PREVIEW_MODELS_PASS recipes={} previews={}", recipes, previews);
    }

    private static List<RecipeDisplayEntry> entries(Minecraft minecraft) {
        return minecraft.player.getRecipeBook().getCollections().stream()
                .flatMap(collection -> collection.getRecipes().stream()).distinct().toList();
    }

    private static ItemStackRenderState model(Minecraft minecraft, ItemStack stack) {
        if (stack.isEmpty()) {
            throw new AssertionError("An ingredient or result preview is empty");
        }
        ItemStackRenderState state = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI,
                minecraft.level, minecraft.player, 0);
        if (state.isEmpty()) {
            throw new AssertionError("Missing recipe preview model: " + stack);
        }
        return state;
    }

    private static void checkModel(Minecraft minecraft, ItemStack stack) {
        var state = model(minecraft, stack);
        var particle = state.pickParticleIcon(RandomSource.create(0));
        if (particle == null || particle.contents().name().toString().contains("missingno")) {
            throw new AssertionError("Missing recipe preview texture: " + stack);
        }
        try {
            for (var layer : layers(state)) {
                @SuppressWarnings("unchecked")
                List<BakedQuad> quads = (List<BakedQuad>) field(layer.getClass(), "quads").get(layer);
                for (BakedQuad quad : quads) {
                    if (quad.sprite().contents().name().toString().contains("missingno")) {
                        throw new AssertionError("Missing recipe face texture: " + stack);
                    }
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect rendered item quads", failure);
        }
    }

    static void checkGrassTint(Minecraft minecraft, ItemStack stack, int expected) {
        var state = model(minecraft, stack);
        try {
            var layer = layers(state).getFirst();
            int[] tints = (int[]) field(layer.getClass(), "tintLayers").get(layer);
            if (tints.length == 0 || (tints[0] & 0xFFFFFF) != (expected & 0xFFFFFF)
                    || tints.length > 1 && tints[1] != -1) {
                throw new AssertionError("Grass-piece inventory tint: " + stack);
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect the baked grass tint", failure);
        }
    }

    private static List<ItemStackRenderState.LayerRenderState> layers(ItemStackRenderState state)
            throws ReflectiveOperationException {
        var type = ItemStackRenderState.class;
        var all = (ItemStackRenderState.LayerRenderState[]) field(type, "layers").get(state);
        int count = field(type, "activeLayerCount").getInt(state);
        return java.util.Arrays.asList(all).subList(0, count);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private RecipePreviewModels() {
    }
}
