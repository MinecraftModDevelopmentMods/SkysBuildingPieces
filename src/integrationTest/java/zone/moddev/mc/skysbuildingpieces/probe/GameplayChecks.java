package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.phys.Vec3;

import java.util.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Real world placement and collision checks, isolated from the saved palette grid. */
public final class GameplayChecks {
    private static final BlockPos FIXTURE_POSITION = new BlockPos(-500, 90, -500);

    public static void prepare(ServerLevel world) {
        world.setChunkForced(FIXTURE_POSITION.getX() >> 4, FIXTURE_POSITION.getZ() >> 4, true);
        world.getChunkAt(FIXTURE_POSITION);
    }

    public static boolean ready(ServerLevel world) {
        return world.isPositionTickingWithEntitiesLoaded(FIXTURE_POSITION);
    }

    public static void release(ServerLevel world) {
        world.setChunkForced(FIXTURE_POSITION.getX() >> 4, FIXTURE_POSITION.getZ() >> 4, false);
    }

    private static InteractionResult use(ItemStack stack, ServerPlayer player, ServerLevel world, BlockPos pos,
            InteractionHand hand, Direction face, float hitX, float hitY, float hitZ) {
        player.setItemInHand(hand, stack);
        return stack.useOn(new UseOnContext(player, hand, new BlockHitResult(
                new Vec3(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ), face, pos, false)));
    }

    public static void run(ServerLevel world) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);

        player.getAbilities().instabuild = false;

        BlockPos position = FIXTURE_POSITION;

        // Native lighting retains data for tracked chunks. Keep this distant fixture tracked while the tests exercise
        // it during a real server tick.
        world.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true);

        long trackingDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);

        long chunkPosition = new net.minecraft.world.level.ChunkPos(position).toLong();
        while (!world.getChunkSource().isPositionTicking(chunkPosition) && System.nanoTime() < trackingDeadline) {
            if (!world.getChunkSource().pollTask()) {
                Thread.yield();
            }
        }

        require(world.getChunkSource().isPositionTicking(chunkPosition), "Native gameplay fixture never became a ticking chunk");

        world.removeBlock(position.above(), false);
        world.removeBlock(position, false);

        int placed = 0;

        for (PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            ItemStack item = Pieces.stack(pieceBlock.getStateFromMeta(0), 2);

            world.removeBlock(position, false);
            player.setShiftKeyDown(false);

            InteractionResult result = use(item, player, world, position, InteractionHand.MAIN_HAND, Direction.UP, .25f, .1f, .25f);

            // Vanilla items consume the action on the server; Sky items retain their explicit success result.
            boolean nativeConsumption = result == InteractionResult.CONSUME && !(item.getItem() instanceof PieceItem);
            require((result == InteractionResult.SUCCESS || nativeConsumption) && item.getCount() == 1,
                    "own placement and consumption " + pieceBlock.getRegistryName() + " item="
                            + item.getItem().getRegistryName() + " result=" + result + " count=" + item.getCount()
                            + " placed=" + world.getBlockState(position));

            BlockState state = world.getBlockState(position);

            if (pieceBlock.palette.shape == Shape.VERTICAL_SLAB
                    && Pieces.state(pieceBlock.palette.materials.get(0), Shape.SLAB, 1) != null) {
                require(state.equals(Pieces.state(pieceBlock.palette.materials.get(0), Shape.SLAB, 1)),
                        "saved vertical item uses regular slab placement");
                state = pieceBlock.getStateFromMeta(0);
                world.setBlock(position, state, 2);
            } else {
                // Retired material items deliberately place their current equivalent, including another module's fixed
                // palette.
                BlockState equivalent = Pieces.state(pieceBlock.palette.materials.get(0), pieceBlock.palette.shape, 0);

                require(state.getBlock() == equivalent.getBlock(), "own placement uses the material's shape: "
                        + pieceBlock.getRegistryName() + " placed " + state + ", expected " + equivalent);
                state = pieceBlock.getStateFromMeta(0);
                world.setBlock(position, state, 2);
            }

            require(world.getBlockEntity(position) == null, "pieces never need tiles");

            List<AABB> boxes = pieceBlock.boxes(pieceBlock.getActualState(state, world, position));

            require(!boxes.isEmpty(), "collision boxes");

            Catalogue.MaterialDef definition = pieceBlock.definition(state);
            BlockState nativeFull = Pieces.nativeState(definition, "full");

            require(state.getDestroySpeed(world,
                    position) == (definition.nativeProperties()
                            ? nativeFull.getDestroySpeed(world, position)
                            : definition.hardness()),
                    "material hardness");
            require(pieceBlock.getSoundType(state, world, position, player) != null, "material sound");

            for (Direction face : Direction.values()) {
                require(!pieceBlock.skipRendering(state, Blocks.STONE.defaultBlockState(), face),
                        "partial face remains visible");
            }

            placed++;
        }

        // Two complementary glass slabs become glass, and consume just one item.
        world.setBlock(position, Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);

        ItemStack slab = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);

        require(use(slab, player, world, position, InteractionHand.MAIN_HAND, Direction.UP, .25f, .75f,
                .25f) == InteractionResult.SUCCESS, "matching slabs combine");
        require(world.getBlockState(position).getBlock() == Blocks.GLASS && slab.getCount() == 1,
                "normalized full block without duplication");
        world.setBlock(position, Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);
        slab = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);
        require(use(slab, player, world, position, InteractionHand.MAIN_HAND, Direction.DOWN, .25f, .5f,
                .25f) == InteractionResult.SUCCESS && world.getBlockState(position).getBlock() == Blocks.GLASS
                && slab.getCount() == 1, "top slab combines on its lower face");

        for (int orientation = 0; orientation < 4; orientation++) {
            world.setBlock(position, Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, orientation), 2);

            ItemStack vertical = Pieces.stack(Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, 0), 2);
            Direction face = new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST }[orientation];
            float hitX = face.getAxis() == Direction.Axis.X ? .5f : .25f;
            float hitZ = face.getAxis() == Direction.Axis.Z ? .5f : .25f;

            require(use(vertical, player, world, position, InteractionHand.MAIN_HAND, face, hitX, .25f,
                    hitZ) == InteractionResult.SUCCESS && world.getBlockState(position).getBlock() == Blocks.GLASS
                    && vertical.getCount() == 1, "vertical slab internal boundary " + orientation);
        }
        // Unlike materials must not replace an occupied piece.
        for (int orientation = 0; orientation < 4; orientation++) {
            BlockState dirt = Pieces.state("minecraft:dirt", Shape.VERTICAL_SLAB, orientation);

            world.setBlock(position, dirt, 2);
            world.setBlock(position.north().below(), Blocks.STONE.defaultBlockState(), 2);
            world.setBlock(position.north(), Blocks.SNOW.defaultBlockState(), 2);
            require(world.getBlockState(position.north()).getBlock() == Blocks.SNOW, "Supported snow fixture");
            require(((PieceBlock) dirt.getBlock()).getActualState(dirt, world, position).getValue(GrassBlock.SNOWY),
                    "vertical dirt snow cap " + orientation);
            world.removeBlock(position.north(), false);

            ItemStack vertical = Pieces.stack(dirt, 2);
            Direction face = new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST }[orientation];
            float hitX = face.getAxis() == Direction.Axis.X ? .5f : .25f;
            float hitZ = face.getAxis() == Direction.Axis.Z ? .5f : .25f;

            require(use(vertical, player, world, position, InteractionHand.MAIN_HAND, face, hitX, .25f,
                    hitZ) == InteractionResult.SUCCESS
                    && world.getBlockState(position).equals(Blocks.DIRT.defaultBlockState()) && vertical.getCount() == 1,
                    "vertical dirt full-block combination " + orientation);
        }

        BlockState original = Pieces.state("minecraft:glass", Shape.SLAB, 1);

        world.setBlock(position, original, 2);
        world.setBlock(position.above(), Blocks.STONE.defaultBlockState(), 2);

        ItemStack other = Pieces.stack(Pieces.state("minecraft:ice", Shape.SLAB, 0), 2);

        use(other, player, world, position, InteractionHand.MAIN_HAND, Direction.UP, .25f, .75f, .25f);
        require(world.getBlockState(position).equals(original) && other.getCount() == 2,
                "unlike combination rejected unchanged");
        slabPlacement(world, position, player);
        nativeSlabs(world, position, player);
        world.setBlock(position.above(), Blocks.STONE.defaultBlockState(), 2);
        settleLighting(world, position);
        require(world.getMaxLocalRawBrightness(position.above()) < 4,
                "Covered grass fixture is dark after native lighting finishes: light=" + world.getMaxLocalRawBrightness(position.above())
                        + " cover=" + world.getBlockState(position.above()));

        // Grass shapes decay to dirt without changing their octant geometry.
        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                BlockState grass = Pieces.state("minecraft:grass", shape, orientation);

                world.setBlock(position, grass, 2);
                grass.getBlock().tick(grass, world, position, new Random(0));
                require(world.getBlockState(position).equals(Pieces.state("minecraft:dirt", shape, orientation)),
                        "covered grass decay preserves geometry: " + shape + " / " + orientation + " became "
                                + world.getBlockState(position));
            }
        }

        world.removeBlock(position.above(), false);

        // This also runs without Grass Slabs installed: finer shapes must not expose a dirt patch.
        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                world.removeBlock(position, false);
                world.setBlock(position.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);

                BlockState grassPiece = Pieces.state("minecraft:grass", shape, orientation);

                world.setBlock(position, grassPiece, 2);
                GrassLifecycle.repairSupport(world, position, grassPiece);

                int cells = ((PieceBlock) grassPiece.getBlock()).mask(grassPiece);
                boolean fullFootprint = ((cells | cells >> 4) & 15) == 15;

                require(world.getBlockState(position.below())
                        .getBlock() == (fullFootprint ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                        "exposed grass support " + shape + " / " + orientation);
            }
        }

        world.setBlock(position.north().below(), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(position.north(), Blocks.SNOW.defaultBlockState(), 2);

        BlockState grass = Pieces.state("minecraft:grass", Shape.CORNER, 0);

        world.setBlock(position, grass, 2);
        require(((PieceBlock) grass.getBlock()).getActualState(grass, world, position).getValue(GrassBlock.SNOWY),
                "adjacent snow visual");
        world.removeBlock(position.north(), false);
        require(!((PieceBlock) grass.getBlock()).getActualState(grass, world, position).getValue(GrassBlock.SNOWY),
                "snow visual clears");
        world.removeBlock(position, false);
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_GAMEPLAY_PASS " + placed + " palettes");
        materialPhysics(world, position, player);
        world.setChunkForced(position.getX() >> 4, position.getZ() >> 4, false);
    }

    private static void settleLighting(ServerLevel world, BlockPos position) {
        // Lighting moved to a worker thread in 1.14. Finish that work before manually ticking grass; a tick immediately
        // after placement sees old light.
        net.minecraft.server.level.ThreadedLevelLightEngine lights = world.getChunkSource().getLightEngine();

        lights.checkBlock(position.above());

        java.util.concurrent.CompletableFuture<?> finished = lights.lightChunk(world.getChunkAt(position), true);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);

        // Light tickets complete through the server mailbox as well as the chunk
        // mailbox. Pump the native event loop while waiting for this exact pass.
        world.getServer().managedBlock(() -> {
            // Keep chunk tickets moving after the normal server loop has spent
            // this tick's budget. Lighting needs the chunk mailbox too.
            world.getChunkSource().pollTask();
            // Pre- and post-light tasks arrive asynchronously. A single schedule
            // call can run before either task has reached the worker's queue.
            lights.tryScheduleUpdate();
            return (finished.isDone() && world.getMaxLocalRawBrightness(position.above()) < 4)
                    || System.nanoTime() >= deadline;
        });

        require(finished.isDone() && world.getMaxLocalRawBrightness(position.above()) < 4,
                "Native lighting did not finish for the grass fixture: own_pass=" + finished.isDone()
                        + " light=" + world.getMaxLocalRawBrightness(position.above()));
        finished.join();
    }

    private static void slabPlacement(ServerLevel world, BlockPos position, ServerPlayer player) {
        int checked = 0;

        // Real BlockItem calls against a solid neighbour: centre plus four edges, all six faces, every custom
        // horizontal slab material and both sneak modes.
        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape == Shape.SLAB) {
                for (int slot = 0; slot < block.palette.materials.size(); slot++) {
                    for (Direction face : Direction.values()) {
                        for (int click = 0; click < 5; click++) {
                            for (boolean sneak : new boolean[] { false, true }) {
                                clearAround(world, position);
                                player.setShiftKeyDown(sneak);

                                BlockPos anchor = position.relative(face.getOpposite());

                                world.setBlock(anchor, Blocks.STONE.defaultBlockState(), 2);

                                float[] edge = { .5f, .1f, .9f, .5f, .5f };
                                float[] height = { .5f, .5f, .5f, .1f, .9f };

                                float hitX;
                                float hitY;
                                float hitZ;

                                Direction expected;

                                if (face.getAxis() == Direction.Axis.Y) {
                                    hitX = edge[click];
                                    hitZ = height[click];
                                    hitY = face == Direction.UP ? 1 : 0;
                                    expected = new Direction[] { face.getOpposite(), Direction.WEST, Direction.EAST,
                                            Direction.NORTH, Direction.SOUTH }[click];
                                } else if (face.getAxis() == Direction.Axis.X) {
                                    hitX = face == Direction.EAST ? 1 : 0;
                                    hitY = height[click];
                                    hitZ = edge[click];
                                    expected = new Direction[] { face.getOpposite(), Direction.NORTH, Direction.SOUTH,
                                            Direction.DOWN, Direction.UP }[click];
                                } else {
                                    hitX = edge[click];
                                    hitY = height[click];
                                    hitZ = face == Direction.SOUTH ? 1 : 0;
                                    expected = new Direction[] { face.getOpposite(), Direction.WEST, Direction.EAST,
                                            Direction.DOWN, Direction.UP }[click];
                                }

                                // Exercise this registered Sky item directly. Drops and recipes may reuse a native
                                // slab, whose placement belongs to BuildingBricks while that mod is installed.
                                ItemStack stack = ownSlab(block, block.palette.materials.get(slot));

                                require(use(stack, player, world, anchor, InteractionHand.MAIN_HAND, face, hitX, hitY,
                                        hitZ) == InteractionResult.SUCCESS,
                                        "slab face/edge placement " + face + " " + click);
                                require(world.getBlockState(position)
                                        .equals(Pieces.state(block.palette.materials.get(slot), slabShape(expected),
                                                slabOrientation(expected))),
                                        "slab selects correct half " + face + " " + click);
                                require(stack.getCount() == 1 && world.getBlockState(anchor).getBlock() == Blocks.STONE,
                                        "one item consumed; supporting block intact");
                                checked++;
                            }
                        }
                    }
                }
            }
        }

        player.setShiftKeyDown(false);

        // A horizontal-slab item also fills the missing half of a vertical slab.
        for (Shape held : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB }) {
            for (Direction occupied : Direction.values()) {
                clearAround(world, position);
                world.setBlock(position,
                        Pieces.state("minecraft:glass", slabShape(occupied), slabOrientation(occupied)), 2);

                ItemStack stack = Pieces.stack(Pieces.state("minecraft:glass", held, 0), 2);

                require(use(stack, player, world, position, InteractionHand.MAIN_HAND, occupied.getOpposite(), .5f, .5f,
                        .5f) == InteractionResult.SUCCESS, "cross-orientation slab combination");
                require(world.getBlockState(position).getBlock() == Blocks.GLASS && stack.getCount() == 1,
                        "six-way combination normalizes exactly once");
            }
        }

        // If the clicked full block cannot combine, use the neighbour's local coordinates when filling its top slab
        // from below.
        clearAround(world, position);
        world.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(position.above(), Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);

        ItemStack stack = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);

        require(use(stack, player, world, position, InteractionHand.MAIN_HAND, Direction.UP, .5f, 1,
                .5f) == InteractionResult.SUCCESS && world.getBlockState(position.above()).getBlock() == Blocks.GLASS
                && stack.getCount() == 1, "adjacent slab combination uses destination coordinates");
        clearAround(world, position);
        world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
        stack = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);
        player.getAbilities().mayBuild = false;

        try {
            require(use(stack, player, world, position.below(), InteractionHand.MAIN_HAND, Direction.UP, .5f, 1,
                    .5f) != InteractionResult.SUCCESS && world.isEmptyBlock(position) && stack.getCount() == 2,
                    "denied placement does not change the world or consume a slab");
        } finally {
            player.getAbilities().mayBuild = true;
        }

        net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(
                net.minecraft.world.entity.EntityType.ARMOR_STAND, world);

        stand.setPos(position.getX() + .25, position.getY(), position.getZ() + .5);
        require(world.addFreshEntity(stand), "collision fixture joins world");

        try {
            require(use(stack, player, world, position.below(), InteractionHand.MAIN_HAND, Direction.UP, .1f, 1,
                    .5f) == InteractionResult.FAIL && world.isEmptyBlock(position) && stack.getCount() == 2,
                    "occupied selected half rejects placement without consumption");
            stand.setPos(position.getX() + .75, position.getY(), position.getZ() + .5);
            require(use(stack, player, world, position.below(), InteractionHand.MAIN_HAND, Direction.UP, .1f, 1,
                    .5f) == InteractionResult.SUCCESS
                    && world.getBlockState(position).equals(Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, 1))
                    && stack.getCount() == 1, "empty selected half permits placement next to an entity");
        } finally {
            stand.discard();
            world.removeEntity(stand, false);
        }

        clearAround(world, position);
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_SLAB_PLACEMENT_PASS " + checked + " face/edge/sneak cases and 12 combinations");
    }

    private static Shape slabShape(Direction occupied) {
        return occupied.getAxis() == Direction.Axis.Y ? Shape.SLAB : Shape.VERTICAL_SLAB;
    }

    private static int slabOrientation(Direction occupied) {
        return occupied == Direction.UP ? 0 : occupied == Direction.DOWN ? 1 : occupied.get2DDataValue();
    }

    private static void clearAround(ServerLevel world, BlockPos position) {
        world.removeBlock(position, false);

        for (Direction face : Direction.values()) {
            world.removeBlock(position.relative(face), false);
        }
    }

    private static net.minecraft.world.inventory.CraftingContainer grid() {
        return new net.minecraft.world.inventory.CraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }
        }, 3, 3);
    }

    private static void verifyHarvesting(ServerLevel world, BlockPos position, ServerPlayer player,
            BlockState piece, BlockState full) {
        ItemStack held = player.getMainHandItem();
        try {
            for (Item tool : new Item[] {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE,
                    Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
                require(piece.canHarvestBlock(world, position, player) == full.canHarvestBlock(world, position, player),
                        "Material harvest requirements changed: " + piece + " with " + tool);
                float pieceHardness = piece.getDestroySpeed(world, position);
                float fullHardness = full.getDestroySpeed(world, position);
                float expected = full.getDestroyProgress(player, world, position);
                if (pieceHardness >= 0 && fullHardness > 0) {
                    expected = expected * fullHardness / pieceHardness;
                }
                float actual = piece.getDestroyProgress(player, world, position);
                require(Float.compare(actual, expected) == 0 || Math.abs(actual - expected) < 0.00001F,
                        "Material mining progress changed: " + piece + " with " + tool);
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
    }

    private static void materialPhysics(ServerLevel world, BlockPos position, ServerPlayer player) {
        for (PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            for (int slot = 0; slot < pieceBlock.palette.materials.size(); slot++) {
                for (int orientation = 0; orientation < pieceBlock.palette.shape.states; orientation++) {
                    BlockState state = pieceBlock
                            .getStateFromMeta(slot * pieceBlock.palette.shape.states + orientation);
                    Catalogue.MaterialDef materialDefinition = pieceBlock.definition(state);

                    world.setBlock(position, state, 2);
                    BlockState nativeFull = Pieces.nativeState(materialDefinition, "full");
                    verifyHarvesting(world, position, player, state, nativeFull);
                    int actualFire = pieceBlock.getFlammability(state, world, position, Direction.UP);
                    int actualSpread = pieceBlock.getFireSpreadSpeed(state, world, position, Direction.UP);

                    world.setBlock(position, nativeFull, 2);

                    int expectedFire = materialDefinition.flammability() >= 0 ? materialDefinition.flammability()
                            : nativeFull.getBlock().getFlammability(nativeFull, world, position, Direction.UP);
                    int expectedSpread = materialDefinition.fireSpread() >= 0 ? materialDefinition.fireSpread()
                            : nativeFull.getBlock().getFireSpreadSpeed(nativeFull, world, position, Direction.UP);

                    require(actualFire == expectedFire, "native flammability " + materialDefinition.id + " actual="
                            + actualFire + " expected=" + expectedFire);
                    require(actualSpread == expectedSpread, "native fire spread");
                    world.setBlock(position, state, 2);
                    require(ItemStack.matches(pieceBlock.getPickBlock(state, null, world, position, player),
                            expectedDrop(materialDefinition.id, pieceBlock, state)),
                            "pick identity follows regular slab");

                    ItemStack silkTool = new ItemStack(Items.DIAMOND_PICKAXE);

                    silkTool.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);

                    BlockState silkState = materialDefinition.type.equals("path")
                            ? Pieces.state("minecraft:dirt", pieceBlock.palette.shape, orientation)
                            : state;
                    List<ItemStack> silkDrops = Block.getDrops(state, world, position, null, player, silkTool);

                    require(silkDrops.size() == 1 && ItemStack.matches(silkDrops.get(0),
                            expectedDrop(materialDefinition.id, pieceBlock, silkState)),
                            "Silk Touch follows regular slab");

                    List<ItemStack> drops = Block.getDrops(state, world, position, null, player,
                            new ItemStack(Items.DIAMOND_PICKAXE));

                    if (materialDefinition.normalDrop().isEmpty()) {
                        require(drops.isEmpty(), "fragile material normal drops");
                    } else {
                        require(drops.size() == 1 && ItemStack.matches(drops.get(0),
                                expectedDrop(materialDefinition.normalDrop(), pieceBlock, Pieces.state(
                                        materialDefinition.normalDrop(), pieceBlock.palette.shape, orientation))),
                                "material drop and metadata");
                    }
                    if (materialDefinition.soil()) {
                        for (Block plant : new Block[] { Blocks.GRASS, Blocks.OAK_SAPLING, Blocks.RED_MUSHROOM }) {
                            BlockState full = Pieces.nativeState(materialDefinition, "full");
                            boolean nativeSupport = full.getBlock().canSustainPlant(full, world, position, Direction.UP,
                                    (net.minecraftforge.common.IPlantable) plant);

                            require(pieceBlock.canSustainPlant(state, world, position, Direction.UP,
                                    (net.minecraftforge.common.IPlantable) plant) == (nativeSupport
                                            && Geometry.completeTop(pieceBlock.mask(state))),
                                    "native plants need complete upper faces " + materialDefinition.id + " "
                                            + pieceBlock.orientation(state));
                        }
                    }
                }
            }
        }

        world.removeBlock(position, false);
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_MATERIAL_PHYSICS_PASS");
    }

    private static ItemStack expectedDrop(String material, PieceBlock block, BlockState state) {
        if (block.palette.shape == Shape.VERTICAL_SLAB) {
            BlockState horizontal = Pieces.state(material, Shape.SLAB, 1);

            if (horizontal != null) {
                return Pieces.stack(horizontal, 1);
            }

            Item dirt = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(new ResourceLocation("skysgrassslabs:dirt_slab"));

            if (material.equals("minecraft:dirt") && dirt != null && dirt != Items.AIR) {
                return new ItemStack(dirt, 1);
            }
        }

        if (state.getBlock() instanceof PieceBlock) {
            PieceBlock actual = (PieceBlock) state.getBlock();

            return Pieces.stack(
                    Pieces.state(actual.definition(state).id, actual.palette.shape, actual.orientation(state)), 1);
        }

        return Pieces.stack(state, 1);
    }

    private static void nativeSlabs(ServerLevel world, BlockPos position, ServerPlayer player) {
        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape == Shape.VERTICAL_SLAB) {
                NonNullList<ItemStack> creative = NonNullList.create();

                Pieces.stack(block.defaultBlockState(), 1).getItem().fillItemCategory(PiecesTab.INSTANCE, creative);
                require(creative.isEmpty(), "vertical slabs absent from creative tab");
            }
        }

        Item dirt = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(new ResourceLocation("skysgrassslabs:dirt_slab"));

        if (dirt != null && dirt != Items.AIR) {
            for (Direction occupied : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST }) {
                clearAround(world, position);
                world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);

                ItemStack stack = new ItemStack(dirt, 2);
                float hitX = occupied == Direction.WEST ? .1f : occupied == Direction.EAST ? .9f : .5f;
                float hitZ = occupied == Direction.NORTH ? .1f : occupied == Direction.SOUTH ? .9f : .5f;

                require(use(stack, player, world, position.below(), InteractionHand.MAIN_HAND, Direction.UP, hitX, 1,
                        hitZ) == InteractionResult.SUCCESS
                        && world.getBlockState(position).equals(
                                Pieces.state("minecraft:dirt", Shape.VERTICAL_SLAB, occupied.get2DDataValue()))
                        && stack.getCount() == 1, "regular dirt slab places vertically");
                ++checked;
            }
        }

        clearAround(world, position);
        world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);

        ItemStack oak = new ItemStack(Blocks.OAK_SLAB, 2);

        player.setItemInHand(InteractionHand.MAIN_HAND, oak);

        PlacementVeto veto = new PlacementVeto();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(veto);

        try {
            require(use(oak, player, world, position.below(), InteractionHand.MAIN_HAND, Direction.UP, .1f, 1,
                    .5f) != InteractionResult.SUCCESS && veto.seen && world.isEmptyBlock(position) && oak.getCount() == 2,
                    "Forge placement protection rolls back the exact native slab placement");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
        }

        clearAround(world, position);
        world.setBlock(position, Blocks.OAK_TRAPDOOR.defaultBlockState(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, oak);
        player.setShiftKeyDown(false);
        require(player.gameMode.useItemOn(player, world, oak, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(position.getX() + .1, position.getY() + 1, position.getZ() + .5),
                        Direction.UP, position, false)) == InteractionResult.CONSUME
                && world.isEmptyBlock(position.above()) && oak.getCount() == 2
                && world.getBlockState(position).getValue(TrapDoorBlock.OPEN),
                "block activation takes priority and happens exactly once");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        if (net.minecraftforge.fml.ModList.get().isLoaded("buildingbricks")) {
            BlockState unchanged = world.getBlockState(position);

            require(NativeSlabPlacement.place(oak, player, world, position, InteractionHand.MAIN_HAND, Direction.UP,
                    .1f, 1, .5f) == null && world.getBlockState(position).equals(unchanged) && oak.getCount() == 2,
                    "installed BuildingBricks retains native slab placement without a Sky write or item consumption");
            clearAround(world, position);
            org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_NATIVE_SLAB_POLICY_PASS installed=true hook=unchanged");
            return;
        }
        for (Catalogue.MaterialDef material : Catalogue.INSTANCE.materials.values()) {
            if (Pieces.state(material.id, Shape.VERTICAL_SLAB, 0) == null) {
                continue;
            }

            BlockState nativeSlab = Pieces.nativeState(material, "slab");

            if (nativeSlab == null) {
                continue;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                for (Direction occupied : Direction.values()) {
                    clearAround(world, position);

                    BlockPos anchor = position.below();

                    world.setBlock(anchor, Blocks.STONE.defaultBlockState(), 2);

                    float hitX = occupied == Direction.WEST ? .1f : occupied == Direction.EAST ? .9f : .5f;
                    float hitZ = occupied == Direction.NORTH ? .1f : occupied == Direction.SOUTH ? .9f : .5f;
                    Direction face = occupied == Direction.UP ? Direction.DOWN : Direction.UP;

                    if (face == Direction.DOWN) {
                        anchor = position.above();
                        world.setBlock(anchor, Blocks.STONE.defaultBlockState(), 2);
                    }

                    ItemStack stack = Pieces.stack(nativeSlab, 3);
                    net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();

                    tag.putString("custom_label", "Preserved slab");
                    stack.setTag(tag);
                    player.setItemInHand(hand, stack);
                    InteractionResult placement = use(stack, player, world, anchor, hand, face, hitX,
                            face == Direction.UP ? 1 : 0, hitZ);
                    require(placement.consumesAction(), "native slab placement consumes the action " + material.id
                            + " result=" + placement);
                    require(world.getBlockState(position)
                            .equals(Pieces.state(material.id, slabShape(occupied), slabOrientation(occupied))),
                            "native slab selects six orientations " + material.id);
                    require(stack.getCount() == 2 && tag.equals(stack.getTag()),
                            "native hook consumes once and preserves NBT");
                    require(use(stack, player, world, position, hand, occupied.getOpposite(), .5f, .5f,
                            .5f).consumesAction(),
                            "native slab combines " + material.id + " / " + hand + " / " + occupied);
                    require(world.getBlockState(position).equals(Pieces.nativeState(material, "full"))
                            && stack.getCount() == 1, "native combination makes exact full material");
                    ++checked;
                }
            }
        }

        clearAround(world, position);
        player.setShiftKeyDown(false);
        org.apache.logging.log4j.LogManager.getLogger().info("BUILDING_PIECES_NATIVE_SLABS_PASS cases=" + checked + " creative=hidden drops=regular");
    }

    private static ItemStack ownSlab(PieceBlock block, String material) {
        for (Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
            if (item instanceof PieceItem) {
                PieceItem piece = (PieceItem) item;

                if (piece.piece == block && piece.material.equals(material)) {
                    return new ItemStack(piece, 2);
                }
            }
        }

        throw new AssertionError("Missing registered slab item: " + block.getRegistryName() + " / " + material);
    }

    public static final class PlacementVeto {
        boolean seen;

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void place(net.minecraftforge.event.world.BlockEvent.EntityPlaceEvent event) {
            seen = true;
            event.setCanceled(true);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
