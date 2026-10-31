package zone.moddev.mc.skysbuildingpieces.probe;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import zone.moddev.mc.skysbuildingpieces.content.*;

/** Real world placement and collision checks, isolated from the saved palette grid. */
public final class GameplayChecks {
    private static ActionResultType use(ItemStack stack, ServerPlayerEntity player, ServerWorld world, BlockPos pos,
            Hand hand, Direction face, float hitX, float hitY, float hitZ) {
        player.setHeldItem(hand, stack);
        return stack.onItemUse(new ItemUseContext(player, hand, new BlockRayTraceResult(
                new Vec3d(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ), face, pos, false)));
    }

    public static void run(ServerWorld world) {
        ServerPlayerEntity player = FakePlayerFactory.getMinecraft(world);

        player.abilities.isCreativeMode = false;

        BlockPos position = new BlockPos(-500, 90, -500);

        // Native lighting retains data for tracked chunks. Keep this distant fixture tracked while the tests exercise
        // it during a real server tick.
        world.forceChunk(position.getX() >> 4, position.getZ() >> 4, true);

        long trackingDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);

        while (!world.getChunkProvider().canTick(position) && System.nanoTime() < trackingDeadline) {
            if (!world.getChunkProvider().func_217234_d()) {
                Thread.yield();
            }
        }

        require(world.getChunkProvider().canTick(position), "Native gameplay fixture never became a ticking chunk");

        world.removeBlock(position.up(), false);
        world.removeBlock(position, false);

        int placed = 0;

        for (PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            ItemStack item = Pieces.stack(pieceBlock.getStateFromMeta(0), 2);

            world.removeBlock(position, false);
            player.setSneaking(false);

            ActionResultType result = use(item, player, world, position, Hand.MAIN_HAND, Direction.UP, .25f, .1f, .25f);

            require(result == ActionResultType.SUCCESS && item.getCount() == 1,
                    "own placement and consumption " + pieceBlock.getRegistryName());

            BlockState state = world.getBlockState(position);

            if (pieceBlock.palette.shape == Shape.VERTICAL_SLAB
                    && Pieces.state(pieceBlock.palette.materials.get(0), Shape.SLAB, 1) != null) {
                require(state.equals(Pieces.state(pieceBlock.palette.materials.get(0), Shape.SLAB, 1)),
                        "saved vertical item uses regular slab placement");
                state = pieceBlock.getStateFromMeta(0);
                world.setBlockState(position, state, 2);
            } else {
                // Retired material items deliberately place their current equivalent, including another module's fixed
                // palette.
                BlockState equivalent = Pieces.state(pieceBlock.palette.materials.get(0), pieceBlock.palette.shape, 0);

                require(state.getBlock() == equivalent.getBlock(), "own placement uses the material's shape: "
                        + pieceBlock.getRegistryName() + " placed " + state + ", expected " + equivalent);
                state = pieceBlock.getStateFromMeta(0);
                world.setBlockState(position, state, 2);
            }

            require(world.getTileEntity(position) == null, "pieces never need tiles");

            List<AxisAlignedBB> boxes = pieceBlock.boxes(pieceBlock.getActualState(state, world, position));

            require(!boxes.isEmpty(), "collision boxes");

            Catalogue.MaterialDef definition = pieceBlock.definition(state);
            BlockState nativeFull = Pieces.nativeState(definition, "full");

            require(pieceBlock.getBlockHardness(state, world,
                    position) == (definition.nativeProperties()
                            ? nativeFull.getBlock().getBlockHardness(nativeFull, world, position)
                            : definition.hardness()),
                    "material hardness");
            require(pieceBlock.getSoundType(state, world, position, player) != null, "material sound");

            for (Direction face : Direction.values()) {
                require(!pieceBlock.isSideInvisible(state, Blocks.STONE.getDefaultState(), face),
                        "partial face remains visible");
            }

            placed++;
        }

        // Two complementary glass slabs become glass, and consume just one item.
        world.setBlockState(position, Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);

        ItemStack slab = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);

        require(use(slab, player, world, position, Hand.MAIN_HAND, Direction.UP, .25f, .75f,
                .25f) == ActionResultType.SUCCESS, "matching slabs combine");
        require(world.getBlockState(position).getBlock() == Blocks.GLASS && slab.getCount() == 1,
                "normalized full block without duplication");
        world.setBlockState(position, Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);
        slab = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);
        require(use(slab, player, world, position, Hand.MAIN_HAND, Direction.DOWN, .25f, .5f,
                .25f) == ActionResultType.SUCCESS && world.getBlockState(position).getBlock() == Blocks.GLASS
                && slab.getCount() == 1, "top slab combines on its lower face");

        for (int orientation = 0; orientation < 4; orientation++) {
            world.setBlockState(position, Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, orientation), 2);

            ItemStack vertical = Pieces.stack(Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, 0), 2);
            Direction face = new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST }[orientation];
            float hitX = face.getAxis() == Direction.Axis.X ? .5f : .25f;
            float hitZ = face.getAxis() == Direction.Axis.Z ? .5f : .25f;

            require(use(vertical, player, world, position, Hand.MAIN_HAND, face, hitX, .25f,
                    hitZ) == ActionResultType.SUCCESS && world.getBlockState(position).getBlock() == Blocks.GLASS
                    && vertical.getCount() == 1, "vertical slab internal boundary " + orientation);
        }
        // Unlike materials must not replace an occupied piece.
        for (int orientation = 0; orientation < 4; orientation++) {
            BlockState dirt = Pieces.state("minecraft:dirt", Shape.VERTICAL_SLAB, orientation);

            world.setBlockState(position, dirt, 2);
            world.setBlockState(position.north().down(), Blocks.STONE.getDefaultState(), 2);
            world.setBlockState(position.north(), Blocks.SNOW.getDefaultState(), 2);
            require(world.getBlockState(position.north()).getBlock() == Blocks.SNOW, "Supported snow fixture");
            require(((PieceBlock) dirt.getBlock()).getActualState(dirt, world, position).get(GrassBlock.SNOWY),
                    "vertical dirt snow cap " + orientation);
            world.removeBlock(position.north(), false);

            ItemStack vertical = Pieces.stack(dirt, 2);
            Direction face = new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST }[orientation];
            float hitX = face.getAxis() == Direction.Axis.X ? .5f : .25f;
            float hitZ = face.getAxis() == Direction.Axis.Z ? .5f : .25f;

            require(use(vertical, player, world, position, Hand.MAIN_HAND, face, hitX, .25f,
                    hitZ) == ActionResultType.SUCCESS
                    && world.getBlockState(position).equals(Blocks.DIRT.getDefaultState()) && vertical.getCount() == 1,
                    "vertical dirt full-block combination " + orientation);
        }

        BlockState original = Pieces.state("minecraft:glass", Shape.SLAB, 1);

        world.setBlockState(position, original, 2);
        world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 2);

        ItemStack other = Pieces.stack(Pieces.state("minecraft:ice", Shape.SLAB, 0), 2);

        use(other, player, world, position, Hand.MAIN_HAND, Direction.UP, .25f, .75f, .25f);
        require(world.getBlockState(position).equals(original) && other.getCount() == 2,
                "unlike combination rejected unchanged");
        slabPlacement(world, position, player);
        nativeSlabs(world, position, player);
        world.setBlockState(position.up(), Blocks.STONE.getDefaultState(), 2);
        settleLighting(world, position);
        require(world.getLight(position.up()) < 4,
                "Covered grass fixture is dark after native lighting finishes: light=" + world.getLight(position.up())
                        + " cover=" + world.getBlockState(position.up()));

        // Grass shapes decay to dirt without changing their octant geometry.
        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                BlockState grass = Pieces.state("minecraft:grass", shape, orientation);

                world.setBlockState(position, grass, 2);
                grass.getBlock().tick(grass, world, position, new Random(0));
                require(world.getBlockState(position).equals(Pieces.state("minecraft:dirt", shape, orientation)),
                        "covered grass decay preserves geometry: " + shape + " / " + orientation + " became "
                                + world.getBlockState(position));
            }
        }

        world.removeBlock(position.up(), false);

        // This also runs without Grass Slabs installed: finer shapes must not expose a dirt patch.
        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS }) {
            for (int orientation = 0; orientation < shape.states; orientation++) {
                world.removeBlock(position, false);
                world.setBlockState(position.down(), Blocks.GRASS_BLOCK.getDefaultState(), 2);

                BlockState grassPiece = Pieces.state("minecraft:grass", shape, orientation);

                world.setBlockState(position, grassPiece, 2);
                GrassLifecycle.repairSupport(world, position, grassPiece);

                int cells = ((PieceBlock) grassPiece.getBlock()).mask(grassPiece);
                boolean fullFootprint = ((cells | cells >> 4) & 15) == 15;

                require(world.getBlockState(position.down())
                        .getBlock() == (fullFootprint ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                        "exposed grass support " + shape + " / " + orientation);
            }
        }

        world.setBlockState(position.north().down(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(position.north(), Blocks.SNOW.getDefaultState(), 2);

        BlockState grass = Pieces.state("minecraft:grass", Shape.CORNER, 0);

        world.setBlockState(position, grass, 2);
        require(((PieceBlock) grass.getBlock()).getActualState(grass, world, position).get(GrassBlock.SNOWY),
                "adjacent snow visual");
        world.removeBlock(position.north(), false);
        require(!((PieceBlock) grass.getBlock()).getActualState(grass, world, position).get(GrassBlock.SNOWY),
                "snow visual clears");
        world.removeBlock(position, false);
        System.out.println("BUILDING_PIECES_GAMEPLAY_PASS " + placed + " palettes");
        materialPhysics(world, position, player);
        world.forceChunk(position.getX() >> 4, position.getZ() >> 4, false);
    }

    private static void settleLighting(ServerWorld world, BlockPos position) {
        // Lighting moved to a worker thread in 1.14. Finish that work before manually ticking grass; a tick immediately
        // after placement sees old light.
        net.minecraft.world.server.ServerWorldLightManager lights = world.getChunkProvider().getLightManager();

        lights.checkBlock(position.up());

        java.util.concurrent.CompletableFuture<?> finished = lights.lightChunk(world.getChunkAt(position), true);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);

        // checkBlock is queued after the light pass. Its propagation can still be pending when lightChunk's completion
        // callback runs.
        while ((!finished.isDone() || lights.func_215570_a()) && System.nanoTime() < deadline) {
            lights.func_215588_z_();
            Thread.yield();
        }

        require(finished.isDone() && !lights.func_215570_a(), "Native lighting did not finish for the grass fixture");
        finished.join();
    }

    private static void slabPlacement(ServerWorld world, BlockPos position, ServerPlayerEntity player) {
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
                                player.setSneaking(sneak);

                                BlockPos anchor = position.offset(face.getOpposite());

                                world.setBlockState(anchor, Blocks.STONE.getDefaultState(), 2);

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

                                require(use(stack, player, world, anchor, Hand.MAIN_HAND, face, hitX, hitY,
                                        hitZ) == ActionResultType.SUCCESS,
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

        player.setSneaking(false);

        // A horizontal-slab item also fills the missing half of a vertical slab.
        for (Shape held : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB }) {
            for (Direction occupied : Direction.values()) {
                clearAround(world, position);
                world.setBlockState(position,
                        Pieces.state("minecraft:glass", slabShape(occupied), slabOrientation(occupied)), 2);

                ItemStack stack = Pieces.stack(Pieces.state("minecraft:glass", held, 0), 2);

                require(use(stack, player, world, position, Hand.MAIN_HAND, occupied.getOpposite(), .5f, .5f,
                        .5f) == ActionResultType.SUCCESS, "cross-orientation slab combination");
                require(world.getBlockState(position).getBlock() == Blocks.GLASS && stack.getCount() == 1,
                        "six-way combination normalizes exactly once");
            }
        }

        // If the clicked full block cannot combine, use the neighbour's local coordinates when filling its top slab
        // from below.
        clearAround(world, position);
        world.setBlockState(position, Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(position.up(), Pieces.state("minecraft:glass", Shape.SLAB, 0), 2);

        ItemStack stack = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);

        require(use(stack, player, world, position, Hand.MAIN_HAND, Direction.UP, .5f, 1,
                .5f) == ActionResultType.SUCCESS && world.getBlockState(position.up()).getBlock() == Blocks.GLASS
                && stack.getCount() == 1, "adjacent slab combination uses destination coordinates");
        clearAround(world, position);
        world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);
        stack = Pieces.stack(Pieces.state("minecraft:glass", Shape.SLAB, 1), 2);
        player.abilities.allowEdit = false;

        try {
            require(use(stack, player, world, position.down(), Hand.MAIN_HAND, Direction.UP, .5f, 1,
                    .5f) != ActionResultType.SUCCESS && world.isAirBlock(position) && stack.getCount() == 2,
                    "denied placement does not change the world or consume a slab");
        } finally {
            player.abilities.allowEdit = true;
        }

        net.minecraft.entity.item.ArmorStandEntity stand = new net.minecraft.entity.item.ArmorStandEntity(
                net.minecraft.entity.EntityType.ARMOR_STAND, world);

        stand.setPosition(position.getX() + .25, position.getY(), position.getZ() + .5);
        require(world.addEntity(stand), "collision fixture joins world");

        try {
            require(use(stack, player, world, position.down(), Hand.MAIN_HAND, Direction.UP, .1f, 1,
                    .5f) == ActionResultType.FAIL && world.isAirBlock(position) && stack.getCount() == 2,
                    "occupied selected half rejects placement without consumption");
            stand.setPosition(position.getX() + .75, position.getY(), position.getZ() + .5);
            require(use(stack, player, world, position.down(), Hand.MAIN_HAND, Direction.UP, .1f, 1,
                    .5f) == ActionResultType.SUCCESS
                    && world.getBlockState(position).equals(Pieces.state("minecraft:glass", Shape.VERTICAL_SLAB, 1))
                    && stack.getCount() == 1, "empty selected half permits placement next to an entity");
        } finally {
            stand.remove();
            world.removeEntity(stand);
        }

        clearAround(world, position);
        System.out.println(
                "BUILDING_PIECES_SLAB_PLACEMENT_PASS " + checked + " face/edge/sneak cases and 12 combinations");
    }

    private static Shape slabShape(Direction occupied) {
        return occupied.getAxis() == Direction.Axis.Y ? Shape.SLAB : Shape.VERTICAL_SLAB;
    }

    private static int slabOrientation(Direction occupied) {
        return occupied == Direction.UP ? 0 : occupied == Direction.DOWN ? 1 : occupied.getHorizontalIndex();
    }

    private static void clearAround(ServerWorld world, BlockPos position) {
        world.removeBlock(position, false);

        for (Direction face : Direction.values()) {
            world.removeBlock(position.offset(face), false);
        }
    }

    private static net.minecraft.inventory.CraftingInventory grid() {
        return new net.minecraft.inventory.CraftingInventory(new net.minecraft.inventory.container.Container(null, 0) {
            public boolean canInteractWith(net.minecraft.entity.player.PlayerEntity player) {
                return true;
            }
        }, 3, 3);
    }

    private static void materialPhysics(ServerWorld world, BlockPos position, ServerPlayerEntity player) {
        for (PieceBlock pieceBlock : Pieces.BLOCKS.values()) {
            for (int slot = 0; slot < pieceBlock.palette.materials.size(); slot++) {
                for (int orientation = 0; orientation < pieceBlock.palette.shape.states; orientation++) {
                    BlockState state = pieceBlock
                            .getStateFromMeta(slot * pieceBlock.palette.shape.states + orientation);
                    Catalogue.MaterialDef materialDefinition = pieceBlock.definition(state);

                    world.setBlockState(position, state, 2);
                    require(pieceBlock.getHarvestTool(state) == null || pieceBlock.getHarvestTool(state)
                            .equals(materialDefinition.type.equals("rock") || materialDefinition.type.equals("metal")
                                    ? net.minecraftforge.common.ToolType.PICKAXE
                                    : materialDefinition.type.equals("wood") ? net.minecraftforge.common.ToolType.AXE
                                            : net.minecraftforge.common.ToolType.SHOVEL),
                            "harvest class");

                    BlockState nativeFull = Pieces.nativeState(materialDefinition, "full");
                    int actualFire = pieceBlock.getFlammability(state, world, position, Direction.UP);
                    int actualSpread = pieceBlock.getFireSpreadSpeed(state, world, position, Direction.UP);

                    world.setBlockState(position, nativeFull, 2);

                    int expectedFire = materialDefinition.flammability() >= 0 ? materialDefinition.flammability()
                            : nativeFull.getBlock().getFlammability(nativeFull, world, position, Direction.UP);
                    int expectedSpread = materialDefinition.fireSpread() >= 0 ? materialDefinition.fireSpread()
                            : nativeFull.getBlock().getFireSpreadSpeed(nativeFull, world, position, Direction.UP);

                    require(actualFire == expectedFire, "native flammability " + materialDefinition.id + " actual="
                            + actualFire + " expected=" + expectedFire);
                    require(actualSpread == expectedSpread, "native fire spread");
                    world.setBlockState(position, state, 2);
                    require(ItemStack.areItemStacksEqual(pieceBlock.getPickBlock(state, null, world, position, player),
                            expectedDrop(materialDefinition.id, pieceBlock, state)),
                            "pick identity follows regular slab");

                    ItemStack silkTool = new ItemStack(Items.DIAMOND_PICKAXE);

                    silkTool.addEnchantment(net.minecraft.enchantment.Enchantments.SILK_TOUCH, 1);

                    BlockState silkState = materialDefinition.type.equals("path")
                            ? Pieces.state("minecraft:dirt", pieceBlock.palette.shape, orientation)
                            : state;
                    List<ItemStack> silkDrops = Block.getDrops(state, world, position, null, player, silkTool);

                    require(silkDrops.size() == 1 && ItemStack.areItemStacksEqual(silkDrops.get(0),
                            expectedDrop(materialDefinition.id, pieceBlock, silkState)),
                            "Silk Touch follows regular slab");

                    List<ItemStack> drops = Block.getDrops(state, world, position, null, player,
                            new ItemStack(Items.DIAMOND_PICKAXE));

                    if (materialDefinition.normalDrop().isEmpty()) {
                        require(drops.isEmpty(), "fragile material normal drops");
                    } else {
                        require(drops.size() == 1 && ItemStack.areItemStacksEqual(drops.get(0),
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
        System.out.println("BUILDING_PIECES_MATERIAL_PHYSICS_PASS");
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

    private static void nativeSlabs(ServerWorld world, BlockPos position, ServerPlayerEntity player) {
        int checked = 0;

        for (PieceBlock block : Pieces.BLOCKS.values()) {
            if (block.palette.shape == Shape.VERTICAL_SLAB) {
                NonNullList<ItemStack> creative = NonNullList.create();

                Pieces.stack(block.getDefaultState(), 1).getItem().fillItemGroup(PiecesTab.INSTANCE, creative);
                require(creative.isEmpty(), "vertical slabs absent from creative tab");
            }
        }

        Item dirt = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(new ResourceLocation("skysgrassslabs:dirt_slab"));

        if (dirt != null && dirt != Items.AIR) {
            for (Direction occupied : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST }) {
                clearAround(world, position);
                world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);

                ItemStack stack = new ItemStack(dirt, 2);
                float hitX = occupied == Direction.WEST ? .1f : occupied == Direction.EAST ? .9f : .5f;
                float hitZ = occupied == Direction.NORTH ? .1f : occupied == Direction.SOUTH ? .9f : .5f;

                require(use(stack, player, world, position.down(), Hand.MAIN_HAND, Direction.UP, hitX, 1,
                        hitZ) == ActionResultType.SUCCESS
                        && world.getBlockState(position).equals(
                                Pieces.state("minecraft:dirt", Shape.VERTICAL_SLAB, occupied.getHorizontalIndex()))
                        && stack.getCount() == 1, "regular dirt slab places vertically");
                ++checked;
            }
        }

        clearAround(world, position);
        world.setBlockState(position.down(), Blocks.STONE.getDefaultState(), 2);

        ItemStack oak = new ItemStack(Blocks.OAK_SLAB, 2);

        player.setHeldItem(Hand.MAIN_HAND, oak);

        PlacementVeto veto = new PlacementVeto();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(veto);

        try {
            require(use(oak, player, world, position.down(), Hand.MAIN_HAND, Direction.UP, .1f, 1,
                    .5f) != ActionResultType.SUCCESS && veto.seen && world.isAirBlock(position) && oak.getCount() == 2,
                    "Forge placement protection rolls back the exact native slab placement");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
        }

        clearAround(world, position);
        world.setBlockState(position, Blocks.OAK_TRAPDOOR.getDefaultState(), 2);
        player.setHeldItem(Hand.MAIN_HAND, oak);
        player.setSneaking(false);
        require(player.interactionManager.func_219441_a(player, world, oak, Hand.MAIN_HAND,
                new BlockRayTraceResult(new Vec3d(position.getX() + .1, position.getY() + 1, position.getZ() + .5),
                        Direction.UP, position, false)) == ActionResultType.SUCCESS
                && world.isAirBlock(position.up()) && oak.getCount() == 2
                && world.getBlockState(position).get(TrapDoorBlock.OPEN),
                "block activation takes priority and happens exactly once");
        player.setHeldItem(Hand.MAIN_HAND, ItemStack.EMPTY);

        if (net.minecraftforge.fml.ModList.get().isLoaded("buildingbricks")) {
            BlockState unchanged = world.getBlockState(position);

            require(NativeSlabPlacement.place(oak, player, world, position, Hand.MAIN_HAND, Direction.UP,
                    .1f, 1, .5f) == null && world.getBlockState(position).equals(unchanged) && oak.getCount() == 2,
                    "installed BuildingBricks retains native slab placement without a Sky write or item consumption");
            clearAround(world, position);
            System.out.println("BUILDING_PIECES_NATIVE_SLAB_POLICY_PASS installed=true hook=unchanged");
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
            for (Hand hand : Hand.values()) {
                for (Direction occupied : Direction.values()) {
                    clearAround(world, position);

                    BlockPos anchor = position.down();

                    world.setBlockState(anchor, Blocks.STONE.getDefaultState(), 2);

                    float hitX = occupied == Direction.WEST ? .1f : occupied == Direction.EAST ? .9f : .5f;
                    float hitZ = occupied == Direction.NORTH ? .1f : occupied == Direction.SOUTH ? .9f : .5f;
                    Direction face = occupied == Direction.UP ? Direction.DOWN : Direction.UP;

                    if (face == Direction.DOWN) {
                        anchor = position.up();
                        world.setBlockState(anchor, Blocks.STONE.getDefaultState(), 2);
                    }

                    ItemStack stack = Pieces.stack(nativeSlab, 3);
                    net.minecraft.nbt.CompoundNBT tag = new net.minecraft.nbt.CompoundNBT();

                    tag.putString("custom_label", "Preserved slab");
                    stack.setTag(tag);
                    player.setHeldItem(hand, stack);
                    require(use(stack, player, world, anchor, hand, face, hitX, face == Direction.UP ? 1 : 0,
                            hitZ) == ActionResultType.SUCCESS, "native slab hook returns success " + material.id);
                    require(world.getBlockState(position)
                            .equals(Pieces.state(material.id, slabShape(occupied), slabOrientation(occupied))),
                            "native slab selects six orientations " + material.id);
                    require(stack.getCount() == 2 && tag.equals(stack.getTag()),
                            "native hook consumes once and preserves NBT");
                    require(use(stack, player, world, position, hand, occupied.getOpposite(), .5f, .5f,
                            .5f) == ActionResultType.SUCCESS,
                            "native slab combines " + material.id + " / " + hand + " / " + occupied);
                    require(world.getBlockState(position).equals(Pieces.nativeState(material, "full"))
                            && stack.getCount() == 1, "native combination makes exact full material");
                    ++checked;
                }
            }
        }

        clearAround(world, position);
        player.setSneaking(false);
        System.out.println("BUILDING_PIECES_NATIVE_SLABS_PASS cases=" + checked + " creative=hidden drops=regular");
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
