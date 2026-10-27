package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.Collections;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Uses the normal server interaction path, so duplicate shovel use cannot pass unnoticed. */
public final class PathChecks {
    private PathChecks() {
    }

    public static void run(WorldServer world) {
        EntityPlayerMP player = FakePlayerFactory.getMinecraft(world);
        BlockPos position = new BlockPos(-760, 95, -760);
        world.setBlockState(position.down(), Blocks.DIRT.getDefaultState(), 2);
        world.setBlockToAir(position.up());
        player.capabilities.allowEdit = true;
        player.capabilities.isCreativeMode = false;
        int conversions = 0;

        for (String material : new String[] { "minecraft:grass", "minecraft:dirt" }) {
            for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER }) {
                for (int orientation = 0; orientation < shape.states; orientation++) {
                    IBlockState source = Pieces.state(material, shape, orientation);
                    IBlockState expected = Pieces.state("minecraft:grass_path", shape, orientation);
                    for (EnumHand hand : EnumHand.values()) {
                        for (EnumFacing face : EnumFacing.values()) {
                            if (face == EnumFacing.DOWN) {
                                continue;
                            }
                            world.setBlockState(position, source, 2);
                            world.setBlockState(position.down(), Blocks.GRASS.getDefaultState(), 2);
                            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
                            shovel.setItemDamage(3);
                            require(click(world, player, position, shovel, hand, face) == EnumActionResult.SUCCESS,
                                    "a handled shovel click must not fall through to the other hand");
                            require(world.getBlockState(position).equals(expected), "shovel orientation " + material + " / " + shape + " / " + orientation);
                            require(shovel.getItemDamage() == 4, "one successful use costs exactly one durability");
                            IBlockState support = material.equals("minecraft:grass")
                                    ? Blocks.GRASS_PATH.getDefaultState() : Blocks.GRASS.getDefaultState();
                            require(world.getBlockState(position.down()).equals(support),
                                    "only flattening a grass piece also flattens its grass support");
                            // Notify the support as ordinary building would; a one-tick conversion is not enough.
                            support.getBlock().neighborChanged(support, world, position.down(), expected.getBlock());
                            require(world.getBlockState(position.down()).equals(support),
                                    "path support must survive neighbour updates beneath a path piece");
                            AxisAlignedBB originalBounds = source.getBlock().getBoundingBox(source, world, position);
                            AxisAlignedBB pathBounds = expected.getBlock().getBoundingBox(expected, world, position);
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
                    double sourceTop = source.getBlock().getBoundingBox(source, world, position).maxY;
                    AxisAlignedBB bounds = path.getBoundingBox(expected, world, position);
                    require(Math.abs(bounds.maxY - (sourceTop - 1.0 / 16.0)) < 1e-9, "path surface height");
                    require(!path.canSilkHarvest(world, position, expected, player), "paths drop dirt even with Silk Touch");
                    ItemStack drop = path.getDrops(world, position, expected, 0).get(0);
                    require(ItemStack.areItemStacksEqual(drop, Pieces.stack(Pieces.state("minecraft:dirt", shape, orientation), 1)), "matching dirt drop");
                    world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 3);
                    require(world.getBlockState(position).equals(Pieces.state("minecraft:dirt", shape, orientation)), "covered path retains its shape as dirt");
                    world.setBlockToAir(position.up());
                }
            }
        }

        IBlockState corner = Pieces.state("minecraft:grass", Shape.CORNER, 4);
        world.setBlockState(position, corner, 2);
        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        click(world, player, position, shovel, EnumHand.MAIN_HAND, EnumFacing.DOWN);
        require(world.getBlockState(position).equals(corner) && shovel.getItemDamage() == 0, "underside cannot be flattened");
        player.capabilities.allowEdit = false;
        click(world, player, position, shovel, EnumHand.MAIN_HAND, EnumFacing.UP);
        require(world.getBlockState(position).equals(corner) && shovel.getItemDamage() == 0, "edit permission is required");
        player.capabilities.allowEdit = true;
        world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 2);
        click(world, player, position, shovel, EnumHand.MAIN_HAND, EnumFacing.UP);
        require(world.getBlockState(position).equals(corner) && shovel.getItemDamage() == 0, "blocked paths are rejected");
        world.setBlockToAir(position.up());
        click(world, player, position, new ItemStack(Items.IRON_AXE), EnumHand.MAIN_HAND, EnumFacing.UP);
        require(world.getBlockState(position).equals(corner), "axes do not flatten pieces");

        player.capabilities.isCreativeMode = true;
        click(world, player, position, shovel, EnumHand.OFF_HAND, EnumFacing.UP);
        require(shovel.getItemDamage() == 0 && !world.getBlockState(position).equals(corner), "creative shovel durability");
        player.capabilities.isCreativeMode = false;
        Item compatible = new Item() {
            @Override
            public java.util.Set<String> getToolClasses(ItemStack stack) {
                return Collections.singleton("shovel");
            }
        }.setMaxDamage(50);
        world.setBlockState(position, corner, 2);
        ItemStack custom = new ItemStack(compatible);
        click(world, player, position, custom, EnumHand.MAIN_HAND, EnumFacing.UP);
        require(custom.getItemDamage() == 1 && !world.getBlockState(position).equals(corner), "registered shovel support");

        world.setBlockState(position, corner, 2);
        player.setSneaking(true);
        ItemStack sneaking = new ItemStack(Items.IRON_SHOVEL);
        require(click(world, player, position, sneaking, EnumHand.MAIN_HAND, EnumFacing.UP) == EnumActionResult.SUCCESS
                && sneaking.getItemDamage() == 1, "sneaking still activates the shovel once");
        player.setSneaking(false);

        int slabs = slabs(world, player, position);
        supportChecks(world, player, position);
        savedSupports(world, player, System.getProperty("skysbuildingpieces.integrationPhase", "").contains("reload"));
        savedPieces(world, System.getProperty("skysbuildingpieces.integrationPhase", "").contains("reload"));
        System.out.println("BUILDING_PIECES_PATH_PASS conversions=" + conversions + " grass_slab_checks=" + slabs);
    }

    private static void supportChecks(WorldServer world, EntityPlayerMP player, BlockPos position) {
        IBlockState grassCorner = Pieces.state("minecraft:grass", Shape.CORNER, 4);
        for (IBlockState support : new IBlockState[] {
                Blocks.DIRT.getDefaultState(), Blocks.STONE.getDefaultState(),
                Blocks.GRASS_PATH.getDefaultState(), Blocks.FARMLAND.getDefaultState(),
                Blocks.CHEST.getDefaultState() }) {
            world.setBlockToAir(position);
            world.setBlockState(position.down(), support, 2);
            world.setBlockState(position, grassCorner, 2);
            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
            click(world, player, position, shovel, EnumHand.MAIN_HAND, EnumFacing.UP);
            require(world.getBlockState(position.down()).equals(support), "non-grass supports stay unchanged");
        }

        world.setBlockToAir(position);
        world.setBlockState(position.down(), Blocks.GRASS.getDefaultState(), 2);
        world.setBlockState(position, grassCorner, 2);
        world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 2);
        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        click(world, player, position, shovel, EnumHand.MAIN_HAND, EnumFacing.UP);
        require(world.getBlockState(position.down()).getBlock() == Blocks.GRASS
                && shovel.getItemDamage() == 0, "a rejected click cannot change the support");
        world.setBlockToAir(position.up());

        player.capabilities.isCreativeMode = true;
        click(world, player, position, shovel, EnumHand.OFF_HAND, EnumFacing.UP);
        require(world.getBlockState(position.down()).getBlock() == Blocks.GRASS_PATH
                && shovel.getItemDamage() == 0, "creative mode flattens both blocks without tool wear");
        player.capabilities.isCreativeMode = false;

        // A solid replacement still restores vanilla dirt; only the partial path allows this support.
        world.setBlockState(position, Blocks.STONE.getDefaultState(), 3);
        require(world.getBlockState(position.down()).getBlock() == Blocks.DIRT,
                "solid cover still restores a vanilla path support to dirt");
        world.setBlockToAir(position);
    }

    private static void savedSupports(WorldServer world, EntityPlayerMP player, boolean requireExisting) {
        BlockPos origin = new BlockPos(920, 96, 880);
        int checked = 0;
        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                BlockPos position = origin.add(checked % 5, 0, checked / 5);
                IBlockState path = Pieces.state("minecraft:grass_path", shape, orientation);
                if (!requireExisting) {
                    world.setBlockToAir(position);
                    world.setBlockToAir(position.up());
                    world.setBlockState(position.down(), Blocks.GRASS.getDefaultState(), 2);
                    world.setBlockState(position, Pieces.state("minecraft:grass", shape, orientation), 2);
                    click(world, player, position, new ItemStack(Items.IRON_SHOVEL),
                            EnumHand.MAIN_HAND, EnumFacing.UP);
                }
                require(world.getBlockState(position).equals(path), "saved path piece above grass support");
                require(world.getBlockState(position.down()).getBlock() == Blocks.GRASS_PATH,
                        "saved path support survives reload");
                checked++;
            }
        }
        System.out.println("BUILDING_PIECES_PATH_SUPPORT_PASS existing=" + requireExisting + " states=" + checked);
    }

    /** A compact saved fixture also used by the 1.10-to-1.12 upgrade check. */
    public static void savedPieces(WorldServer world, boolean requireExisting) {
        BlockPos origin = new BlockPos(880, 96, 880);
        BlockPos chestPosition = origin.south(6);
        if (!requireExisting) {
            world.setBlockState(chestPosition, Blocks.CHEST.getDefaultState(), 2);
        }
        require(world.getTileEntity(chestPosition) instanceof net.minecraft.tileentity.TileEntityChest,
                "saved path fixture chest");
        net.minecraft.tileentity.TileEntityChest chest =
                (net.minecraft.tileentity.TileEntityChest) world.getTileEntity(chestPosition);
        int slot = 0;
        for (Shape shape : new Shape[] { Shape.STEP, Shape.CORNER }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                IBlockState expected = Pieces.state("minecraft:grass_path", shape, orientation);
                BlockPos position = origin.add(slot % 5, 0, slot / 5);
                ItemStack stack = new ItemStack(expected.getBlock(), slot + 1,
                        expected.getBlock().getMetaFromState(expected));
                net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
                data.setString("path_fixture", shape.name() + "/" + orientation);
                net.minecraft.nbt.NBTTagCompound display = new net.minecraft.nbt.NBTTagCompound();
                display.setString("Name", "Path fixture " + slot);
                data.setTag("display", display);
                stack.setTagCompound(data);
                if (requireExisting) {
                    require(world.getBlockState(position).equals(expected),
                            "saved path state " + shape + " / " + orientation);
                    require(ItemStack.areItemStacksEqual(chest.getStackInSlot(slot), stack),
                            "saved path inventory " + shape + " / " + orientation);
                } else {
                    world.setBlockToAir(position.up());
                    world.setBlockState(position, expected, 2);
                    chest.setInventorySlotContents(slot, stack);
                }
                slot++;
            }
        }
        System.out.println("BUILDING_PIECES_PATH_FIXTURE_PASS existing=" + requireExisting
                + " states=20 stacks=20 units=210 custom_nbt=true");
    }

    private static int slabs(WorldServer world, EntityPlayerMP player, BlockPos position) {
        if (!Block.REGISTRY.containsKey(new ResourceLocation("skysgrassslabs", "grass_slab"))) {
            return 0;
        }
        Block path = Block.REGISTRY.getObject(new ResourceLocation("skysgrassslabs", "path_slab"));
        int checks = 0;
        for (String name : new String[] { "dirt_slab", "grass_slab" }) {
            Block slab = Block.REGISTRY.getObject(new ResourceLocation("skysgrassslabs", name));
            for (BlockSlab.EnumBlockHalf half : BlockSlab.EnumBlockHalf.values()) {
                world.setBlockState(position, slab.getDefaultState().withProperty(BlockSlab.HALF, half), 2);
                ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
                click(world, player, position, shovel, EnumHand.MAIN_HAND, EnumFacing.UP);
                require(world.getBlockState(position).equals(path.getDefaultState().withProperty(BlockSlab.HALF, half))
                        && shovel.getItemDamage() == 1, "normal dirt/grass slab shovel regression");
                checks++;
            }
        }
        return checks;
    }

    private static EnumActionResult click(WorldServer world, EntityPlayerMP player, BlockPos position, ItemStack tool,
            EnumHand hand, EnumFacing face) {
        player.setHeldItem(hand, tool);
        return player.interactionManager.processRightClickBlock(player, world, tool, hand, position, face, .25F, .75F, .25F);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
