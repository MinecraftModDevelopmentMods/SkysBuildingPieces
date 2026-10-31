package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.*;
import net.minecraft.block.WallBlock;
import net.minecraft.block.Block;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayerFactory;

import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Real collision and connection checks for the shared wall profile. */
public final class PresentationChecks {
    public static void run(ServerWorld world) {
        torches(world);

        BlockPos pos = new BlockPos(-520, 90, -520);
        Direction[] sides = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        int checked = 0;
        int materials = 0;

        Set<String> coveredMaterials = new HashSet<String>();

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape != Shape.WALL) {
                continue;
            }

            require(block.getDefaultState().has(WallBlock.UP), "every wall has a derived post state");
            materials += block.palette.materials.size();
            coveredMaterials.addAll(block.palette.materials);

            for (int material = 0; material < block.palette.materials.size(); material++) {
                for (int connections = 0; connections < 16; connections++) {
                    for (boolean covered : new boolean[] { false, true }) {
                        BlockState state = block.getStateFromMeta(material);

                        world.setBlockState(pos, state, 2);

                        if (covered) {
                            world.setBlockState(pos.up(), Blocks.STONE.getDefaultState(), 2);
                        } else {
                            world.removeBlock(pos.up(), false);
                        }
                        for (int entryIndex = 0; entryIndex < sides.length; entryIndex++) {
                            if ((connections & (1 << entryIndex)) == 0) {
                                world.removeBlock(pos.offset(sides[entryIndex]), false);
                            } else {
                                world.setBlockState(pos.offset(sides[entryIndex]), state, 2);
                            }
                        }

                        BlockState actual = block.getActualState(state, world, pos);

                        require(actual.get(PieceBlock.CONNECTIONS) == connections, "wall connections");

                        boolean post = covered || (connections != 5 && connections != 10);

                        require(actual.get(WallBlock.UP) == post,
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
                            require(visible(block, state, world, pos,
                                    sides[entryIndex]) == ((connections & (1 << entryIndex)) == 0),
                                    "only touching matching wall ends are hidden");
                        }

                        require(visible(block, state, world, pos, Direction.UP), "wall top remains visible");
                        require(visible(block, state, world, pos, Direction.DOWN), "wall underside remains visible");
                        checked++;
                    }
                }
            }
        }

        world.removeBlock(pos.up(), false);

        for (Direction side : sides) {
            world.removeBlock(pos.offset(side), false);
        }

        BlockState blue = Pieces.state("minecraft:stained_glass_blue", Shape.WALL, 0);
        BlockState redSandState = Pieces.state("minecraft:stained_glass_red", Shape.WALL, 0);

        world.setBlockState(pos, blue, 2);
        world.setBlockState(pos.east(), redSandState, 2);
        require(visible((PieceBlock) blue.getBlock(), blue, world, pos, Direction.EAST),
                "different glass colours retain their boundary");
        world.setBlockState(pos.north(), blue, 2);

        BlockRayTraceResult high = trace(blue, world, pos, new Vec3d(pos.getX() + .5, pos.getY() + .9, pos.getZ() - 1),
                new Vec3d(pos.getX() + .5, pos.getY() + .9, pos.getZ() + 1));
        BlockRayTraceResult low = trace(blue, world, pos, new Vec3d(pos.getX() + .5, pos.getY() + .7, pos.getZ() - 1),
                new Vec3d(pos.getX() + .5, pos.getY() + .7, pos.getZ() + 1));

        require(high != null && high.getHitVec().z == pos.getZ() + .25, "ray passes above the lower arm to the post");
        require(low != null && low.getHitVec().z == pos.getZ(), "ray hits the lower connection");
        world.removeBlock(pos.east(), false);
        world.setBlockState(pos.south(), blue, 3);

        PieceBlock blueWall = (PieceBlock) blue.getBlock();
        BlockState straight = blueWall.getActualState(blue, world, pos);

        require(!straight.get(WallBlock.UP), "straight runs have no post");
        require(straight.getShape(world, pos).getBoundingBox().maxY == 13 / 16.0,
                "straight selection follows the lower wall");
        require(trace(blue, world, pos, new Vec3d(pos.getX() + .5, pos.getY() + .9, pos.getZ() - 1),
                new Vec3d(pos.getX() + .5, pos.getY() + .9, pos.getZ() + 1)) == null,
                "no invisible selectable post in a straight run");
        world.removeBlock(pos.south(), false);
        require(blueWall.getActualState(blue, world, pos).get(WallBlock.UP),
                "removing the next piece creates an end post");
        world.setBlockState(pos.south(), blue, 3);
        require(!blueWall.getActualState(blue, world, pos).get(WallBlock.UP),
                "extending the wall removes its previous end post");
        world.setBlockState(pos.up(), Blocks.STONE.getDefaultState(), 3);
        require(blueWall.getActualState(blue, world, pos).get(WallBlock.UP),
                "overhead blocks keep their supporting post");
        world.removeBlock(pos.up(), false);
        require(!blueWall.getActualState(blue, world, pos).get(WallBlock.UP),
                "removing overhead support removes the post again");
        world.removeBlock(pos, false);

        for (Direction side : sides) {
            world.removeBlock(pos.offset(side), false);
        }

        require(checked == materials * 32, "all wall materials, connection profiles and overhead states");
        require(coveredMaterials.contains("minecraft:gold_block") && coveredMaterials.contains("minecraft:packed_ice"),
                "gold and packed ice are covered");
        System.out.println("BUILDING_PIECES_PRESENTATION_PASS wall_profiles=" + checked);
    }

    public static void names() {
        require(Pieces.stack(Pieces.state("minecraft:gold_block", Shape.WALL, 0), 1).getDisplayName().getString()
                .equals("Gold Wall"), "gold wall display name");
        require(Pieces.stack(Pieces.state("minecraft:gold_block", Shape.STAIRS, 0), 1).getDisplayName().getString()
                .equals("Gold Stairs"), "gold stair display name");
        require(Pieces.stack(Pieces.state("minecraft:planks_oak", Shape.HORIZONTAL_STEP, 0), 1).getDisplayName()
                .getString().equals("Oak Step"), "wood piece display name");
        require(new ItemStack(Blocks.GOLD_BLOCK).getDisplayName().getString().equals("Block of Gold"),
                "vanilla full block name is unchanged");

        ItemStack named = Pieces.stack(Pieces.state("minecraft:gold_block", Shape.WALL, 0), 1);

        named.setDisplayName(new net.minecraft.util.text.StringTextComponent("My Wall"));
        require(named.getDisplayName().getString().equals("My Wall"), "custom item names are preserved");

        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            for (String material : block.palette.materials) {
                String previous = null;

                for (int orientation = 0; orientation < block.palette.shape.states; orientation++) {
                    ItemStack stack = Pieces.stack(Pieces.state(material, block.palette.shape, orientation), 1);
                    String name = stack.getDisplayName().getString();

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
                    .getString().equals("Light Blue Terracotta Wall"),
                    "retired sapphire uses its current material name");
            require(Pieces.stack(Pieces.state("biomesoplenty:planks_redwood", Shape.HORIZONTAL_STEP, 0), 1)
                    .getDisplayName().getString().equals("Redwood Step"), "BOP wood display name");
        }

        System.out.println("BUILDING_PIECES_NAMES_PASS states=" + checked);
    }

    public static void connectedGlass(ServerWorld world) {
        BlockPos pos = new BlockPos(-528, 90, -528);
        Direction[] sides = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        List<BlockState> materials = new ArrayList<BlockState>();

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
            BlockState wall = materials.get(material);
            BlockState different = materials.get((material + 1) % materials.size());

            PieceBlock block = (PieceBlock) wall.getBlock();

            require(block.getStateContainer().getValidStates().size() == block.palette.materials.size() * 64,
                    "matching textures add no listed states");

            for (int connections = 0; connections < 16; connections++) {
                for (int matching = 0; matching < 16; matching++) {
                    if ((matching & connections) != matching) {
                        continue;
                    }

                    world.setBlockState(pos, wall, 2);
                    world.removeBlock(pos.up(), false);

                    for (int entryIndex = 0; entryIndex < sides.length; entryIndex++) {
                        if ((connections & (1 << entryIndex)) == 0) {
                            world.removeBlock(pos.offset(sides[entryIndex]), false);
                        } else {
                            world.setBlockState(pos.offset(sides[entryIndex]),
                                    (matching & (1 << entryIndex)) != 0 ? wall : different, 2);
                        }
                    }

                    BlockState actual = block.getActualState(wall, world, pos);
                    int joined = joins(world, pos, actual);

                    require(joined == matching, "only identical glass materials lose their border");
                    require(actual.get(PieceBlock.CONNECTIONS) == connections,
                            "colour changes do not disconnect the wall");
                    require(block.getMetaFromState(actual) == block.getMetaFromState(wall),
                            "render joins do not change the palette value");
                    require(block.getStateContainer().getValidStates().size() == block.palette.materials.size() * 64,
                            "render joins do not add listed save states");
                    checked++;
                }
            }
        }
        for (BlockState wall : materials) {
            for (BlockState neighbour : materials) {
                for (int side = 0; side < sides.length; side++) {
                    world.setBlockState(pos, wall, 2);

                    for (Direction facing : sides) {
                        world.removeBlock(pos.offset(facing), false);
                    }

                    world.setBlockState(pos.offset(sides[side]), neighbour, 2);

                    PieceBlock block = (PieceBlock) wall.getBlock();
                    PieceBlock other = (PieceBlock) neighbour.getBlock();

                    int joined = joins(world, pos, block.getActualState(wall, world, pos));
                    int expected = block.definition(wall).id.equals(other.definition(neighbour).id) ? 1 << side : 0;

                    require(joined == expected, "all clear/stained colour pairs keep the correct boundary");
                    checked++;
                }
            }
        }

        world.removeBlock(pos, false);

        for (Direction side : sides) {
            world.removeBlock(pos.offset(side), false);
        }

        require(checked == 2533, "every connection combination and clear/stained colour pair");
        System.out.println("BUILDING_PIECES_CONTINUOUS_GLASS_PASS cases=" + checked);
    }

    private static void torches(ServerWorld world) {
        BlockPos pos = new BlockPos(-524, 90, -524);
        Direction[] sides = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        ServerPlayerEntity player = FakePlayerFactory.getMinecraft(world);

        player.abilities.isCreativeMode = false;

        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape != Shape.WALL) {
                continue;
            }
            for (int material = 0; material < block.palette.materials.size(); material++) {
                for (int connections = 0; connections < 16; connections++) {
                    for (TorchBlock torch : new TorchBlock[] { (TorchBlock) Blocks.TORCH,
                            (TorchBlock) Blocks.REDSTONE_TORCH }) {
                        BlockState wall = block.getStateFromMeta(material);

                        world.setBlockState(pos, wall, 3);
                        world.removeBlock(pos.up(), false);

                        for (int entryIndex = 0; entryIndex < sides.length; entryIndex++) {
                            if ((connections & (1 << entryIndex)) == 0) {
                                world.removeBlock(pos.offset(sides[entryIndex]), false);
                            } else {
                                world.setBlockState(pos.offset(sides[entryIndex]), wall, 3);
                            }
                        }

                        require(torch.isValidPosition(torch.getDefaultState(), world, pos.up()),
                                "wall supports a top torch " + block.getRegistryName());

                        ItemStack stack = new ItemStack(Item.getItemFromBlock(torch), 2);

                        player.setHeldItem(Hand.MAIN_HAND, stack);
                        require(stack.onItemUse(new ItemUseContext(player, Hand.MAIN_HAND,
                                new BlockRayTraceResult(new Vec3d(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5),
                                        Direction.UP, pos, false))) == ActionResultType.SUCCESS,
                                "torch places on wall");

                        BlockState placed = world.getBlockState(pos.up());

                        require(placed.getBlock() == torch && stack.getCount() == 1, "upright torch consumes one item");
                        torch.neighborChanged(placed, world, pos.up(), block, pos, false);
                        require(world.getBlockState(pos.up()).getBlock() == torch, "torch survives a neighbour update");

                        if (wall.has(WallBlock.UP)) {
                            require(block.getActualState(wall, world, pos).get(WallBlock.UP),
                                    "torch creates its supporting post");
                        }

                        world.removeBlock(pos.up(), false);

                        if (wall.has(WallBlock.UP)) {
                            require(block.getActualState(wall, world, pos)
                                    .get(WallBlock.UP) == (connections != 5 && connections != 10),
                                    "removing torch restores the wall profile");
                        }

                        world.setBlockState(pos.up(), placed, 3);
                        world.removeBlock(pos, false);
                        require(world.isAirBlock(pos.up()), "torch drops when its wall is removed");
                        checked++;
                    }
                }
            }
        }

        world.removeBlock(pos, false);
        world.removeBlock(pos.up(), false);

        for (Direction side : sides) {
            world.removeBlock(pos.offset(side), false);
        }

        System.out.println("BUILDING_PIECES_WALL_TORCH_PASS cases=" + checked);
    }

    private static boolean visible(PieceBlock block, BlockState state, ServerWorld world, BlockPos position,
            Direction side) {
        return !block.isSideInvisible(block.getActualState(state, world, position),
                world.getBlockState(position.offset(side)), side);
    }

    private static BlockRayTraceResult trace(BlockState state, ServerWorld world, BlockPos position, Vec3d start,
            Vec3d end) {
        PieceBlock block = (PieceBlock) state.getBlock();

        return block.getActualState(state, world, position).getShape(world, position).rayTrace(start, end, position);
    }

    @SuppressWarnings("unchecked")
    private static int joins(ServerWorld world, BlockPos position, BlockState state) {
        try {
            Class<?> type = zone.moddev.mc.skysbuildingpieces.client.ContinuousGlassModel.class;
            java.lang.reflect.Field field = type.getDeclaredField("JOINS");

            field.setAccessible(true);

            net.minecraftforge.client.model.data.ModelProperty<Integer> property = (net.minecraftforge.client.model.data.ModelProperty<Integer>) field
                    .get(null);
            zone.moddev.mc.skysbuildingpieces.client.ContinuousGlassModel model = new zone.moddev.mc.skysbuildingpieces.client.ContinuousGlassModel(
                    null);
            net.minecraftforge.client.model.data.IModelData data = model.getModelData(world, position, state,
                    net.minecraftforge.client.model.data.EmptyModelData.INSTANCE);

            require(data.hasProperty(property), "glass joins are present in render-only model data");
            return data.getData(property);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect the native glass model data", failure);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void gallery(ServerWorld world) {
        world.setDayTime(6000);
        world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false, world.getServer());

        for (int blockX = 0; blockX <= 12; blockX++) {
            for (int blockZ = 0; blockZ <= 11; blockZ++) {
                world.setBlockState(new BlockPos(blockX, 63, blockZ), Blocks.GRASS_BLOCK.getDefaultState(), 2);
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
