package zone.moddev.mc.skysbuildingpieces.content;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.property.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

/** Metadata palettes with derived visuals; no tile entities or saved composites. */
public final class PieceBlock extends Block implements IGrowable {
    public static final PropertyInteger META = PropertyInteger.create("meta", 0, 15);
    public static final PropertyInteger CONNECTIONS = PropertyInteger.create("connections", 0, 15);
    public static final PropertyEnum<BlockStairs.EnumShape> STAIR_SHAPE = BlockStairs.SHAPE;
    private static final ThreadLocal<Catalogue.Palette> CONSTRUCTING = new ThreadLocal<Catalogue.Palette>();
    public final Catalogue.Palette palette;

    public PieceBlock(Catalogue.Palette palette) {
        super(materialFor(palette));
        this.palette = palette;
        CONSTRUCTING.remove();
        setRegistryName(palette.namespace, palette.id);
        setTranslationKey(palette.namespace + "." + palette.id);
        setCreativeTab(PiecesTab.INSTANCE);
        useNeighborBrightness = true;
        setLightOpacity(0);
        setTickRandomly(palette.group.equals("grass") || palette.group.equals("dirt"));
        slipperiness = (palette.group.equals("ice") || palette.group.equals("packed_ice")) ? 0.98f : 0.6f;

        IBlockState initial = blockState.getBaseState().withProperty(META, 0);

        if (initial.getPropertyKeys().contains(BlockGrass.SNOWY)) {
            initial = initial.withProperty(BlockGrass.SNOWY, false);
        }
        if (initial.getPropertyKeys().contains(CONNECTIONS)) {
            initial = initial.withProperty(CONNECTIONS, 0);
        }
        if (initial.getPropertyKeys().contains(BlockWall.UP)) {
            initial = initial.withProperty(BlockWall.UP, true);
        }
        if (initial.getPropertyKeys().contains(STAIR_SHAPE)) {
            initial = initial.withProperty(STAIR_SHAPE, BlockStairs.EnumShape.STRAIGHT);
        }

        setDefaultState(initial);
    }

    private static Material materialFor(Catalogue.Palette palette) {
        CONSTRUCTING.set(palette);

        String materialGroup = palette.group;

        if (!palette.module.equals("vanilla")) {
            return Pieces.nativeState(Catalogue.INSTANCE.materials.get(palette.materials.get(0)), "full").getMaterial();
        }
        if (materialGroup.equals("metal")) {
            return Material.IRON;
        }
        if (materialGroup.equals("wood")) {
            return Material.WOOD;
        }
        if (materialGroup.equals("dirt")) {
            return Material.GROUND;
        }
        if (materialGroup.equals("grass")) {
            return Material.GRASS;
        }
        if (materialGroup.equals("cloth")) {
            return Material.CLOTH;
        }
        if (materialGroup.contains("glass")) {
            return Material.GLASS;
        }
        if (materialGroup.equals("ice")) {
            return Material.ICE;
        }
        if (materialGroup.equals("packed_ice")) {
            return Material.PACKED_ICE;
        }
        if (materialGroup.equals("snow")) {
            return Material.CRAFTED_SNOW;
        }

        return Material.ROCK;
    }

    protected BlockStateContainer createBlockState() {
        Catalogue.Palette palette = CONSTRUCTING.get();

        if (palette == null) {
            throw new IllegalStateException("Palette construction context missing");
        }

        List<IProperty<?>> properties = new ArrayList<IProperty<?>>();

        properties.add(META);

        if (palette.group.equals("grass") || palette.group.equals("dirt")) {
            properties.add(BlockGrass.SNOWY);
        }
        if (palette.shape == Shape.WALL || palette.shape == Shape.PANE) {
            properties.add(CONNECTIONS);
        }
        if (palette.shape == Shape.WALL) {
            properties.add(BlockWall.UP);
        }
        if (palette.shape == Shape.STAIRS) {
            properties.add(STAIR_SHAPE);
        }

        IProperty<?>[] listed = properties.toArray(new IProperty<?>[properties.size()]);

        if (palette.shape == Shape.WALL && palette.group.contains("glass")) {
            return new ExtendedBlockState(this, listed, new IUnlistedProperty<?>[] { GlassWallTexture.JOINS });
        }

        return new BlockStateContainer(this, listed);
    }

    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(META, meta & 15);
    }

    public int getMetaFromState(IBlockState blockState) {
        return blockState.getValue(META);
    }

    public Catalogue.MaterialDef definition(IBlockState blockState) {
        return Catalogue.INSTANCE.materials.get(palette.material(getMetaFromState(blockState)));
    }

    public int orientation(IBlockState blockState) {
        return getMetaFromState(blockState) % palette.shape.states;
    }

    public int canonicalMeta(IBlockState blockState) {
        return getMetaFromState(blockState) / palette.shape.states * palette.shape.states;
    }

    public int mask(IBlockState blockState) {
        return palette.shape == Shape.STAIRS
                ? Geometry.stairs(orientation(blockState), blockState.getValue(STAIR_SHAPE).getName())
                : Geometry.mask(palette.shape, orientation(blockState));
    }

    public boolean isOpaqueCube(IBlockState blockState) {
        return false;
    }

    public boolean isFullCube(IBlockState blockState) {
        return false;
    }

    public boolean isSideSolid(IBlockState blockState, IBlockAccess blockAccess, BlockPos position, EnumFacing side) {
        return palette.shape != Shape.WALL && palette.shape != Shape.PANE
                && Geometry.completeFace(mask(blockState), side.getIndex());
    }

    @Override
    public boolean canPlaceTorchOnTop(IBlockState blockState, IBlockAccess blockAccess, BlockPos position) {
        return palette.shape == Shape.WALL || super.canPlaceTorchOnTop(blockState, blockAccess, position);
    }

    public boolean doesSideBlockRendering(IBlockState blockState, IBlockAccess blockAccess, BlockPos position,
            EnumFacing side) {
        return getMaterial(blockState).isOpaque() && isSideSolid(blockState, blockAccess, position, side);
    }

    // Only boundary quads on wall connections have cull faces. Recessed and exposed partial faces must remain visible
    // beside other blocks.
    public boolean shouldSideBeRendered(IBlockState blockState, IBlockAccess blockAccess, BlockPos position,
            EnumFacing side) {
        if (palette.shape == Shape.WALL && side.getAxis() != EnumFacing.Axis.Y) {
            IBlockState neighbour = blockAccess.getBlockState(position.offset(side));

            if (neighbour.isOpaqueCube()) {
                return false;
            }
            if (neighbour.getBlock() instanceof PieceBlock) {
                PieceBlock other = (PieceBlock) neighbour.getBlock();

                if (other.palette.shape == Shape.WALL
                        && other.definition(neighbour).id.equals(definition(blockState).id)) {
                    return false;
                }
            }
        }

        return true;
    }

    public BlockRenderLayer getRenderLayer() {
        if (palette.group.equals("grass")) {
            return BlockRenderLayer.CUTOUT_MIPPED;
        }
        if (palette.group.equals("clear_glass")) {
            return BlockRenderLayer.CUTOUT;
        }
        if (palette.group.equals("stained_glass") || palette.group.equals("ice")) {
            return BlockRenderLayer.TRANSLUCENT;
        }

        return BlockRenderLayer.SOLID;
    }

    public float getBlockHardness(IBlockState blockState, World world, BlockPos position) {
        Catalogue.MaterialDef materialDefinition = definition(blockState);
        IBlockState full = materialDefinition.nativeProperties() ? Pieces.nativeState(materialDefinition, "full")
                : null;

        return full == null ? materialDefinition.hardness() : full.getBlock().getBlockHardness(full, world, position);
    }

    public float getExplosionResistance(World world, BlockPos position, Entity entity, Explosion explosion) {
        Catalogue.MaterialDef materialDefinition = definition(world.getBlockState(position));

        if (materialDefinition.explosionResistance() >= 0) {
            return materialDefinition.explosionResistance();
        }
        if (materialDefinition.nativeProperties()) {
            return Pieces.nativeState(materialDefinition, "full").getBlock().getExplosionResistance(entity);
        }

        return materialDefinition.resistance() < 0 ? materialDefinition.hardness()
                : materialDefinition.resistance() * 3 / 5;
    }

    public String getHarvestTool(IBlockState blockState) {
        String materialType = definition(blockState).type;

        return materialType.equals("rock") || materialType.equals("metal") ? "pickaxe"
                : materialType.equals("wood") ? "axe"
                        : materialType.equals("dirt") || materialType.equals("grass") || materialType.equals("snow")
                                ? "shovel"
                                : null;
    }

    public int getHarvestLevel(IBlockState blockState) {
        return Pieces.nativeState(definition(blockState), "full").getBlock()
                .getHarvestLevel(Pieces.nativeState(definition(blockState), "full"));
    }

    public SoundType getSoundType(IBlockState blockState, World world, BlockPos position, Entity entity) {
        IBlockState full = Pieces.nativeState(definition(blockState), "full");

        return full.getBlock().getSoundType(full, world, position, entity);
    }

    public int getFlammability(IBlockAccess blockAccess, BlockPos position, EnumFacing face) {
        Catalogue.MaterialDef materialDefinition = definition(blockAccess.getBlockState(position));

        if (materialDefinition.flammability() >= 0) {
            return materialDefinition.flammability();
        }

        IBlockState full = Pieces.nativeState(materialDefinition, "full");

        return full.getBlock().getFlammability(blockAccess, position, face);
    }

    public int getFireSpreadSpeed(IBlockAccess blockAccess, BlockPos position, EnumFacing face) {
        Catalogue.MaterialDef materialDefinition = definition(blockAccess.getBlockState(position));

        if (materialDefinition.fireSpread() >= 0) {
            return materialDefinition.fireSpread();
        }

        IBlockState full = Pieces.nativeState(materialDefinition, "full");

        return full.getBlock().getFireSpreadSpeed(blockAccess, position, face);
    }

    public boolean canSustainPlant(IBlockState blockState, IBlockAccess blockAccess, BlockPos position,
            EnumFacing facing, IPlantable plant) {
        if (facing != EnumFacing.UP || !Geometry.completeTop(mask(blockState)) || !definition(blockState).soil()) {
            return false;
        }

        IBlockState full = Pieces.nativeState(definition(blockState), "full");

        return full.getBlock().canSustainPlant(full, blockAccess, position, facing, plant);
    }

    public List<AxisAlignedBB> boxes(IBlockState blockState) {
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();

        if (palette.shape == Shape.WALL) {
            for (WallGeometry.Bounds wallBounds : WallGeometry.boxes(blockState.getValue(CONNECTIONS),
                    blockState.getValue(BlockWall.UP))) {
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

                    boxes.add(new AxisAlignedBB(cellMinimumX, cellMinimumY, cellMinimumZ, cellMinimumX + .5,
                            cellMinimumY + .5, cellMinimumZ + .5));
                }
            }
        }

        return boxes;
    }

    public AxisAlignedBB getBoundingBox(IBlockState blockState, IBlockAccess blockAccess, BlockPos position) {
        List<AxisAlignedBB> collisionBoxes = boxes(getActualState(blockState, blockAccess, position));
        AxisAlignedBB result = collisionBoxes.get(0);

        for (int boxIndex = 1; boxIndex < collisionBoxes.size(); boxIndex++) {
            result = result.union(collisionBoxes.get(boxIndex));
        }

        return result;
    }

    @Override
    public void addCollisionBoxToList(IBlockState blockState, World world, BlockPos position, AxisAlignedBB entityBox,
            List<AxisAlignedBB> list, Entity entity, boolean actualState) {
        for (AxisAlignedBB boundingBox : boxes(getActualState(blockState, world, position))) {
            if (palette.shape == Shape.WALL) {
                boundingBox = new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX,
                        1.5, boundingBox.maxZ);
            }

            addCollisionBoxToList(position, entityBox, list, boundingBox);
        }
    }

    public RayTraceResult collisionRayTrace(IBlockState blockState, World world, BlockPos position, Vec3d from,
            Vec3d rayEnd) {
        RayTraceResult closest = null;
        double distance = Double.MAX_VALUE;

        for (AxisAlignedBB boundingBox : boxes(getActualState(blockState, world, position))) {
            RayTraceResult hit = rayTrace(position, from, rayEnd, boundingBox);

            if (hit != null && hit.hitVec.squareDistanceTo(from) < distance) {
                closest = hit;
                distance = hit.hitVec.squareDistanceTo(from);
            }
        }

        return closest;
    }

    public IBlockState getActualState(IBlockState blockState, IBlockAccess blockAccess, BlockPos position) {
        boolean snow = false;

        for (EnumFacing face : new EnumFacing[] { EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH,
                EnumFacing.WEST }) {
            Block block = blockAccess.getBlockState(position.offset(face)).getBlock();

            snow |= block == Blocks.SNOW || block == Blocks.SNOW_LAYER;
        }

        int connections = 0;
        int connectionBit = 1;

        for (EnumFacing face : new EnumFacing[] { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH,
                EnumFacing.WEST }) {
            IBlockState neighbourState = blockAccess.getBlockState(position.offset(face));
            boolean compatible = neighbourState.getBlock() instanceof PieceBlock
                    && ((PieceBlock) neighbourState.getBlock()).palette.shape == palette.shape;

            if (compatible || neighbourState.getBlock() instanceof BlockWall
                    || neighbourState.getBlock() instanceof BlockPane
                    || neighbourState.getBlock() instanceof BlockFenceGate
                    || neighbourState.isSideSolid(blockAccess, position.offset(face), face.getOpposite())) {
                connections |= connectionBit;
            }

            connectionBit <<= 1;
        }
        if (palette.shape == Shape.STAIRS) {
            blockState = blockState.withProperty(STAIR_SHAPE, stairsShape(blockState, blockAccess, position));
        }
        if (blockState.getPropertyKeys().contains(BlockGrass.SNOWY)) {
            blockState = blockState.withProperty(BlockGrass.SNOWY, snow);
        }
        if (blockState.getPropertyKeys().contains(CONNECTIONS)) {
            blockState = blockState.withProperty(CONNECTIONS, connections);
        }
        // Straight runs need no post unless there is a block to support above. Like vanilla wall connections, this is
        // derived rather than saved.
        if (blockState.getPropertyKeys().contains(BlockWall.UP)) {
            blockState = blockState.withProperty(BlockWall.UP,
                    (connections != 5 && connections != 10) || !blockAccess.isAirBlock(position.up()));
        }

        return blockState;
    }

    @Override
    public IBlockState getExtendedState(IBlockState blockState, IBlockAccess blockAccess, BlockPos position) {
        if (!(blockState instanceof IExtendedBlockState)) {
            return blockState;
        }

        IBlockState actual = getActualState(blockState, blockAccess, position);
        int joins = 0;
        int connectionBit = 1;

        for (EnumFacing side : new EnumFacing[] { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH,
                EnumFacing.WEST }) {
            IBlockState neighbour = blockAccess.getBlockState(position.offset(side));

            if (neighbour.getBlock() instanceof PieceBlock) {
                PieceBlock other = (PieceBlock) neighbour.getBlock();

                if (other.palette.shape == Shape.WALL && other.palette.group.contains("glass")
                        && other.definition(neighbour).id.equals(definition(blockState).id)) {
                    joins |= connectionBit;
                }
            }

            connectionBit <<= 1;
        }

        return ((IExtendedBlockState) actual).withProperty(GlassWallTexture.JOINS, joins);
    }

    private static EnumFacing stairsFacing(int meta) {
        return new EnumFacing[] { EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.NORTH }[meta & 3];
    }

    private Integer stairMeta(IBlockState blockState, String material) {
        if (blockState.getBlock() instanceof PieceBlock) {
            PieceBlock pieceBlock = (PieceBlock) blockState.getBlock();

            return pieceBlock.palette.shape == Shape.STAIRS && pieceBlock.definition(blockState).id.equals(material)
                    ? pieceBlock.orientation(blockState)
                    : null;
        }
        if (blockState.getBlock() instanceof BlockStairs) {
            IBlockState nativeStairs = Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "stairs");

            if (nativeStairs != null && nativeStairs.getBlock() == blockState.getBlock()) {
                return blockState.getBlock().getMetaFromState(blockState) & 7;
            }
        }

        return null;
    }

    private boolean different(IBlockState blockState, IBlockAccess blockAccess, BlockPos position, EnumFacing offset) {
        Integer neighbourMetadata = stairMeta(blockAccess.getBlockState(position.offset(offset)),
                definition(blockState).id);

        return neighbourMetadata == null || neighbourMetadata != orientation(blockState);
    }

    private BlockStairs.EnumShape stairsShape(IBlockState blockState, IBlockAccess blockAccess, BlockPos position) {
        EnumFacing stairFacing = stairsFacing(orientation(blockState));
        Integer front = stairMeta(blockAccess.getBlockState(position.offset(stairFacing)), definition(blockState).id);

        if (front != null && (front & 4) == (orientation(blockState) & 4)) {
            EnumFacing neighbourFacing = stairsFacing(front);

            if (neighbourFacing.getAxis() != stairFacing.getAxis()
                    && different(blockState, blockAccess, position, neighbourFacing.getOpposite())) {
                return neighbourFacing == stairFacing.rotateYCCW() ? BlockStairs.EnumShape.OUTER_LEFT
                        : BlockStairs.EnumShape.OUTER_RIGHT;
            }
        }

        Integer back = stairMeta(blockAccess.getBlockState(position.offset(stairFacing.getOpposite())),
                definition(blockState).id);

        if (back != null && (back & 4) == (orientation(blockState) & 4)) {
            EnumFacing neighbourFacing = stairsFacing(back);

            if (neighbourFacing.getAxis() != stairFacing.getAxis()
                    && different(blockState, blockAccess, position, neighbourFacing)) {
                return neighbourFacing == stairFacing.rotateYCCW() ? BlockStairs.EnumShape.INNER_LEFT
                        : BlockStairs.EnumShape.INNER_RIGHT;
            }
        }

        return BlockStairs.EnumShape.STRAIGHT;
    }

    public List<ItemStack> getDrops(IBlockAccess blockAccess, BlockPos position, IBlockState blockState, int fortune) {
        String drop = definition(blockState).normalDrop();

        if (drop.isEmpty()) {
            return Collections.emptyList();
        }

        IBlockState dropped = Pieces.state(drop, palette.shape, orientation(blockState));
        ItemStack stack = palette.shape == Shape.VERTICAL_SLAB ? Pieces.slabDrop(drop, dropped)
                : Pieces.stack(dropped, 1);

        return stack == null ? Collections.<ItemStack>emptyList() : Collections.singletonList(stack);
    }

    protected ItemStack getSilkTouchDrop(IBlockState blockState) {
        return palette.shape == Shape.VERTICAL_SLAB ? Pieces.slabDrop(definition(blockState).id, blockState)
                : Pieces.stack(blockState, 1);
    }

    public boolean canSilkHarvest(World world, BlockPos position, IBlockState blockState, EntityPlayer player) {
        return true;
    }

    public int damageDropped(IBlockState blockState) {
        return canonicalMeta(blockState);
    }

    public ItemStack getPickBlock(IBlockState blockState, RayTraceResult hit, World world, BlockPos position,
            EntityPlayer player) {
        return palette.shape == Shape.VERTICAL_SLAB ? Pieces.slabDrop(definition(blockState).id, blockState)
                : Pieces.stack(blockState, 1);
    }

    @Override
    public void getSubBlocks(net.minecraft.creativetab.CreativeTabs tab, NonNullList<ItemStack> list) {
        if (palette.shape == Shape.VERTICAL_SLAB) {
            return;
        }
        for (int slot = 0; slot < palette.materials.size(); slot++) {
            list.add(new ItemStack(this, 1, slot * palette.shape.states));
        }
    }

    public void updateTick(World world, BlockPos position, IBlockState blockState, Random rand) {
        GrassLifecycle.tick(world, position, blockState, rand);
    }

    public void onBlockAdded(World world, BlockPos position, IBlockState blockState) {
        GrassLifecycle.repairSupport(world, position, blockState);
    }

    @Override
    public void neighborChanged(IBlockState blockState, World world, BlockPos position, Block changed,
            BlockPos changedPos) {
        GrassLifecycle.repairSupport(world, position, blockState);
    }

    public boolean canGrow(World world, BlockPos position, IBlockState blockState, boolean client) {
        return GrassLifecycle.grass(blockState) && Geometry.completeTop(mask(blockState));
    }

    public boolean canUseBonemeal(World world, Random rand, BlockPos position, IBlockState blockState) {
        return canGrow(world, position, blockState, world.isRemote);
    }

    public void grow(World world, Random rand, BlockPos position, IBlockState blockState) {
        if (canGrow(world, position, blockState, world.isRemote)) {
            Blocks.GRASS.grow(world, rand, position, Blocks.GRASS.getDefaultState());
        }
    }
}
