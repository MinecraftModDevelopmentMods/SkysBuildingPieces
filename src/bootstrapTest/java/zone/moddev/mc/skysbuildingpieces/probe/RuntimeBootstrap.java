package zone.moddev.mc.skysbuildingpieces.probe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;

/** Early native checks complement the complete gameplay and upgrade harness. */
@Mod("buildingpiecesbootstrap")
public final class RuntimeBootstrap {
    public RuntimeBootstrap() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        net.minecraftforge.fml.DistExecutor.runWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> new ClientProbe().init());
    }

    private void started(FMLServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerWorld world = server.getWorld(DimensionType.OVERWORLD);
        String phase = System.getProperty("skysbuildingpieces.bootstrapPhase", "");
        File root = server.getActiveAnvilConverter().getFile(server.getFolderName(), "level.dat").getParentFile();

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
                zone.moddev.mc.skysbuildingpieces.legacy.NativeStagingChecks.run(world);
            } else if (phase.startsWith("forward")) {
                NativeForwardChecks.run(world);
            } else {
                recipes = RecipeChecks.run(world);
                stacks = legacyStacks();
                rawPlayer();
                NativeLegacyChecks.run(world);
                GameplayChecks.run(world);
                WaterloggingChecks.run(world);
                PathChecks.run(world);
                SharedGrassChecks.run(world);
                MineralogyChecks.run();
                RecombinationChecks.run(world);
                RecipeBookChecks.run(world);
                PresentationChecks.run(world);

                if (phase.startsWith("client")) {
                    PresentationChecks.connectedGlass(world);
                    clientGrid(world, phase.endsWith("reload"));
                    ClientStatus.complete = true;
                    return;
                }
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

            System.out.println("BUILDING_PIECES_NATIVE_BOOTSTRAP_PASS " + phase + " recipes=" + recipes
                    + " legacy_stacks=" + stacks);
            server.initiateShutdown(false);
        } catch (Throwable failure) {
            failure.printStackTrace();
            ClientStatus.failure = failure;
            server.initiateShutdown(false);
        }
    }

    private static void clientGrid(ServerWorld world, boolean reload) throws Exception {
        File file = new File(world.getSaveHandler().getWorldDirectory(), "native-client-grid.dat");
        CompoundNBT saved = new CompoundNBT();

        if (reload) {
            try (FileInputStream input = new FileInputStream(file)) {
                saved = CompressedStreamTools.readCompressed(input);
            }
        }

        CompoundNBT rules = world.getGameRules().write();

        rules.putString("randomTickSpeed", "0");
        world.getGameRules().read(rules);

        int number = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            int values = block.palette.materials.size() * block.palette.shape.states;

            for (int metadata = 0; metadata < values; metadata++) {
                BlockPos position = new BlockPos(500 + (number & 63), 180 + (number >> 6 & 15), 500 + (number >> 10));
                String key = Integer.toString(number++);

                if (reload) {
                    require(NBTUtil.writeBlockState(world.getBlockState(position)).equals(saved.getCompound(key)),
                            "Client world reload lost " + block.getRegistryName() + " / " + metadata);
                } else {
                    world.setBlockState(position, block.getStateFromMeta(metadata), 18);
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
                world.setBlockState(position, state, 18);
                saved.put(Integer.toString(index), NBTUtil.writeBlockState(state));
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                CompressedStreamTools.writeCompressed(saved, output);
            }
        }

        System.out.println("BUILDING_PIECES_CLIENT_WORLD_PASS reload=" + reload + " states=" + number);
    }

    private static CraftingInventory grid() {
        return new CraftingInventory(new Container(null, 0) {
            @Override
            public boolean canInteractWith(PlayerEntity player) {
                return true;
            }
        }, 3, 3);
    }

    private static int legacyStacks() {
        int checked = 0;
        java.util.Set<net.minecraft.item.Item> nativeItems = new java.util.HashSet<>();

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            int assigned = block.palette.materials.size() * block.palette.shape.states;

            for (int metadata = 0; metadata < assigned; metadata++) {
                CompoundNBT raw = new CompoundNBT();

                raw.putString("id", block.getRegistryName().toString());
                raw.putShort("Damage", (short) metadata);
                raw.putByte("Count", (byte) 17);
                raw.putByte("Slot", (byte) 4);

                CompoundNBT custom = new CompoundNBT();

                custom.putString("Note", "Keep this custom data");
                raw.put("tag", custom);
                LegacyBridge.prepareStack(raw);

                ItemStack actual = ItemStack.read(raw);
                ItemStack expected = Pieces.legacyItem(block.getRegistryName().toString(), metadata, 17);

                require(actual.getItem() == expected.getItem() && actual.getCount() == 17,
                        "Legacy material item: " + block.getRegistryName() + " / " + metadata);
                require(actual.hasTag() && actual.getTag().equals(custom) && raw.getByte("Slot") == 4,
                        "Legacy custom data and inventory slot");

                if (expected.getItem().getRegistryName().getNamespace().equals("minecraft")
                        && nativeItems.add(expected.getItem())) {
                    // Native item names can have an older meaning. Check the chosen target after both the direct
                    // fixer and the chunk loader's intermediate-version route, not only our own raw conversion.
                    for (boolean intermediate : new boolean[] { false, true }) {
                        CompoundNBT fixed = new CompoundNBT();
                        ListNBT inventory = new ListNBT();
                        inventory.add(raw.copy());
                        fixed.put("Inventory", inventory);
                        if (intermediate) {
                            fixed = NBTUtil.update(net.minecraft.util.datafix.DataFixesManager.getDataFixer(),
                                    net.minecraft.util.datafix.DefaultTypeReferences.PLAYER, fixed, 1343, 1493);
                        }
                        fixed = NBTUtil.update(net.minecraft.util.datafix.DataFixesManager.getDataFixer(),
                                net.minecraft.util.datafix.DefaultTypeReferences.PLAYER, fixed,
                                intermediate ? 1493 : 1343, 1976).getList("Inventory", 10).getCompound(0);
                        ItemStack saved = ItemStack.read(fixed);
                        require(saved.getItem() == expected.getItem() && saved.getCount() == 17
                                && saved.hasTag() && saved.getTag().equals(custom) && fixed.getByte("Slot") == 4,
                                "Native item fixer changed " + expected.getItem().getRegistryName()
                                        + " intermediate=" + intermediate + ": " + fixed);
                    }
                }

                CompoundNBT before = raw.copy();

                LegacyBridge.prepareStack(raw);
                require(raw.equals(before), "Legacy item conversion must be idempotent");
                checked++;
            }
        }

        System.out.println("BUILDING_PIECES_NATIVE_ITEM_FIXERS_PASS identities=" + nativeItems.size() + " pipelines=2");
        return checked;
    }

    private static void rawPlayer() {
        PieceBlock block = Pieces.BLOCKS.values().iterator().next();
        int metadata = block.palette.shape.states - 1;
        CompoundNBT source = new CompoundNBT();
        ListNBT inventory = new ListNBT();
        CompoundNBT stack = new CompoundNBT();

        stack.putString("id", block.getRegistryName().toString());
        stack.putShort("Damage", (short) metadata);
        stack.putByte("Count", (byte) 23);
        stack.putByte("Slot", (byte) 7);

        CompoundNBT custom = new CompoundNBT();

        custom.putString("Keep", "Unrelated custom data");

        CompoundNBT display = new CompoundNBT();

        display.putString("Name", "Named legacy piece");
        custom.put("display", display);
        stack.put("tag", custom);
        inventory.add(stack);
        source.put("Inventory", inventory);

        CompoundNBT upgraded = NBTUtil.update(net.minecraft.util.datafix.DataFixesManager.getDataFixer(),
                net.minecraft.util.datafix.DefaultTypeReferences.PLAYER, source, 1343, 1976);
        CompoundNBT saved = upgraded.getList("Inventory", 10).getCompound(0);
        ItemStack actual = ItemStack.read(saved);
        ItemStack expected = Pieces.legacyItem(block.getRegistryName().toString(), metadata, 23);

        require(actual.getItem() == expected.getItem() && actual.getCount() == 23,
                "The real player data fixer must see the recovered flat item, not the metadata carrier");
        require(actual.hasTag() && actual.getTag().getString("Keep").equals("Unrelated custom data")
                && actual.getDisplayName().getString().equals("Named legacy piece") && saved.getByte("Slot") == 7,
                "Player data fixing must preserve the custom name, slot and unrelated data");
        System.out.println("BUILDING_PIECES_RAW_PLAYER_FIXER_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
