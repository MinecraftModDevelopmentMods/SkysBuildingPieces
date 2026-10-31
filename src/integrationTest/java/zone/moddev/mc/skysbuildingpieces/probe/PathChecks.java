package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Collections;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Uses the normal server interaction path, so duplicate shovel use cannot pass unnoticed. */
public final class PathChecks {
    private PathChecks() {
    }

    public static void run(ServerWorld world) {
        ServerPlayerEntity player = FakePlayerFactory.getMinecraft(world);
        BlockPos position = new BlockPos(-760, 95, -760);

        world.setBlockState(position.down(), Blocks.DIRT.getDefaultState(), 2);
        world.removeBlock(position.up(), false);
        player.abilities.allowEdit = true;
        player.abilities.isCreativeMode = false;

        int conversions = 0;

        for (String material : new String[] { "minecraft:grass", "minecraft:dirt" }) {
            for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER }) {
                for (int orientation = 0; orientation < shape.states; orientation++) {
                    BlockState source = Pieces.state(material, shape, orientation);
                    BlockState expected = Pieces.state("minecraft:grass_path", shape, orientation);

                    for (Hand hand : Hand.values()) {
                        for (Direction face : Direction.values()) {
                            if (face == Direction.DOWN) {
                                continue;
                            }

                            world.setBlockState(position, source, 2);
                            world.setBlockState(position.down(), Blocks.GRASS_BLOCK.getDefaultState(), 2);

                            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);

                            shovel.setDamage(3);
                            require(click(world, player, position, shovel, hand, face) == ActionResultType.SUCCESS,
                                    "a handled shovel click must not fall through to the other hand");
                            expected = ((PieceBlock) expected.getBlock()).getActualState(expected, world, position);
                            require(world.getBlockState(position).equals(expected), "shovel orientation " + material
                                    + " / " + shape + " / " + orientation + " became " + world.getBlockState(position));
                            require(shovel.getDamage() == 4, "one successful use costs exactly one durability");

                            BlockState support = material.equals("minecraft:grass")
                                    ? Blocks.GRASS_PATH.getDefaultState()
                                    : Blocks.GRASS_BLOCK.getDefaultState();

                            require(world.getBlockState(position.down()).equals(support),
                                    "only flattening a grass piece also flattens its grass support");
                            // Notify the support as ordinary building would; a one-tick conversion is not enough.
                            support.getBlock().neighborChanged(support, world, position.down(), expected.getBlock(),
                                    position, false);

                            if (support.getBlock() == Blocks.GRASS_PATH) {
                                require(support.isValidPosition(world, position.down()),
                                        "partial path pieces do not schedule a covered-path conversion");
                            }

                            require(world.getBlockState(position.down()).equals(support),
                                    "path support must survive neighbour updates beneath a path piece");

                            AxisAlignedBB originalBounds = source.getShape(world, position).getBoundingBox();
                            AxisAlignedBB pathBounds = expected.getShape(world, position).getBoundingBox();
                            double base = originalBounds.minY;

                            if (material.equals("minecraft:grass") && base == 0) {
                                base -= 1.0 / 16.0;
                            }

                            require(Math.abs(pathBounds.minY - base) < 1e-9,
                                    "the path base meets its support without moving the top");
                            require(expected.getMaterial().blocksMovement() && !expected.getMaterial().isReplaceable(),
                                    "path pieces retain collision and cannot be overwritten");
                            conversions++;
                        }
                    }

                    PieceBlock path = (PieceBlock) expected.getBlock();

                    require(path.mask(expected) == Geometry.mask(shape, orientation), "path footprint changed");

                    double sourceTop = source.getShape(world, position).getBoundingBox().maxY;
                    AxisAlignedBB bounds = expected.getShape(world, position).getBoundingBox();

                    require(Math.abs(bounds.maxY - (sourceTop - 1.0 / 16.0)) < 1e-9, "path surface height");

                    ItemStack silkTool = new ItemStack(Items.DIAMOND_PICKAXE);

                    silkTool.addEnchantment(net.minecraft.enchantment.Enchantments.SILK_TOUCH, 1);
                    require(ItemStack.areItemStacksEqual(
                            Block.getDrops(expected, world, position, null, player, silkTool).get(0),
                            Pieces.stack(Pieces.state("minecraft:dirt", shape, orientation), 1)),
                            "paths drop dirt even with Silk Touch");

                    ItemStack drop = Block.getDrops(expected, world, position, null, player, ItemStack.EMPTY).get(0);

                    require(ItemStack.areItemStacksEqual(drop,
                            Pieces.stack(Pieces.state("minecraft:dirt", shape, orientation), 1)), "matching dirt drop");
                    world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 3);
                    require(world.getBlockState(position).equals(Pieces.state("minecraft:dirt", shape, orientation)),
                            "covered path retains its shape as dirt");
                    world.removeBlock(position.up(), false);
                }
            }
        }

        BlockState corner = Pieces.state("minecraft:grass", Shape.CORNER, 4);

        world.setBlockState(position, corner, 2);

        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);

        click(world, player, position, shovel, Hand.MAIN_HAND, Direction.DOWN);
        require(world.getBlockState(position).equals(corner) && shovel.getDamage() == 0,
                "underside cannot be flattened");
        player.abilities.allowEdit = false;
        click(world, player, position, shovel, Hand.MAIN_HAND, Direction.UP);
        require(world.getBlockState(position).equals(corner) && shovel.getDamage() == 0, "edit permission is required");
        player.abilities.allowEdit = true;
        world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 2);
        click(world, player, position, shovel, Hand.MAIN_HAND, Direction.UP);
        require(world.getBlockState(position).equals(corner) && shovel.getDamage() == 0, "blocked paths are rejected");
        world.removeBlock(position.up(), false);
        click(world, player, position, new ItemStack(Items.IRON_AXE), Hand.MAIN_HAND, Direction.UP);
        require(world.getBlockState(position).equals(corner), "axes do not flatten pieces");

        player.abilities.isCreativeMode = true;
        click(world, player, position, shovel, Hand.OFF_HAND, Direction.UP);
        require(shovel.getDamage() == 0 && !world.getBlockState(position).equals(corner), "creative shovel durability");
        player.abilities.isCreativeMode = false;

        Item compatible = new Item(new Item.Properties().defaultMaxDamage(50)) {
            @Override
            public java.util.Set<net.minecraftforge.common.ToolType> getToolTypes(ItemStack stack) {
                return Collections.singleton(net.minecraftforge.common.ToolType.SHOVEL);
            }
        };

        world.setBlockState(position, corner, 2);

        ItemStack custom = new ItemStack(compatible);

        click(world, player, position, custom, Hand.MAIN_HAND, Direction.UP);
        require(custom.getDamage() == 1 && !world.getBlockState(position).equals(corner), "registered shovel support");

        world.setBlockState(position, corner, 2);
        player.setSneaking(true);

        ItemStack sneaking = new ItemStack(Items.IRON_SHOVEL);

        require(click(world, player, position, sneaking, Hand.MAIN_HAND, Direction.UP) == ActionResultType.SUCCESS
                && sneaking.getDamage() == 1, "sneaking still activates the shovel once");
        player.setSneaking(false);

        int slabs = slabs(world, player, position);

        supportChecks(world, player, position);
        savedSupports(world, player, System.getProperty("skysbuildingpieces.integrationPhase",
                System.getProperty("skysbuildingpieces.bootstrapPhase", "")).contains("reload"));
        savedPieces(world, System.getProperty("skysbuildingpieces.integrationPhase",
                System.getProperty("skysbuildingpieces.bootstrapPhase", "")).contains("reload"));
        System.out.println("BUILDING_PIECES_PATH_PASS conversions=" + conversions + " grass_slab_checks=" + slabs);
    }

    private static void supportChecks(ServerWorld world, ServerPlayerEntity player, BlockPos position) {
        BlockState grassCorner = Pieces.state("minecraft:grass", Shape.CORNER, 4);

        for (BlockState support : new BlockState[] { Blocks.DIRT.getDefaultState(), Blocks.STONE.getDefaultState(),
                Blocks.GRASS_PATH.getDefaultState(), Blocks.FARMLAND.getDefaultState(),
                Blocks.CHEST.getDefaultState() }) {
            world.removeBlock(position, false);
            world.setBlockState(position.down(), support, 2);
            world.setBlockState(position, grassCorner, 2);

            // Native farmland may already turn to dirt when the piece is placed. The shovel must leave the resulting
            // non-grass support untouched.
            BlockState beforeClick = world.getBlockState(position.down());
            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);

            click(world, player, position, shovel, Hand.MAIN_HAND, Direction.UP);
            require(world.getBlockState(position.down()).equals(beforeClick), "non-grass supports stay unchanged");
        }

        world.removeBlock(position, false);
        world.setBlockState(position.down(), Blocks.GRASS_BLOCK.getDefaultState(), 2);
        world.setBlockState(position, grassCorner, 2);
        world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 2);

        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);

        click(world, player, position, shovel, Hand.MAIN_HAND, Direction.UP);
        require(world.getBlockState(position.down()).getBlock() == Blocks.GRASS_BLOCK && shovel.getDamage() == 0,
                "a rejected click cannot change the support");
        world.removeBlock(position.up(), false);

        player.abilities.isCreativeMode = true;
        click(world, player, position, shovel, Hand.OFF_HAND, Direction.UP);
        require(world.getBlockState(position.down()).getBlock() == Blocks.GRASS_PATH && shovel.getDamage() == 0,
                "creative mode flattens both blocks without tool wear");
        player.abilities.isCreativeMode = false;

        // A solid replacement still restores vanilla dirt; only the partial path allows this support.
        world.setBlockState(position, Blocks.STONE.getDefaultState(), 3);

        // Native paths schedule their covered-block check instead of changing immediately during the neighbour
        // notification.
        BlockState coveredSupport = world.getBlockState(position.down());

        require(!coveredSupport.isValidPosition(world, position.down())
                && world.getPendingBlockTicks().isTickScheduled(position.down(), Blocks.GRASS_PATH),
                "solid cover schedules the native path conversion");
        coveredSupport.getBlock().tick(coveredSupport, world, position.down(), new java.util.Random(0));
        require(world.getBlockState(position.down()).getBlock() == Blocks.DIRT,
                "solid cover still restores a vanilla path support to dirt");
        world.removeBlock(position, false);
    }

    private static void savedSupports(ServerWorld world, ServerPlayerEntity player, boolean requireExisting) {
        BlockPos origin = new BlockPos(920, 96, 880);
        int checked = 0;

        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                BlockPos position = origin.add(checked % 5, 0, checked / 5);
                BlockState path = Pieces.state("minecraft:grass_path", shape, orientation);

                if (!requireExisting) {
                    world.removeBlock(position, false);
                    world.removeBlock(position.up(), false);
                    world.setBlockState(position.down(), Blocks.GRASS_BLOCK.getDefaultState(), 2);
                    world.setBlockState(position, Pieces.state("minecraft:grass", shape, orientation), 2);
                    click(world, player, position, new ItemStack(Items.IRON_SHOVEL), Hand.MAIN_HAND, Direction.UP);
                }

                BlockState loaded = world.getBlockState(position);

                path = ((PieceBlock) path.getBlock()).getActualState(path, world, position);
                require(loaded.equals(path), "saved path piece above grass support: " + position);
                require(world.getBlockState(position.down()).getBlock() == Blocks.GRASS_PATH,
                        "saved path support survives reload");
                checked++;
            }
        }

        System.out.println("BUILDING_PIECES_PATH_SUPPORT_PASS existing=" + requireExisting + " states=" + checked);
    }

    /** A compact saved fixture also used by the 1.10-to-1.12 upgrade check. */
    public static void savedPieces(ServerWorld world, boolean requireExisting) {
        BlockPos origin = new BlockPos(880, 96, 880);
        BlockPos chestPosition = origin.south(6);

        if (!requireExisting) {
            world.setBlockState(chestPosition, Blocks.CHEST.getDefaultState(), 2);
        }

        require(world.getTileEntity(chestPosition) instanceof net.minecraft.tileentity.ChestTileEntity,
                "saved path fixture chest");

        net.minecraft.tileentity.ChestTileEntity chest = (net.minecraft.tileentity.ChestTileEntity) world
                .getTileEntity(chestPosition);
        int slot = 0;

        for (Shape shape : new Shape[] { Shape.STEP, Shape.CORNER }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                BlockState expected = Pieces.state("minecraft:grass_path", shape, orientation);
                BlockPos position = origin.add(slot % 5, 0, slot / 5);
                ItemStack stack = Pieces.stack(expected, slot + 1);
                net.minecraft.nbt.CompoundNBT data = new net.minecraft.nbt.CompoundNBT();

                data.putString("path_fixture", shape.name() + "/" + orientation);

                net.minecraft.nbt.CompoundNBT display = new net.minecraft.nbt.CompoundNBT();

                display.putString("Name", "{\"text\":\"Path fixture " + slot + "\"}");
                data.put("display", display);
                stack.setTag(data);

                if (requireExisting) {
                    require(world.getBlockState(position).equals(expected),
                            "saved path state " + shape + " / " + orientation);
                    require(ItemStack.areItemStacksEqual(chest.getStackInSlot(slot), stack),
                            "saved path inventory " + shape + " / " + orientation);
                } else {
                    world.removeBlock(position.up(), false);
                    world.setBlockState(position, expected, 2);
                    chest.setInventorySlotContents(slot, stack);
                }

                slot++;
            }
        }

        System.out.println("BUILDING_PIECES_PATH_FIXTURE_PASS existing=" + requireExisting
                + " states=20 stacks=20 units=210 custom_nbt=true");
    }

    private static int slabs(ServerWorld world, ServerPlayerEntity player, BlockPos position) {
        if (!net.minecraftforge.registries.ForgeRegistries.BLOCKS
                .containsKey(new ResourceLocation("skysgrassslabs", "grass_slab"))) {
            return 0;
        }

        Block path = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                .getValue(new ResourceLocation("skysgrassslabs", "path_slab"));
        int checks = 0;

        for (String name : new String[] { "dirt_slab", "grass_slab" }) {
            Block slab = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                    .getValue(new ResourceLocation("skysgrassslabs", name));

            for (net.minecraft.state.properties.SlabType half : new net.minecraft.state.properties.SlabType[] {
                    net.minecraft.state.properties.SlabType.BOTTOM, net.minecraft.state.properties.SlabType.TOP }) {
                world.setBlockState(position, slab.getDefaultState().with(SlabBlock.TYPE, half), 2);

                ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);

                click(world, player, position, shovel, Hand.MAIN_HAND, Direction.UP);
                require(world.getBlockState(position).equals(path.getDefaultState().with(SlabBlock.TYPE, half))
                        && shovel.getDamage() == 1, "normal dirt/grass slab shovel regression");
                checks++;
            }
        }

        return checks;
    }

    private static ActionResultType click(ServerWorld world, ServerPlayerEntity player, BlockPos position,
            ItemStack tool, Hand hand, Direction face) {
        player.setHeldItem(hand, tool);
        return player.interactionManager.func_219441_a(player, world, tool, hand,
                new net.minecraft.util.math.BlockRayTraceResult(new net.minecraft.util.math.Vec3d(position.getX() + .25,
                        position.getY() + .75, position.getZ() + .25), face, position, false));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
