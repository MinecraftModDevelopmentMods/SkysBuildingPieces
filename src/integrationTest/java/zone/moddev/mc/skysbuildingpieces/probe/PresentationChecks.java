package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.*;
import net.minecraft.block.BlockWall;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.property.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Real collision and connection checks for the shared wall profile. */
public final class PresentationChecks {
    public static void run(WorldServer world) {
        names();
        connectedGlass(world);
        torches(world);

        BlockPos pos = new BlockPos(-520, 90, -520);
        EnumFacing[] sides = { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST };
        int checked = 0;
        int materials = 0;

        Set<String> coveredMaterials = new HashSet<String>();

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape != Shape.WALL) {
                continue;
            }

            require(block.getDefaultState().getPropertyKeys().contains(BlockWall.UP),
                    "every wall has a derived post state");
            materials += block.palette.materials.size();
            coveredMaterials.addAll(block.palette.materials);

            for (int material = 0; material < block.palette.materials.size(); material++) {
                for (int connections = 0; connections < 16; connections++) {
                    for (boolean covered : new boolean[] { false, true }) {
                        IBlockState state = block.getStateFromMeta(material);

                        world.setBlockState(pos, state, 2);

                        if (covered) {
                            world.setBlockState(pos.up(), Blocks.STONE.getDefaultState(), 2);
                        } else {
                            world.setBlockToAir(pos.up());
                        }
                        for (int entryIndex = 0; entryIndex < sides.length; entryIndex++) {
                            if ((connections & (1 << entryIndex)) == 0) {
                                world.setBlockToAir(pos.offset(sides[entryIndex]));
                            } else {
                                world.setBlockState(pos.offset(sides[entryIndex]), state, 2);
                            }
                        }

                        IBlockState actual = block.getActualState(state, world, pos);

                        require(actual.getValue(PieceBlock.CONNECTIONS) == connections, "wall connections");

                        boolean post = covered || (connections != 5 && connections != 10);

                        require(actual.getValue(BlockWall.UP) == post,
                                "posts follow ends, corners, junctions and overhead support");
                        require(block.getMetaFromState(actual) == material, "connections do not change saved metadata");

                        List<AxisAlignedBB> actualBoxes = block.boxes(actual);
                        List<WallGeometry.Bounds> expected = WallGeometry.boxes(connections, post);

                        require(actualBoxes.size() == expected.size(), "wall box count");

                        for (int entryIndex = 0; entryIndex < expected.size(); entryIndex++) {
                            WallGeometry.Bounds bounds = expected.get(entryIndex);

                            require(actualBoxes.get(entryIndex)
                                    .equals(new AxisAlignedBB(bounds.minX / 16.0, bounds.minY / 16.0,
                                            bounds.minZ / 16.0, bounds.maxX / 16.0, bounds.maxY / 16.0,
                                            bounds.maxZ / 16.0)),
                                    "glass mesh and collision agree");
                        }
                        for (int entryIndex = 0; entryIndex < sides.length; entryIndex++) {
                            require(block.shouldSideBeRendered(state, world, pos,
                                    sides[entryIndex]) == ((connections & (1 << entryIndex)) == 0),
                                    "only touching matching wall ends are hidden");
                        }

                        require(block.shouldSideBeRendered(state, world, pos, EnumFacing.UP),
                                "wall top remains visible");
                        require(block.shouldSideBeRendered(state, world, pos, EnumFacing.DOWN),
                                "wall underside remains visible");
                        checked++;
                    }
                }
            }
        }

        world.setBlockToAir(pos.up());

        for (EnumFacing side : sides) {
            world.setBlockToAir(pos.offset(side));
        }

        IBlockState blue = Pieces.state("minecraft:stained_glass_blue", Shape.WALL, 0);
        IBlockState redSandState = Pieces.state("minecraft:stained_glass_red", Shape.WALL, 0);

        world.setBlockState(pos, blue, 2);
        world.setBlockState(pos.east(), redSandState, 2);
        require(((PieceBlock) blue.getBlock()).shouldSideBeRendered(blue, world, pos, EnumFacing.EAST),
                "different glass colours retain their boundary");
        world.setBlockState(pos.north(), blue, 2);

        RayTraceResult high = blue.getBlock().collisionRayTrace(blue, world, pos,
                new Vec3d(pos.getX() + .5, pos.getY() + .9, pos.getZ() - 1),
                new Vec3d(pos.getX() + .5, pos.getY() + .9, pos.getZ() + 1));
        RayTraceResult low = blue.getBlock().collisionRayTrace(blue, world, pos,
                new Vec3d(pos.getX() + .5, pos.getY() + .7, pos.getZ() - 1),
                new Vec3d(pos.getX() + .5, pos.getY() + .7, pos.getZ() + 1));

        require(high != null && high.hitVec.z == pos.getZ() + .25, "ray passes above the lower arm to the post");
        require(low != null && low.hitVec.z == pos.getZ(), "ray hits the lower connection");
        world.setBlockToAir(pos.east());
        world.setBlockState(pos.south(), blue, 3);

        PieceBlock blueWall = (PieceBlock) blue.getBlock();
        IBlockState straight = blueWall.getActualState(blue, world, pos);

        require(!straight.getValue(BlockWall.UP), "straight runs have no post");
        require(blueWall.getBoundingBox(blue, world, pos).maxY == 13 / 16.0,
                "straight selection follows the lower wall");
        require(blueWall.collisionRayTrace(blue, world, pos,
                new Vec3d(pos.getX() + .5, pos.getY() + .9, pos.getZ() - 1),
                new Vec3d(pos.getX() + .5, pos.getY() + .9, pos.getZ() + 1)) == null,
                "no invisible selectable post in a straight run");
        world.setBlockToAir(pos.south());
        require(blueWall.getActualState(blue, world, pos).getValue(BlockWall.UP),
                "removing the next piece creates an end post");
        world.setBlockState(pos.south(), blue, 3);
        require(!blueWall.getActualState(blue, world, pos).getValue(BlockWall.UP),
                "extending the wall removes its previous end post");
        world.setBlockState(pos.up(), Blocks.STONE.getDefaultState(), 3);
        require(blueWall.getActualState(blue, world, pos).getValue(BlockWall.UP),
                "overhead blocks keep their supporting post");
        world.setBlockToAir(pos.up());
        require(!blueWall.getActualState(blue, world, pos).getValue(BlockWall.UP),
                "removing overhead support removes the post again");
        world.setBlockToAir(pos);

        for (EnumFacing side : sides) {
            world.setBlockToAir(pos.offset(side));
        }

        require(checked == materials * 32, "all wall materials, connection profiles and overhead states");
        require(coveredMaterials.contains("minecraft:gold_block") && coveredMaterials.contains("minecraft:packed_ice"),
                "gold and packed ice are covered");
        System.out.println("BUILDING_PIECES_PRESENTATION_PASS wall_profiles=" + checked);
    }

    private static void names() {
        require(Pieces.stack(Pieces.state("minecraft:gold_block", Shape.WALL, 0), 1).getDisplayName()
                .equals("Gold Wall"), "gold wall display name");
        require(Pieces.stack(Pieces.state("minecraft:gold_block", Shape.STAIRS, 0), 1).getDisplayName()
                .equals("Gold Stairs"), "gold stair display name");
        require(Pieces.stack(Pieces.state("minecraft:planks_oak", Shape.HORIZONTAL_STEP, 0), 1).getDisplayName()
                .equals("Oak Step"), "wood piece display name");
        require(new ItemStack(Blocks.GOLD_BLOCK).getDisplayName().equals("Block of Gold"),
                "vanilla full block name is unchanged");

        ItemStack named = Pieces.stack(Pieces.state("minecraft:gold_block", Shape.WALL, 0), 1);

        named.setStackDisplayName("My Wall");
        require(named.getDisplayName().equals("My Wall"), "custom item names are preserved");

        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            for (String material : block.palette.materials) {
                String previous = null;

                for (int orientation = 0; orientation < block.palette.shape.states; orientation++) {
                    ItemStack stack = Pieces.stack(Pieces.state(material, block.palette.shape, orientation), 1);
                    String name = stack.getDisplayName();

                    require(!name.contains("pieces.") && !name.contains("material.") && !name.contains("%"),
                            "resolved piece name");

                    if (previous != null) {
                        require(previous.equals(name), "orientation does not alter material name");
                    }

                    previous = name;
                    checked++;
                }
            }
        }
        if (zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue.INSTANCE.modules.containsKey("biomesoplenty")) {
            require(Pieces.stack(Pieces.state("biomesoplenty:sapphire_block", Shape.WALL, 0), 1).getDisplayName()
                    .equals("Sapphire Wall"), "BOP gem display name");
            require(Pieces.stack(Pieces.state("biomesoplenty:planks_redwood", Shape.HORIZONTAL_STEP, 0), 1)
                    .getDisplayName().equals("Redwood Step"), "BOP wood display name");
        }

        System.out.println("BUILDING_PIECES_NAMES_PASS states=" + checked);
    }

    private static void connectedGlass(WorldServer world) {
        BlockPos pos = new BlockPos(-528, 90, -528);
        EnumFacing[] sides = { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST };
        List<IBlockState> materials = new ArrayList<IBlockState>();

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape == Shape.WALL && block.palette.group.contains("glass")) {
                for (int slot = 0; slot < block.palette.materials.size(); slot++) {
                    materials.add(block.getStateFromMeta(slot));
                }
            }
        }

        require(materials.size() == 17, "clear glass and all sixteen stained colours");

        int checked = 0;

        for (int material = 0; material < materials.size(); material++) {
            IBlockState wall = materials.get(material);
            IBlockState different = materials.get((material + 1) % materials.size());

            PieceBlock block = (PieceBlock) wall.getBlock();

            require(block.getBlockState().getValidStates().size() == 512, "matching textures add no listed states");

            for (int connections = 0; connections < 16; connections++) {
                for (int matching = 0; matching < 16; matching++) {
                    if ((matching & connections) != matching) {
                        continue;
                    }

                    world.setBlockState(pos, wall, 2);
                    world.setBlockToAir(pos.up());

                    for (int entryIndex = 0; entryIndex < sides.length; entryIndex++) {
                        if ((connections & (1 << entryIndex)) == 0) {
                            world.setBlockToAir(pos.offset(sides[entryIndex]));
                        } else {
                            world.setBlockState(pos.offset(sides[entryIndex]),
                                    (matching & (1 << entryIndex)) != 0 ? wall : different, 2);
                        }
                    }

                    IBlockState extended = block.getExtendedState(wall, world, pos);

                    require(extended instanceof IExtendedBlockState, "glass joins are render-only extended state");

                    IExtendedBlockState data = (IExtendedBlockState) extended;

                    require(data.getUnlistedNames().size() == 1, "exactly one render-only join property");

                    IUnlistedProperty<?> property = data.getUnlistedNames().iterator().next();

                    require(property.getName().equals("glass_joins")
                            && Integer.valueOf(matching).equals(data.getValue(property)),
                            "only identical glass materials lose their border");
                    require(extended.getValue(PieceBlock.CONNECTIONS) == connections,
                            "colour changes do not disconnect the wall");
                    require(block.getMetaFromState(extended) == block.getMetaFromState(wall),
                            "render joins do not change metadata");
                    require(data.getClean().equals(block.getActualState(wall, world, pos)),
                            "render joins do not add listed save states");
                    checked++;
                }
            }
        }
        for (IBlockState wall : materials) {
            for (IBlockState neighbour : materials) {
                for (int side = 0; side < sides.length; side++) {
                    world.setBlockState(pos, wall, 2);

                    for (EnumFacing facing : sides) {
                        world.setBlockToAir(pos.offset(facing));
                    }

                    world.setBlockState(pos.offset(sides[side]), neighbour, 2);

                    PieceBlock block = (PieceBlock) wall.getBlock();
                    PieceBlock other = (PieceBlock) neighbour.getBlock();

                    IExtendedBlockState extended = (IExtendedBlockState) block.getExtendedState(wall, world, pos);
                    int expected = block.definition(wall).id.equals(other.definition(neighbour).id) ? 1 << side : 0;

                    require(extended.getValue(GlassWallTexture.JOINS) == expected,
                            "all clear/stained colour pairs keep the correct boundary");
                    checked++;
                }
            }
        }

        world.setBlockToAir(pos);

        for (EnumFacing side : sides) {
            world.setBlockToAir(pos.offset(side));
        }

        require(checked == 2533, "every connection combination and clear/stained colour pair");
        System.out.println("BUILDING_PIECES_CONTINUOUS_GLASS_PASS cases=" + checked);
    }

    private static void torches(WorldServer world) {
        BlockPos pos = new BlockPos(-524, 90, -524);
        EnumFacing[] sides = { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST };
        EntityPlayerMP player = FakePlayerFactory.getMinecraft(world);

        player.capabilities.isCreativeMode = false;

        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape != Shape.WALL) {
                continue;
            }
            for (int material = 0; material < block.palette.materials.size(); material++) {
                for (int connections = 0; connections < 16; connections++) {
                    for (BlockTorch torch : new BlockTorch[] { (BlockTorch) Blocks.TORCH,
                            (BlockTorch) Blocks.REDSTONE_TORCH }) {
                        IBlockState wall = block.getStateFromMeta(material);

                        world.setBlockState(pos, wall, 3);
                        world.setBlockToAir(pos.up());

                        for (int entryIndex = 0; entryIndex < sides.length; entryIndex++) {
                            if ((connections & (1 << entryIndex)) == 0) {
                                world.setBlockToAir(pos.offset(sides[entryIndex]));
                            } else {
                                world.setBlockState(pos.offset(sides[entryIndex]), wall, 3);
                            }
                        }

                        require(block.canPlaceTorchOnTop(wall, world, pos),
                                "wall supports a top torch " + block.getRegistryName());

                        ItemStack stack = new ItemStack(Item.getItemFromBlock(torch), 2);

                        player.setHeldItem(EnumHand.MAIN_HAND, stack);
                        require(stack.getItem().onItemUse(player, world, pos, EnumHand.MAIN_HAND, EnumFacing.UP, .5f, 1,
                                .5f) == EnumActionResult.SUCCESS, "torch places on wall");

                        IBlockState placed = world.getBlockState(pos.up());

                        require(placed.getBlock() == torch && placed.getValue(BlockTorch.FACING) == EnumFacing.UP
                                && stack.getCount() == 1, "upright torch consumes one item");
                        torch.neighborChanged(placed, world, pos.up(), block, pos);
                        require(world.getBlockState(pos.up()).getBlock() == torch, "torch survives a neighbour update");

                        if (wall.getPropertyKeys().contains(BlockWall.UP)) {
                            require(block.getActualState(wall, world, pos).getValue(BlockWall.UP),
                                    "torch creates its supporting post");
                        }

                        world.setBlockToAir(pos.up());

                        if (wall.getPropertyKeys().contains(BlockWall.UP)) {
                            require(block.getActualState(wall, world, pos)
                                    .getValue(BlockWall.UP) == (connections != 5 && connections != 10),
                                    "removing torch restores the wall profile");
                        }

                        world.setBlockState(pos.up(), placed, 3);
                        world.setBlockToAir(pos);
                        require(world.isAirBlock(pos.up()), "torch drops when its wall is removed");
                        checked++;
                    }
                }
            }
        }

        world.setBlockToAir(pos);
        world.setBlockToAir(pos.up());

        for (EnumFacing side : sides) {
            world.setBlockToAir(pos.offset(side));
        }

        System.out.println("BUILDING_PIECES_WALL_TORCH_PASS cases=" + checked);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void gallery(WorldServer world) {
        world.setWorldTime(6000);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");

        for (int blockX = 0; blockX <= 12; blockX++) {
            for (int blockZ = 0; blockZ <= 11; blockZ++) {
                world.setBlockState(new BlockPos(blockX, 63, blockZ), Blocks.GRASS.getDefaultState(), 2);
            }
        }
        for (int blockX = 3; blockX <= 7; blockX++) {
            world.setBlockState(new BlockPos(blockX, 64, 3),
                    Pieces.state("minecraft:stained_glass_blue", Shape.WALL, 0), 2);
            world.setBlockState(new BlockPos(blockX, 64, 7), Pieces.state("minecraft:glass", Shape.WALL, 0), 2);
        }
        for (int blockZ = 4; blockZ <= 5; blockZ++) {
            world.setBlockState(new BlockPos(7, 64, blockZ),
                    Pieces.state("minecraft:stained_glass_blue", Shape.WALL, 0), 2);
        }

        world.setBlockState(new BlockPos(5, 64, 2), Pieces.state("minecraft:stained_glass_blue", Shape.WALL, 0), 2);
        world.setBlockState(new BlockPos(1, 64, 3), Pieces.state("minecraft:stained_glass_blue", Shape.WALL, 0), 2);
        world.setBlockState(new BlockPos(1, 64, 7), Pieces.state("minecraft:glass", Shape.WALL, 0), 2);
    }

    private PresentationChecks() {
    }
}
