package zone.moddev.mc.skysbuildingpieces.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.IGrowable;
import net.minecraft.block.IWaterLoggable;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowyDirtBlock;
import net.minecraft.block.SoundType;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.Half;
import net.minecraft.state.properties.StairsShape;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.ToolType;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Permanent palettes with native states and collision shapes; no material tile entities. */
public final class PieceBlock extends Block implements IGrowable, IWaterLoggable {
    public static final IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 15);
    public static final BooleanProperty LOWERED_SUPPORT = BooleanProperty.create("lowered_support");
    public static final EnumProperty<StairsShape> STAIR_SHAPE = StairsBlock.SHAPE;
    private static final ThreadLocal<Construction> CONSTRUCTING = new ThreadLocal<>();
    private static final Material PATH_MATERIAL = new Material.Builder(MaterialColor.DIRT).nonSolid().build();
    public final Catalogue.Palette palette;
    private final IntegerProperty metadataProperty;
    private final Map<Integer, VoxelShape> outlines = new ConcurrentHashMap<>();
    private final Map<Integer, VoxelShape> collisions = new ConcurrentHashMap<>();

    private static final class Construction {
        final Catalogue.Palette palette;
        final IntegerProperty metadata;

        Construction(Catalogue.Palette palette) {
            this.palette = palette;

            int assigned = palette.materials.size() * palette.shape.states;

            // A single-material wall or pane needs no material selector. Native integer properties require at least two
            // distinct values.
            metadata = assigned == 1 ? null : IntegerProperty.create("meta", 0, assigned - 1);
        }
    }

    public PieceBlock(Catalogue.Palette palette) {
        super(properties(palette));

        Construction construction = CONSTRUCTING.get();

        this.palette = palette;
        metadataProperty = construction.metadata;
        CONSTRUCTING.remove();
        setRegistryName(palette.namespace, palette.id);

        BlockState initial = stateDefinition.any().setValue(SlabBlock.WATERLOGGED, false);

        if (metadataProperty != null) {
            initial = initial.setValue(metadataProperty, 0);
        }
        if (initial.hasProperty(SnowyDirtBlock.SNOWY)) {
            initial = initial.setValue(SnowyDirtBlock.SNOWY, false);
        }
        if (initial.hasProperty(CONNECTIONS)) {
            initial = initial.setValue(CONNECTIONS, 0);
        }
        if (initial.hasProperty(WallBlock.UP)) {
            initial = initial.setValue(WallBlock.UP, true);
        }
        if (initial.hasProperty(STAIR_SHAPE)) {
            initial = initial.setValue(STAIR_SHAPE, StairsShape.STRAIGHT);
        }
        if (initial.hasProperty(LOWERED_SUPPORT)) {
            initial = initial.setValue(LOWERED_SUPPORT, false);
        }

        registerDefaultState(initial);
    }

    private static Block.Properties properties(Catalogue.Palette palette) {
        CONSTRUCTING.set(new Construction(palette));

        Material material;

        switch (palette.group) {
            case "metal":
                material = Material.METAL;
                break;
            case "wood":
                material = Material.WOOD;
                break;
            case "path":
                material = PATH_MATERIAL;
                break;
            case "dirt":
                material = Material.DIRT;
                break;
            case "grass":
                material = Material.GRASS;
                break;
            case "cloth":
                material = Material.WOOL;
                break;
            case "clear_glass":
            case "stained_glass":
                material = Material.GLASS;
                break;
            case "ice":
                material = Material.ICE;
                break;
            case "packed_ice":
                material = Material.ICE_SOLID;
                break;
            case "snow":
                material = Material.SNOW;
                break;
            default:
                material = Material.STONE;
        }

        Block.Properties properties = Block.Properties.of(material).strength(1).sound(SoundType.STONE)
                .dynamicShape().noOcclusion();

        if (palette.group.equals("ice") || palette.group.equals("packed_ice")) {
            properties.friction(.98F);
        }

        return palette.group.equals("grass") || palette.group.equals("dirt") || palette.group.equals("path")
                ? properties.randomTicks()
                : properties;
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        Construction construction = CONSTRUCTING.get();

        if (construction == null) {
            throw new IllegalStateException("Missing palette construction context");
        }

        Catalogue.Palette palette = construction.palette;

        if (construction.metadata != null) {
            builder.add(construction.metadata);
        }

        builder.add(SlabBlock.WATERLOGGED);

        if (palette.group.equals("grass") || palette.group.equals("dirt")) {
            builder.add(SnowyDirtBlock.SNOWY);
        }
        if (palette.group.equals("path")) {
            builder.add(LOWERED_SUPPORT);
        }
        if (palette.shape == Shape.WALL || palette.shape == Shape.PANE) {
            builder.add(CONNECTIONS);
        }
        if (palette.shape == Shape.WALL) {
            builder.add(WallBlock.UP);
        }
        if (palette.shape == Shape.STAIRS) {
            builder.add(STAIR_SHAPE);
        }
    }

    public BlockState getStateFromMeta(int metadata) {
        palette.material(metadata);

        if (metadata < 0 || metadata >= palette.materials.size() * palette.shape.states) {
            throw new IllegalArgumentException("Unassigned palette metadata: " + palette.registryId() + ":" + metadata);
        }

        return metadataProperty == null ? defaultBlockState() : defaultBlockState().setValue(metadataProperty, metadata);
    }

    public int getMetaFromState(BlockState state) {
        return metadataProperty == null ? 0 : state.getValue(metadataProperty);
    }

    public Catalogue.MaterialDef definition(BlockState state) {
        return Catalogue.INSTANCE.materials.get(palette.material(getMetaFromState(state)));
    }

    public int orientation(BlockState state) {
        return getMetaFromState(state) % palette.shape.states;
    }

    public int canonicalMeta(BlockState state) {
        return getMetaFromState(state) / palette.shape.states * palette.shape.states;
    }

    public int mask(BlockState state) {
        return palette.shape == Shape.STAIRS ? Geometry.stairs(orientation(state), state.getValue(STAIR_SHAPE).getSerializedName())
                : Geometry.mask(palette.shape, orientation(state));
    }

    @Override
    public int getLightBlock(BlockState state, IBlockReader world, BlockPos position) {
        return 0;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState neighbour, Direction side) {
        if (palette.shape != Shape.WALL || side.getAxis() == Direction.Axis.Y
                || !(neighbour.getBlock() instanceof PieceBlock)) {
            return false;
        }

        PieceBlock other = (PieceBlock) neighbour.getBlock();

        return other.palette.shape == Shape.WALL && other.definition(neighbour).id.equals(definition(state).id);
    }

    public float materialDestroySpeed(BlockState state, IBlockReader world, BlockPos position) {
        Catalogue.MaterialDef material = definition(state);

        return material.nativeProperties() ? Pieces.nativeState(material, "full").getDestroySpeed(world, position)
                : material.hardness();
    }

    @Override
    public float getExplosionResistance(BlockState state, IBlockReader world, BlockPos position, Explosion explosion) {
        Catalogue.MaterialDef material = definition(state);

        if (material.explosionResistance() >= 0) {
            return material.explosionResistance();
        }
        if (material.nativeProperties()) {
            BlockState full = Pieces.nativeState(material, "full");

            return full.getBlock().getExplosionResistance(full, world, position, explosion);
        }

        return material.resistance() < 0 ? material.hardness() : material.resistance() * 3 / 5;
    }

    @Override
    public ToolType getHarvestTool(BlockState state) {
        String type = definition(state).type;

        if (type.equals("rock") || type.equals("metal")) {
            return ToolType.PICKAXE;
        }
        if (type.equals("wood")) {
            return ToolType.AXE;
        }

        return type.equals("dirt") || type.equals("grass") || type.equals("path") || type.equals("snow")
                ? ToolType.SHOVEL
                : null;
    }

    @Override
    public int getHarvestLevel(BlockState state) {
        BlockState full = Pieces.nativeState(definition(state), "full");

        return full.getBlock().getHarvestLevel(full);
    }

    @Override
    public SoundType getSoundType(BlockState state, IWorldReader world, BlockPos position, Entity entity) {
        BlockState full = Pieces.nativeState(definition(state), "full");

        return full.getBlock().getSoundType(full, world, position, entity);
    }

    @Override
    public int getFlammability(BlockState state, IBlockReader world, BlockPos position, Direction face) {
        Catalogue.MaterialDef material = definition(state);

        if (material.flammability() >= 0) {
            return material.flammability();
        }

        BlockState full = Pieces.nativeState(material, "full");

        return full.getBlock().getFlammability(full, world, position, face);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, IBlockReader world, BlockPos position, Direction face) {
        Catalogue.MaterialDef material = definition(state);

        if (material.fireSpread() >= 0) {
            return material.fireSpread();
        }

        BlockState full = Pieces.nativeState(material, "full");

        return full.getBlock().getFireSpreadSpeed(full, world, position, face);
    }

    @Override
    public boolean canSustainPlant(BlockState state, IBlockReader world, BlockPos position, Direction face,
            IPlantable plant) {
        if (face != Direction.UP || !completeTop(state) || !definition(state).soil()
                || state.getValue(SlabBlock.WATERLOGGED)) {
            return false;
        }

        BlockState full = Pieces.nativeState(definition(state), "full");

        return full.getBlock().canSustainPlant(full, world, position, face, plant);
    }

    private boolean completeTop(BlockState state) {
        return palette.shape != Shape.WALL && palette.shape != Shape.PANE && Geometry.completeTop(mask(state));
    }

    public List<AxisAlignedBB> boxes(BlockState blockState) {
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();

        if (palette.shape == Shape.WALL) {
            for (WallGeometry.Bounds wallBounds : WallGeometry.boxes(blockState.getValue(CONNECTIONS),
                    blockState.getValue(WallBlock.UP))) {
                boxes.add(new AxisAlignedBB(wallBounds.minX / 16.0, wallBounds.minY / 16.0, wallBounds.minZ / 16.0,
                        wallBounds.maxX / 16.0, wallBounds.maxY / 16.0, wallBounds.maxZ / 16.0));
            }
        } else if (palette.shape == Shape.PANE) {
            double paneMinimum = .4375;
            double paneMaximum = 1 - paneMinimum;

            boxes.add(new AxisAlignedBB(paneMinimum, 0, paneMinimum, paneMaximum, 1, paneMaximum));

            int connections = blockState.getValue(CONNECTIONS);

            if ((connections & 1) != 0) {
                boxes.add(new AxisAlignedBB(paneMinimum, 0, 0, paneMaximum, 1, paneMinimum));
            }
            if ((connections & 2) != 0) {
                boxes.add(new AxisAlignedBB(paneMaximum, 0, paneMinimum, 1, 1, paneMaximum));
            }
            if ((connections & 4) != 0) {
                boxes.add(new AxisAlignedBB(paneMinimum, 0, paneMaximum, paneMaximum, 1, 1));
            }
            if ((connections & 8) != 0) {
                boxes.add(new AxisAlignedBB(0, 0, paneMinimum, paneMinimum, 1, paneMaximum));
            }
        } else {
            int occupiedCells = mask(blockState);

            for (int cellIndex = 0; cellIndex < 8; cellIndex++) {
                if ((occupiedCells & (1 << cellIndex)) != 0) {
                    double cellMinimumX = (cellIndex & 1) * .5;
                    double cellMinimumZ = ((cellIndex >> 1) & 1) * .5;
                    double cellMinimumY = ((cellIndex >> 2) & 1) * .5;

                    boolean exposedTop = cellIndex >= 4 || (occupiedCells & (1 << (cellIndex + 4))) == 0;
                    double loweredTop = palette.group.equals("path") && exposedTop ? 1.0 / 16.0 : 0;
                    // Extend only the base to meet a lowered vanilla path, keeping the walking surface in place.
                    double loweredBase = palette.group.equals("path") && cellMinimumY == 0
                            && blockState.getValue(LOWERED_SUPPORT) ? 1.0 / 16.0 : 0;

                    boxes.add(new AxisAlignedBB(cellMinimumX, cellMinimumY - loweredBase, cellMinimumZ,
                            cellMinimumX + .5, cellMinimumY + .5 - loweredTop, cellMinimumZ + .5));
                }
            }
        }

        return boxes;
    }

    private int geometryKey(BlockState state) {
        if (palette.shape == Shape.WALL) {
            return state.getValue(CONNECTIONS) | (state.getValue(WallBlock.UP) ? 16 : 0);
        }
        if (palette.shape == Shape.PANE) {
            return state.getValue(CONNECTIONS);
        }

        return mask(state) | (state.hasProperty(LOWERED_SUPPORT) && state.getValue(LOWERED_SUPPORT) ? 256 : 0);
    }

    private VoxelShape shape(BlockState state, boolean collision) {
        Map<Integer, VoxelShape> cache = collision ? collisions : outlines;

        return cache.computeIfAbsent(geometryKey(state), key -> {
            VoxelShape result = VoxelShapes.empty();

            for (AxisAlignedBB box : boxes(state)) {
                if (collision && palette.shape == Shape.WALL) {
                    box = new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, 1.5, box.maxZ);
                }

                result = VoxelShapes.or(result, VoxelShapes.create(box));
            }

            return result;
        });
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos position, ISelectionContext context) {
        return shape(state, false);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos position,
            ISelectionContext context) {
        return shape(state, true);
    }

    public AxisAlignedBB bounds(BlockState state, IBlockReader world, BlockPos position) {
        return shape(state, false).bounds();
    }

    public BlockState getActualState(BlockState state, IBlockReader world, BlockPos position) {
        if (state.hasProperty(SnowyDirtBlock.SNOWY)) {
            boolean snowy = false;

            for (Direction side : new Direction[] { Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST }) {
                Block neighbour = readState(world, position.relative(side)).getBlock();

                snowy |= neighbour == Blocks.SNOW_BLOCK || neighbour == Blocks.SNOW;
            }

            state = state.setValue(SnowyDirtBlock.SNOWY, snowy);
        }
        if (state.hasProperty(CONNECTIONS)) {
            int connections = 0;
            int bit = 1;

            for (Direction side : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST }) {
                BlockPos neighbourPos = position.relative(side);
                BlockState neighbour = readState(world, neighbourPos);
                Block block = neighbour.getBlock();
                boolean compatible = block instanceof PieceBlock && ((PieceBlock) block).palette.shape == palette.shape;

                if (compatible || block instanceof WallBlock || block instanceof PaneBlock
                        || block instanceof FenceGateBlock
                        || Block.isFaceFull(neighbour.getCollisionShape(world, neighbourPos), side.getOpposite())) {
                    connections |= bit;
                }

                bit <<= 1;
            }

            state = state.setValue(CONNECTIONS, connections);

            if (state.hasProperty(WallBlock.UP)) {
                state = state.setValue(WallBlock.UP,
                        connections != 5 && connections != 10 || !readState(world, position.above()).isAir());
            }
        }
        if (palette.shape == Shape.STAIRS) {
            state = state.setValue(STAIR_SHAPE, stairsShape(state, world, position));
        }
        if (state.hasProperty(LOWERED_SUPPORT)) {
            state = state.setValue(LOWERED_SUPPORT, readState(world, position.below()).getBlock() == Blocks.GRASS_PATH);
        }

        return state;
    }

    private static BlockState readState(IBlockReader world, BlockPos position) {
        if (world instanceof World && !((World) world).isLoaded(position)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (world instanceof WorldGenRegion
                && !((WorldGenRegion) world).hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
            return Blocks.AIR.defaultBlockState();
        }

        return world.getBlockState(position);
    }

    private static Direction stairsFacing(int meta) {
        return new Direction[] { Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH }[meta & 3];
    }

    private Integer stairMeta(BlockState blockState, String material) {
        if (blockState.getBlock() instanceof PieceBlock) {
            PieceBlock pieceBlock = (PieceBlock) blockState.getBlock();

            return pieceBlock.palette.shape == Shape.STAIRS && pieceBlock.definition(blockState).id.equals(material)
                    ? pieceBlock.orientation(blockState)
                    : null;
        }
        if (blockState.getBlock() instanceof StairsBlock) {
            BlockState nativeStairs = Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "stairs");

            if (nativeStairs != null && nativeStairs.getBlock() == blockState.getBlock()) {
                Direction facing = blockState.getValue(StairsBlock.FACING);
                int rotation = facing == Direction.EAST ? 0
                        : facing == Direction.WEST ? 1 : facing == Direction.SOUTH ? 2 : 3;

                return rotation + (blockState.getValue(StairsBlock.HALF) == Half.TOP ? 4 : 0);
            }
        }

        return null;
    }

    private boolean different(BlockState blockState, IBlockReader blockAccess, BlockPos position, Direction offset) {
        Integer neighbourMetadata = stairMeta(readState(blockAccess, position.relative(offset)),
                definition(blockState).id);

        return neighbourMetadata == null || neighbourMetadata != orientation(blockState);
    }

    private StairsShape stairsShape(BlockState blockState, IBlockReader blockAccess, BlockPos position) {
        Direction stairFacing = stairsFacing(orientation(blockState));
        Integer front = stairMeta(readState(blockAccess, position.relative(stairFacing)), definition(blockState).id);

        if (front != null && (front & 4) == (orientation(blockState) & 4)) {
            Direction neighbourFacing = stairsFacing(front);

            if (neighbourFacing.getAxis() != stairFacing.getAxis()
                    && different(blockState, blockAccess, position, neighbourFacing.getOpposite())) {
                return neighbourFacing == stairFacing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
            }
        }

        Integer back = stairMeta(readState(blockAccess, position.relative(stairFacing.getOpposite())),
                definition(blockState).id);

        if (back != null && (back & 4) == (orientation(blockState) & 4)) {
            Direction neighbourFacing = stairsFacing(back);

            if (neighbourFacing.getAxis() != stairFacing.getAxis()
                    && different(blockState, blockAccess, position, neighbourFacing)) {
                return neighbourFacing == stairFacing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
            }
        }

        return StairsShape.STRAIGHT;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder context) {
        ItemStack tool = context.getParameter(LootParameters.TOOL);

        if (!palette.group.equals("path") && tool != null
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return Collections.singletonList(getSilkTouchDrop(state));
        }

        String material = definition(state).normalDrop();

        if (material.isEmpty()) {
            return Collections.emptyList();
        }

        BlockState dropped = Pieces.state(material, palette.shape, orientation(state));
        ItemStack stack = palette.shape == Shape.VERTICAL_SLAB ? Pieces.slabDrop(material, dropped)
                : Pieces.stack(dropped, 1);

        return stack == null || stack.isEmpty() ? Collections.emptyList() : Collections.singletonList(stack);
    }

    protected ItemStack getSilkTouchDrop(BlockState state) {
        if (palette.group.equals("path")) {
            return Pieces.stack(PathPieces.dirt(state), 1);
        }

        return palette.shape == Shape.VERTICAL_SLAB ? Pieces.slabDrop(definition(state).id, state)
                : Pieces.stack(Pieces.state(definition(state).id, palette.shape, orientation(state)), 1);
    }

    @Override
    public ItemStack getCloneItemStack(IBlockReader world, BlockPos position, BlockState state) {
        return palette.shape == Shape.VERTICAL_SLAB ? Pieces.slabDrop(definition(state).id, state)
                : Pieces.stack(Pieces.state(definition(state).id, palette.shape, orientation(state)), 1);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(SlabBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbour, IWorld world,
            BlockPos position, BlockPos neighbourPos) {
        if (state.getValue(SlabBlock.WATERLOGGED)) {
            world.getLiquidTicks().scheduleTick(position, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return getActualState(state, world, position);
    }

    private void repairVisualState(World world, BlockPos position, BlockState state) {
        BlockState actual = getActualState(state, world, position);

        if (actual != state) {
            world.setBlock(position, actual, 2);
        }
    }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos position, Random random) {
        if (world.isClientSide) {
            return;
        }

        repairVisualState(world, position, state);
        PathPieces.checkCover(world, position, world.getBlockState(position));
        GrassLifecycle.tick(world, position, world.getBlockState(position), random);
    }

    @Override
    public void onPlace(BlockState state, World world, BlockPos position, BlockState oldState, boolean moving) {
        if (world.isClientSide) {
            return;
        }

        repairVisualState(world, position, state);
        PathPieces.checkCover(world, position, world.getBlockState(position));
        GrassLifecycle.repairSupport(world, position, world.getBlockState(position));
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos position, PlayerEntity player, Hand hand,
            BlockRayTraceResult hit) {
        return PathPieces.activate(world, position, player, player.getItemInHand(hand), hit.getDirection(), hand)
                ? ActionResultType.SUCCESS : ActionResultType.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, World world, BlockPos position, Block changed, BlockPos changedPos,
            boolean moving) {
        if (world.isClientSide) {
            return;
        }

        repairVisualState(world, position, state);
        PathPieces.checkCover(world, position, world.getBlockState(position));
        GrassLifecycle.repairSupport(world, position, world.getBlockState(position));
    }

    @Override
    public boolean isValidBonemealTarget(IBlockReader world, BlockPos position, BlockState state, boolean client) {
        return GrassLifecycle.grass(state) && completeTop(state) && !state.getValue(SlabBlock.WATERLOGGED);
    }

    @Override
    public boolean isBonemealSuccess(World world, Random random, BlockPos position, BlockState state) {
        return isValidBonemealTarget(world, position, state, world.isClientSide);
    }

    @Override
    public void performBonemeal(ServerWorld world, Random random, BlockPos position, BlockState state) {
        if (isValidBonemealTarget(world, position, state, world.isClientSide)) {
            ((IGrowable) Blocks.GRASS_BLOCK).performBonemeal(world, random, position, Blocks.GRASS_BLOCK.defaultBlockState());
        }
    }
}
