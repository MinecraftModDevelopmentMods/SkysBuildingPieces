package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;

/** Early native checks complement the complete gameplay and upgrade harness. */
@Mod("buildingpiecesbootstrap")
public final class RuntimeBootstrap {
    private MinecraftServer pendingServer;
    private int startupTicks;
    private DeferredAudit deferredAudit;

    private record DeferredAudit(MinecraftServer server, RecipeChecks.Audit recipeAudit,
            RecombinationChecks.Audit recombinationAudit, int recipes, int stacks, long resumeAt) {
    }

    public RuntimeBootstrap(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        PathChecks.register(context.getModBusGroup(),
                "buildingpiecesbootstrap");
        PersistenceRegistrationChecks.register(
                context.getModBusGroup());
        ServerStartedEvent.BUS.addListener(this::started);
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(this::tick);
        net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent.getBus(context.getModBusGroup())
                .addListener(PersistenceOrderChecks::ready);
        net.minecraftforge.fml.DistExecutor.runWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> new ClientProbe().init());
    }

    private void started(ServerStartedEvent event) {
        pendingServer = event.getServer();
        startupTicks = 0;
        if (System.getProperty("skysbuildingpieces.bootstrapPhase", "").startsWith("forward")) {
            NativeForwardChecks.prepare(pendingServer.getLevel(Level.OVERWORLD));
        } else {
            GameplayChecks.prepare(pendingServer.getLevel(Level.OVERWORLD));
        }
    }

    private void tick(net.minecraftforge.event.TickEvent.ServerTickEvent.Post event) {
        if (deferredAudit != null) {
            if (System.nanoTime() < deferredAudit.resumeAt()) {
                return;
            }
            DeferredAudit next = deferredAudit;
            deferredAudit = null;
            audit(next.server(), next);
            return;
        }
        if (pendingServer == null) {
            return;
        }
        if (++startupTicks < 20) {
            return;
        }
        ServerLevel world = pendingServer.getLevel(Level.OVERWORLD);
        boolean forward = System.getProperty("skysbuildingpieces.bootstrapPhase", "").startsWith("forward");
        if (!(forward ? NativeForwardChecks.ready(world) : GameplayChecks.ready(world))) {
            if (startupTicks > 1200) {
                org.apache.logging.log4j.LogManager.getLogger().error("The native fixture never finished loading its chunks and entities");
                pendingServer.halt(false);
                pendingServer = null;
            }
            return;
        }
        MinecraftServer server = pendingServer;
        pendingServer = null;
        // Entity storage is asynchronous in 1.17. Run after normal server ticks
        // make the fixture visible, keeping every collision assertion intact.
        audit(server, null);
    }

    private void audit(MinecraftServer server, DeferredAudit pending) {
        ServerLevel world = server.getLevel(Level.OVERWORLD);
        String phase = System.getProperty("skysbuildingpieces.bootstrapPhase", "");
        File root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();

        try {
            require(phase.equals("fresh") || phase.equals("reload") || phase.startsWith("forward")
                    || phase.startsWith("client") || phase.equals("water") || phase.equals("staging"),
                    "Unknown bootstrap phase");
            require(Pieces.BLOCKS.size() == Catalogue.INSTANCE.palettes.size(), "Every fixed palette is registered");

            int recipes = 0;
            int stacks = 0;

            if (phase.equals("water")) {
                WaterloggingChecks.run(world);
            } else if (phase.equals("staging")) {
                stacks = legacyStacks();
                zone.moddev.mc.skysbuildingpieces.probe.legacy.NativeStagingChecks.run(world);
            } else if (phase.startsWith("forward")) {
                NativeForwardChecks.run(world);
            } else if (phase.startsWith("client")) {
                // The dedicated server exercises crafting and migration exhaustively.
                // Here a real client must load, render and reopen the native piece states.
                PresentationChecks.connectedGlass(world);
                clientGrid(world, phase.endsWith("reload"));
                ClientStatus.complete = true;
                return;
            } else {
                if (pending == null || pending.recombinationAudit() == null) {
                    RecipeChecks.Audit current = pending == null
                            ? new RecipeChecks.Audit(world) : pending.recipeAudit();
                    if (!current.advance(24)) {
                        defer(server, current, null, 0, 0);
                        return;
                    }
                    recipes = current.finish();
                    stacks = legacyStacks();
                    rawPlayer();
                    rawRandomSequences();
                    NativeLegacyChecks.run(world);
                    GameplayChecks.run(world);
                    WaterloggingChecks.run(world);
                    PathChecks.run(world);
                    SharedGrassChecks.run(world);
                    MineralogyChecks.run();
                    defer(server, null, new RecombinationChecks.Audit(world), recipes, stacks);
                    return;
                }
                recipes = pending.recipes();
                stacks = pending.stacks();
                RecombinationChecks.Audit combinations = pending.recombinationAudit();
                if (!combinations.advance(4)) {
                    defer(server, null, combinations, recipes, stacks);
                    return;
                }
                combinations.finish();
                PresentationChecks.run(world);

                // This check reloads server resources. Run it on the dedicated
                // server, away from the integrated client's login handshake.
                RecipeBookChecks.run(world);
            }

            File marker = new File(root, "native-bootstrap.properties");
            Properties result = new Properties();

            if (marker.isFile()) {
                try (FileInputStream input = new FileInputStream(marker)) {
                    result.load(input);
                }
            }
            if (phase.equals("reload")) {
                require(result.getProperty("fresh_complete", "false").equals("true"), "Fresh evidence is missing");
            }

            result.setProperty(phase + "_complete", "true");
            result.setProperty("recipes", Integer.toString(recipes));
            result.setProperty("legacy_stacks", Integer.toString(stacks));
            result.setProperty("palette_blocks", Integer.toString(Pieces.BLOCKS.size()));

            try (FileOutputStream output = new FileOutputStream(marker)) {
                result.store(output, "Native runtime checks");
            }

            PersistenceOrderChecks.verify(world);
            org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_NATIVE_BOOTSTRAP_PASS " + phase + " recipes=" + recipes
                    + " legacy_stacks=" + stacks);
            server.halt(false);
        } catch (Throwable failure) {
            org.apache.logging.log4j.LogManager.getLogger().error("Building Pieces native checks failed", failure);
            ClientStatus.failure = failure;
            // World creation waits for server readiness on the render thread.
            // Report a failed probe there too, so it exits instead of waiting forever.
            net.minecraftforge.fml.DistExecutor.runWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> net.minecraft.client.Minecraft.getInstance().delayCrash(
                            net.minecraft.CrashReport.forThrowable(failure, "Building Pieces runtime check")));
            server.halt(false);
        } finally {
            if (phase.startsWith("forward")) {
                NativeForwardChecks.release(world);
            } else if (deferredAudit == null) {
                GameplayChecks.release(world);
            }
        }
    }

    private void defer(MinecraftServer server, RecipeChecks.Audit recipes, RecombinationChecks.Audit combinations,
            int recipesChecked, int stacks) {
        // Let the server catch up between batches while keeping the watchdog active.
        long resumeAt = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(75);
        deferredAudit = new DeferredAudit(server, recipes, combinations, recipesChecked, stacks, resumeAt);
    }

    private static void clientGrid(ServerLevel world, boolean reload) throws Exception {
        File file = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "native-client-grid.dat");
        CompoundTag saved = new CompoundTag();

        if (reload) {
            try (FileInputStream input = new FileInputStream(file)) {
                saved = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
        }

        world.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED,
                0, world.getServer());

        int number = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            int values = block.palette.materials.size() * block.palette.shape.states;

            for (int metadata = 0; metadata < values; metadata++) {
                BlockPos position = new BlockPos(500 + (number & 63), 180 + (number >> 6 & 15), 500 + (number >> 10));
                String key = Integer.toString(number++);

                if (reload) {
                    require(NbtUtils.writeBlockState(world.getBlockState(position)).equals(saved.getCompoundOrEmpty(key)),
                            "Client world reload lost " + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block) + " / " + metadata);
                } else {
                    world.setBlock(position, block.getStateFromMeta(metadata), 18);
                }
            }
        }
        if (!reload) {
            // Take the snapshot after all neighbours have been placed, retaining the native connection and stair states
            // as well as palette values.
            for (int index = 0; index < number; index++) {
                BlockPos position = new BlockPos(500 + (index & 63), 180 + (index >> 6 & 15), 500 + (index >> 10));
                BlockState state = world.getBlockState(position);
                PieceBlock block = (PieceBlock) state.getBlock();

                state = block.getActualState(state, world, position);
                world.setBlock(position, state, 18);
                saved.put(Integer.toString(index), NbtUtils.writeBlockState(state));
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                NbtIo.writeCompressed(saved, output);
            }
        }

        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_CLIENT_WORLD_PASS reload=" + reload + " states=" + number);
    }

    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return ItemStack.EMPTY;
            }
            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, 3, 3);
    }

    private static int legacyStacks() {
        int checked = 0;
        java.util.Set<net.minecraft.world.item.Item> nativeItems = new java.util.HashSet<>();

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            int assigned = block.palette.materials.size() * block.palette.shape.states;

            for (int metadata = 0; metadata < assigned; metadata++) {
                CompoundTag raw = new CompoundTag();

                raw.putString("id", net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString());
                raw.putShort("Damage", (short) metadata);
                raw.putByte("Count", (byte) 17);
                raw.putByte("Slot", (byte) 4);

                CompoundTag custom = new CompoundTag();

                custom.putString("Note", "Keep this custom data");
                raw.put("tag", custom);
                LegacyBridge.prepareStack(raw);

                ItemStack actual = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(raw);
                ItemStack expected = Pieces.legacyItem(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString(), metadata, 17);

                require(actual.getItem() == expected.getItem() && actual.getCount() == 17,
                        "Legacy material item: " + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block) + " / " + metadata);
                require(zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.hasCustom(actual) && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(actual).equals(custom) && raw.getByteOr("Slot", (byte) 0) == 4,
                        "Legacy custom data and inventory slot");

                if (net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(expected.getItem()).getNamespace().equals("minecraft")
                        && nativeItems.add(expected.getItem())) {
                    // Native item names can have an older meaning. Check the chosen target after both the direct
                    // fixer and the chunk loader's intermediate-version route, not only our own raw conversion.
                    for (boolean intermediate : new boolean[] { false, true }) {
                        CompoundTag fixed = new CompoundTag();
                        ListTag inventory = new ListTag();
                        inventory.add(raw.copy());
                        fixed.put("Inventory", inventory);
                        if (intermediate) {
                            fixed = net.minecraft.util.datafix.DataFixTypes.PLAYER.update(net.minecraft.util.datafix.DataFixers.getDataFixer(), fixed, 1343, 1493);
                        }
                        fixed = net.minecraft.util.datafix.DataFixTypes.PLAYER.update(net.minecraft.util.datafix.DataFixers.getDataFixer(), fixed,
                                intermediate ? 1493 : 1343, 1976).getListOrEmpty("Inventory").getCompoundOrEmpty(0);
                        ItemStack saved = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(fixed);
                        require(saved.getItem() == expected.getItem() && saved.getCount() == 17
                                && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.hasCustom(saved) && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(saved).equals(custom) && fixed.getByteOr("Slot", (byte) 0) == 4,
                                "Native item fixer changed " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(expected.getItem())
                                        + " intermediate=" + intermediate + ": " + fixed);
                    }
                }

                CompoundTag before = raw.copy();

                LegacyBridge.prepareStack(raw);
                require(raw.equals(before), "Legacy item conversion must be idempotent");
                checked++;
            }
        }

        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_NATIVE_ITEM_FIXERS_PASS identities=" + nativeItems.size() + " pipelines=2");
        return checked;
    }

    private static void rawRandomSequences() {
        CompoundTag oldSequences = new CompoundTag();
        CompoundTag sequence = new CompoundTag();
        sequence.putLongArray("source", new long[] {-1435481386322947279L, -7008063121657184808L});
        oldSequences.put("minecraft:blocks/moss_carpet", sequence);
        CompoundTag oldFile = new CompoundTag();
        oldFile.put("data", oldSequences);

        var type = net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES;
        var fixer = net.minecraft.util.datafix.DataFixers.getDataFixer();
        CompoundTag fixed = type.updateToCurrentVersion(fixer, oldFile, 3465).getCompoundOrEmpty("data");
        require(fixed.getIntOr("salt", -1) == 0 && fixed.getCompoundOrEmpty("sequences").equals(oldSequences),
                "Vanilla 1.20.1 random sequences must keep their state and use the original default salt");
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_RANDOM_SEQUENCE_UPGRADE_PASS");
    }

    private static void rawPlayer() {
        PieceBlock block = Pieces.BLOCKS.values().iterator().next();
        int metadata = block.palette.shape.states - 1;
        CompoundTag source = new CompoundTag();
        ListTag inventory = new ListTag();
        CompoundTag stack = new CompoundTag();

        stack.putString("id", net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString());
        stack.putShort("Damage", (short) metadata);
        stack.putByte("Count", (byte) 23);
        stack.putByte("Slot", (byte) 7);

        CompoundTag custom = new CompoundTag();

        custom.putString("Keep", "Unrelated custom data");

        CompoundTag display = new CompoundTag();

        display.putString("Name", "Named legacy piece");
        custom.put("display", display);
        stack.put("tag", custom);
        inventory.add(stack);
        source.put("Inventory", inventory);

        CompoundTag upgraded = net.minecraft.util.datafix.DataFixTypes.PLAYER.update(net.minecraft.util.datafix.DataFixers.getDataFixer(), source, 1343, 1976);
        CompoundTag saved = upgraded.getListOrEmpty("Inventory").getCompoundOrEmpty(0);
        ItemStack actual = zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.read(saved);
        ItemStack expected = Pieces.legacyItem(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString(), metadata, 23);

        require(actual.getItem() == expected.getItem() && actual.getCount() == 23,
                "The real player data fixer must see the recovered flat item, not the metadata carrier");
        require(zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.hasCustom(actual) && zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems.custom(actual).getStringOr("Keep", "").equals("Unrelated custom data")
                && actual.getHoverName().getString().equals("Named legacy piece") && saved.getByteOr("Slot", (byte) 0) == 7,
                "Player data fixing must preserve the custom name, slot and unrelated data");
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_RAW_PLAYER_FIXER_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
