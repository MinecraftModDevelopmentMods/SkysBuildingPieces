package zone.moddev.mc.skysbuildingpieces;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class RecipeUnlockPredicateTest {
    @Test
    void nativeItemPredicatesRejectUnrelatedInventoryItems() throws Exception {
        MinecraftTestBootstrap.registerVanilla();
        ItemStack stone = vanillaStack(Items.STONE);
        ItemStack stick = vanillaStack(Items.STICK);
        Path directory = Path.of("src/main/resources/data/skysbuildingpieces/advancement/recipes");
        var ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        int checked = 0;
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) paths.filter(file -> file.toString().endsWith(".json"))::iterator) {
                JsonObject advancement = new JsonParser().parse(Files.readString(path)).getAsJsonObject();
                for (java.util.Map.Entry<String, JsonElement> entry : advancement.getAsJsonObject("criteria").entrySet()) {
                    JsonObject criterion = entry.getValue().getAsJsonObject();
                    if (!criterion.get("trigger").getAsString().equals("minecraft:inventory_changed")) {
                        continue;
                    }
                    for (JsonElement item : criterion.getAsJsonObject("conditions").getAsJsonArray("items")) {
                        JsonObject original = item.getAsJsonObject();
                        if (original.has("tag")) {
                            continue;
                        }
                        JsonObject predicate = new JsonParser().parse(original.toString()).getAsJsonObject();
                        // The unit registry contains vanilla items. Substitute only the identity,
                        // leaving the saved predicate schema for the native parser to interpret.
                        if (predicate.has("item")) {
                            predicate.addProperty("item", "minecraft:stone");
                        } else {
                            JsonArray identities = new JsonArray();
                            identities.add("minecraft:stone");
                            predicate.add("items", identities);
                        }
                        ItemPredicate nativePredicate = ItemPredicate.CODEC.parse(ops, predicate).getOrThrow();
                        assertTrue(nativePredicate.test(stone), path.toString());
                        assertFalse(nativePredicate.test(stick),
                                "An unrelated item must not unlock " + path);
                        assertTrue(original.has("items") && !original.has("item"), path.toString());
                        ++checked;
                    }
                }
            }
        }
        assertTrue(checked > 1400, "Check every ordinary recipe unlock");
    }

    private static ItemStack vanillaStack(Item item) throws ReflectiveOperationException {
        // A plain unit JVM has no Forge event bus. Use the item's vanilla
        // component defaults here; the runtime probe exercises the real bus.
        Field defaults = Item.class.getDeclaredField("components");
        Field gathered = Item.class.getDeclaredField("builtComponents");
        defaults.setAccessible(true);
        gathered.setAccessible(true);
        gathered.set(item, defaults.get(item));
        return new ItemStack(item, 64);
    }
}
