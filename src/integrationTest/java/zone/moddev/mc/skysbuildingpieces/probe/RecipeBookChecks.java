package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.LevelResource;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.advancements.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

/** Checks the real server advancement manager and recipe book, not just the JSON files. */
public final class RecipeBookChecks {
    private static final Set<String> MODS = new HashSet<>(Arrays.asList("skysbuildingpieces", "skysbuildingpiecesbop",
            "skysgrassslabs", "skysterrainsmoother", "skysterrainsmootherbop"));

    public static void run(ServerLevel world) {
        RecipeUniquenessChecks.run(world);
        SortedMap<String, JsonObject> entries = new TreeMap<>();
        List<RecipeHolder<?>> recipes = new ArrayList<>();

        for (RecipeHolder<?> holder : world.getRecipeManager().getRecipes()) {
            ResourceLocation id = holder.id();
            Recipe<?> recipe = holder.value();

            if (id == null || !MODS.contains(id.getNamespace()) || recipe.isSpecial()) {
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

                predicate.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());

                if (seen.add(predicate.toString())) {
                    ingredients.add(predicate);
                }
            }

            require(ingredients.size() > 0, "Empty book ingredients: " + id);
            entry.add("ingredients", ingredients);
            entries.put(id.toString(), entry);
            recipes.add(holder);
        }

        File snapshot = new File("recipe-book-snapshot.json");

        try (Writer out = new OutputStreamWriter(new FileOutputStream(snapshot), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(entries.values(), out);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        if (Boolean.getBoolean("skysbuildingpieces.snapshotRecipeBook")) {
            org.apache.logging.log4j.LogManager.getLogger().info("RECIPE_BOOK_SNAPSHOT_ONLY recipes=" + recipes.size());
            return;
        }

        GameProfile profile = new GameProfile(UUID.fromString("7c27cbb1-2a4e-4e94-8726-964f72cbce17"), "RecipeBookProbe");
        ServerPlayer player = NativeProbeItems.player(world, profile);
        List<Packet<?>> sentPackets = new ArrayList<>();
        Connection network = new Connection(PacketFlow.SERVERBOUND) {
            private final io.netty.channel.Channel testChannel = new io.netty.channel.embedded.EmbeddedChannel();

            @Override
            public io.netty.channel.Channel channel() {
                return testChannel;
            }

            @Override
            public void send(Packet<?> packet) {
                sentPackets.add(packet);
            }

            @Override
            public void send(Packet<?> packet,
                    net.minecraft.network.PacketSendListener listener) {
                sentPackets.add(packet);
            }

            @Override
            public void send(Packet<?> packet,
                    net.minecraft.network.PacketSendListener listener, boolean flush) {
                sentPackets.add(packet);
            }
        };

        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(world.getServer(), network, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
        require(!world.getServer().getAdvancements().getAllAdvancements().isEmpty(), "Advancements were loaded");

        File savedRecipes = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "recipe-book-probe.dat");

        if (savedRecipes.isFile()) {
            try (InputStream input = new FileInputStream(savedRecipes)) {
                player.getRecipeBook().fromNbt(net.minecraft.nbt.NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap()), world.getRecipeManager());
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
            for (RecipeHolder<?> holder : recipes) {
                if (Boolean.getBoolean("skysbuildingpieces.checkAllSkyRecipes")
                        || holder.id().getNamespace().equals("skysbuildingpieces")
                        || holder.id().getNamespace().equals("skysbuildingpiecesbop")) {
                    require(player.getRecipeBook().contains(holder), "Saved recipe book lost " + holder.id());
                }
            }
        }

        int checks = 0;
        List<RecipeHolder<?>> verified = new ArrayList<>();

        for (RecipeHolder<?> holder : recipes) {
            ResourceLocation id = holder.id();
            Recipe<?> recipe = holder.value();

            if (!Boolean.getBoolean("skysbuildingpieces.checkAllSkyRecipes")
                    && !id.getNamespace().equals("skysbuildingpieces")
                    && !id.getNamespace().equals("skysbuildingpiecesbop")) {
                continue;
            }

            verified.add(holder);

            AdvancementHolder advancement = world.getServer().getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(owner(id), "recipes/" + id.getPath()));

            require(advancement != null, "Missing recipe advancement: " + id);
            require(!recipe.getResultItem(world.registryAccess()).isEmpty(), "Missing book result: " + id);
            reset(player, holder, advancement);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.APPLE));
            for (int inventorySlot = 0; inventorySlot < player.getInventory().getContainerSize(); inventorySlot++) {
                    ItemStack changed = player.getInventory().getItem(inventorySlot);
                    if (!changed.isEmpty()) CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), changed);
                }
            require(!player.getRecipeBook().contains(holder), "Unrelated item unlocked " + id);

            Ingredient first = recipe.getIngredients().stream().filter(ingredient -> ingredient != Ingredient.EMPTY)
                    .findFirst().get();
            ItemStack input = first.getItems()[0].copy();

            player.getInventory().setItem(0, input);
            for (int inventorySlot = 0; inventorySlot < player.getInventory().getContainerSize(); inventorySlot++) {
                    ItemStack changed = player.getInventory().getItem(inventorySlot);
                    if (!changed.isEmpty()) CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), changed);
                }
            require(player.getRecipeBook().contains(holder), "Ingredient did not unlock " + id);

            if (!id.getNamespace().equals("skysgrassslabs") || !id.getPath().equals("turf")) {
                reset(player, holder, advancement);
                player.getInventory().clearContent();
                NeoForge.EVENT_BUS
                        .post(new PlayerEvent.ItemCraftedEvent(player, input, RecombinationChecks.grid(2)));
                require(player.getRecipeBook().contains(holder), "Crafted ingredient did not unlock " + id);
            }
            if (id.getPath().contains("recombine") || id.getPath().contains("slab_recombination")) {
                require(first.getItems().length == 1,
                        "Recombination preview must show the normal item: " + id);

                for (ItemStack orientation : acceptedStacks(first)) {
                    require(first.test(orientation), "Packed ingredient does not match " + id);

                    net.minecraft.world.entity.player.StackedContents autoFill = new net.minecraft.world.entity.player.StackedContents();
                    ItemStack available = orientation.copy();

                    available.setCount(2);
                    autoFill.accountStack(available);
                    require(autoFill.canCraft(recipe, null), "Recipe book cannot fill saved orientation: " + id);
                    reset(player, holder, advancement);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, orientation.copy());
                    for (int inventorySlot = 0; inventorySlot < player.getInventory().getContainerSize(); inventorySlot++) {
                    ItemStack changed = player.getInventory().getItem(inventorySlot);
                    if (!changed.isEmpty()) CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), changed);
                }
                    require(player.getRecipeBook().contains(holder), "Saved orientation did not unlock " + id);
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

                    reset(player, holder, advancement);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, other);
                    for (int inventorySlot = 0; inventorySlot < player.getInventory().getContainerSize(); inventorySlot++) {
                    ItemStack changed = player.getInventory().getItem(inventorySlot);
                    if (!changed.isEmpty()) CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), changed);
                }
                    require(!player.getRecipeBook().contains(holder), "Wrong palette material unlocked " + id);
                    player.getInventory().setItem(0, input.copy());
                    for (int inventorySlot = 0; inventorySlot < player.getInventory().getContainerSize(); inventorySlot++) {
                    ItemStack changed = player.getInventory().getItem(inventorySlot);
                    if (!changed.isEmpty()) CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), changed);
                }
                    require(player.getRecipeBook().contains(holder),
                            "Correct flat item failed after negative check " + id);
                    break;
                }
            }

            checks++;
        }

        player.getAdvancements().save();

        net.minecraft.nbt.CompoundTag savedBook = player.getRecipeBook().toNbt();
        net.minecraft.stats.ServerRecipeBook reloaded = new net.minecraft.stats.ServerRecipeBook();

        reloaded.fromNbt(savedBook, world.getRecipeManager());

        for (RecipeHolder<?> holder : verified) {
            require(reloaded.contains(holder), "Book reload lost " + holder.id());
        }

        require(sentPackets.stream()
                .anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundRecipePacket),
                "Unlocks did not send recipe-book packets");

        try (OutputStream output = new FileOutputStream(savedRecipes)) {
            net.minecraft.nbt.NbtIo.writeCompressed(savedBook, output);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }

        world.getServer().reloadResources(
                world.getServer().getPackRepository().getSelectedIds()).join();
        require(!world.getServer().getAdvancements().getAllAdvancements().isEmpty(), "Advancement reload failed");
        player.getAdvancements().reload(world.getServer().getAdvancements());

        for (RecipeHolder<?> holder : verified) {
            ResourceLocation id = holder.id();
            Recipe<?> recipe = holder.value();

            require(world.getServer().getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(owner(id), "recipes/" + id.getPath())) != null,
                    "Advancement reload lost " + id);
        }

        player.getAdvancements().stopListening();
        network.channel().close();
        org.apache.logging.log4j.LogManager.getLogger().info("SKY_RECIPE_BOOK_PASS recipes=" + checks + " inventory=true crafting=true reload=true");
    }

    private static List<ItemStack> acceptedStacks(Ingredient ingredient) {
        List<ItemStack> stacks = new ArrayList<>();

        for (int packed : ingredient.getStackingIds()) {
            ItemStack stack = net.minecraft.world.entity.player.StackedContents.fromStackingIndex(packed);

            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }

        return stacks;
    }

    private static void reset(ServerPlayer player, RecipeHolder<?> holder, AdvancementHolder advancement) {
        player.resetRecipes(Collections.singletonList(holder));

        for (String criterion : advancement.value().criteria().keySet()) {
            player.getAdvancements().revoke(advancement, criterion);
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
