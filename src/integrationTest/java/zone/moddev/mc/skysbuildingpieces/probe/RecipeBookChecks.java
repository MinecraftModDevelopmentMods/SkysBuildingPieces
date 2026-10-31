package zone.moddev.mc.skysbuildingpieces.probe;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.advancements.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.network.*;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

/** Checks the real server advancement manager and recipe book, not just the JSON files. */
public final class RecipeBookChecks {
    private static final Set<String> MODS = new HashSet<>(Arrays.asList("skysbuildingpieces", "skysbuildingpiecesbop",
            "skysgrassslabs", "skysterrainsmoother", "skysterrainsmootherbop"));

    public static void run(ServerWorld world) {
        SortedMap<String, JsonObject> entries = new TreeMap<>();
        List<IRecipe<?>> recipes = new ArrayList<>();

        for (IRecipe<?> recipe : world.getRecipeManager().getRecipes()) {
            ResourceLocation id = recipe.getId();

            if (id == null || !MODS.contains(id.getNamespace()) || recipe.isDynamic()) {
                continue;
            }

            Ingredient first = recipe.getIngredients().stream().filter(ingredient -> ingredient != Ingredient.EMPTY)
                    .findFirst().orElseThrow(() -> new AssertionError("No book ingredients: " + id));
            JsonObject entry = new JsonObject();

            entry.addProperty("recipe", id.toString());
            entry.addProperty("owner", owner(id));

            JsonArray ingredients = new JsonArray();
            Set<String> seen = new HashSet<>();

            for (ItemStack stack : acceptedStacks(first)) {
                if (stack.isEmpty()) {
                    continue;
                }

                JsonObject predicate = new JsonObject();

                predicate.addProperty("item", stack.getItem().getRegistryName().toString());

                if (seen.add(predicate.toString())) {
                    ingredients.add(predicate);
                }
            }

            require(ingredients.size() > 0, "Empty book ingredients: " + id);
            entry.add("ingredients", ingredients);
            entries.put(id.toString(), entry);
            recipes.add(recipe);
        }

        File snapshot = new File("recipe-book-snapshot.json");

        try (Writer out = new OutputStreamWriter(new FileOutputStream(snapshot), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(entries.values(), out);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        if (Boolean.getBoolean("skysbuildingpieces.snapshotRecipeBook")) {
            System.out.println("RECIPE_BOOK_SNAPSHOT_ONLY recipes=" + recipes.size());
            return;
        }

        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.fromString("7c27cbb1-2a4e-4e94-8726-964f72cbce17"), "RecipeBookProbe"),
                new PlayerInteractionManager(world));
        List<IPacket<?>> sentPackets = new ArrayList<>();
        NetworkManager network = new NetworkManager(PacketDirection.SERVERBOUND) {
            private final io.netty.channel.Channel testChannel = new io.netty.channel.embedded.EmbeddedChannel();

            @Override
            public io.netty.channel.Channel channel() {
                return testChannel;
            }

            @Override
            public void sendPacket(IPacket<?> packet) {
                sentPackets.add(packet);
            }

            @Override
            public void sendPacket(IPacket<?> packet,
                    io.netty.util.concurrent.GenericFutureListener<? extends io.netty.util.concurrent.Future<? super Void>> listener) {
                sentPackets.add(packet);
            }
        };

        player.connection = new net.minecraft.network.play.ServerPlayNetHandler(world.getServer(), network, player);
        require(!world.getServer().getAdvancementManager().getAllAdvancements().isEmpty(), "Advancements were loaded");

        File savedRecipes = new File(world.getSaveHandler().getWorldDirectory(), "recipe-book-probe.dat");

        if (savedRecipes.isFile()) {
            try (InputStream input = new FileInputStream(savedRecipes)) {
                player.getRecipeBook().read(net.minecraft.nbt.CompressedStreamTools.readCompressed(input));
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
            for (IRecipe<?> recipe : recipes) {
                if (Boolean.getBoolean("skysbuildingpieces.checkAllSkyRecipes")
                        || recipe.getId().getNamespace().equals("skysbuildingpieces")
                        || recipe.getId().getNamespace().equals("skysbuildingpiecesbop")) {
                    require(player.getRecipeBook().isUnlocked(recipe), "Saved recipe book lost " + recipe.getId());
                }
            }
        }

        int checks = 0;
        List<IRecipe<?>> verified = new ArrayList<>();

        for (IRecipe<?> recipe : recipes) {
            ResourceLocation id = recipe.getId();

            if (!Boolean.getBoolean("skysbuildingpieces.checkAllSkyRecipes")
                    && !id.getNamespace().equals("skysbuildingpieces")
                    && !id.getNamespace().equals("skysbuildingpiecesbop")) {
                continue;
            }

            verified.add(recipe);

            Advancement advancement = world.getServer().getAdvancementManager()
                    .getAdvancement(new ResourceLocation(owner(id), "recipes/" + id.getPath()));

            require(advancement != null, "Missing recipe advancement: " + id);
            require(!recipe.getRecipeOutput().isEmpty(), "Missing book result: " + id);
            reset(player, recipe, advancement);
            player.inventory.clear();
            player.inventory.setInventorySlotContents(0, new ItemStack(Items.APPLE));
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.inventory);
            require(!player.getRecipeBook().isUnlocked(recipe), "Unrelated item unlocked " + id);

            Ingredient first = recipe.getIngredients().stream().filter(ingredient -> ingredient != Ingredient.EMPTY)
                    .findFirst().get();
            ItemStack input = first.getMatchingStacks()[0].copy();

            player.inventory.setInventorySlotContents(0, input);
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.inventory);
            require(player.getRecipeBook().isUnlocked(recipe), "Ingredient did not unlock " + id);

            if (!id.getNamespace().equals("skysgrassslabs") || !id.getPath().equals("turf")) {
                reset(player, recipe, advancement);
                player.inventory.clear();
                MinecraftForge.EVENT_BUS
                        .post(new PlayerEvent.ItemCraftedEvent(player, input, RecombinationChecks.grid(2)));
                require(player.getRecipeBook().isUnlocked(recipe), "Crafted ingredient did not unlock " + id);
            }
            if (id.getPath().contains("recombine") || id.getPath().contains("slab_recombination")) {
                require(first.getMatchingStacks().length == 1,
                        "Recombination preview must show the normal item: " + id);

                for (ItemStack orientation : acceptedStacks(first)) {
                    require(first.test(orientation), "Packed ingredient does not match " + id);

                    net.minecraft.item.crafting.RecipeItemHelper autoFill = new net.minecraft.item.crafting.RecipeItemHelper();
                    ItemStack available = orientation.copy();

                    available.setCount(2);
                    autoFill.accountStack(available);
                    require(autoFill.canCraft(recipe, null), "Recipe book cannot fill saved orientation: " + id);
                    reset(player, recipe, advancement);
                    player.inventory.clear();
                    player.inventory.setInventorySlotContents(0, orientation.copy());
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.inventory);
                    require(player.getRecipeBook().isUnlocked(recipe), "Saved orientation did not unlock " + id);
                }
            }
            if (input.getItem() instanceof zone.moddev.mc.skysbuildingpieces.content.PieceItem) {
                zone.moddev.mc.skysbuildingpieces.content.PieceItem item = (zone.moddev.mc.skysbuildingpieces.content.PieceItem) input
                        .getItem();

                for (String material : item.piece.palette.materials) {
                    ItemStack other = zone.moddev.mc.skysbuildingpieces.content.Pieces
                            .stack(zone.moddev.mc.skysbuildingpieces.content.Pieces.state(material,
                                    item.piece.palette.shape, 0), 1);

                    if (first.test(other)) {
                        continue;
                    }

                    reset(player, recipe, advancement);
                    player.inventory.clear();
                    player.inventory.setInventorySlotContents(0, other);
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.inventory);
                    require(!player.getRecipeBook().isUnlocked(recipe), "Wrong palette material unlocked " + id);
                    player.inventory.setInventorySlotContents(0, input.copy());
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.inventory);
                    require(player.getRecipeBook().isUnlocked(recipe),
                            "Correct flat item failed after negative check " + id);
                    break;
                }
            }

            checks++;
        }

        player.getAdvancements().save();

        net.minecraft.nbt.CompoundNBT savedBook = player.getRecipeBook().write();
        net.minecraft.item.crafting.ServerRecipeBook reloaded = new net.minecraft.item.crafting.ServerRecipeBook(
                world.getRecipeManager());

        reloaded.read(savedBook);

        for (IRecipe<?> recipe : verified) {
            require(reloaded.isUnlocked(recipe), "Book reload lost " + recipe.getId());
        }

        require(sentPackets.stream()
                .anyMatch(packet -> packet instanceof net.minecraft.network.play.server.SRecipeBookPacket),
                "Unlocks did not send recipe-book packets");

        try (OutputStream output = new FileOutputStream(savedRecipes)) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(savedBook, output);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }

        world.getServer().reload();
        require(!world.getServer().getAdvancementManager().getAllAdvancements().isEmpty(), "Advancement reload failed");
        player.getAdvancements().reload();

        for (IRecipe<?> recipe : verified) {
            ResourceLocation id = recipe.getId();

            require(world.getServer().getAdvancementManager()
                    .getAdvancement(new ResourceLocation(owner(id), "recipes/" + id.getPath())) != null,
                    "Advancement reload lost " + id);
        }

        player.getAdvancements().dispose();
        network.channel().close();
        System.out.println("SKY_RECIPE_BOOK_PASS recipes=" + checks + " inventory=true crafting=true reload=true");
    }

    private static List<ItemStack> acceptedStacks(Ingredient ingredient) {
        List<ItemStack> stacks = new ArrayList<>();

        for (int packed : ingredient.getValidItemStacksPacked()) {
            ItemStack stack = net.minecraft.item.crafting.RecipeItemHelper.unpack(packed);

            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }

        return stacks;
    }

    private static void reset(ServerPlayerEntity player, IRecipe<?> recipe, Advancement advancement) {
        player.resetRecipes(Collections.singletonList(recipe));

        for (String criterion : advancement.getCriteria().keySet()) {
            player.getAdvancements().revokeCriterion(advancement, criterion);
        }
    }

    private static String owner(ResourceLocation recipe) {
        if (recipe.getNamespace().equals("skysbuildingpieces")) {
            String[] path = recipe.getPath().split("/");

            if (path.length >= 2) {
                Catalogue.MaterialDef material = Catalogue.INSTANCE.materials.get(path[0] + ":" + path[1]);

                if (material != null) {
                    return Catalogue.INSTANCE.modules.get(material.module).namespace;
                }
            }
        }

        return recipe.getNamespace();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private RecipeBookChecks() {
    }
}
