package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** One material per inventory item; placement still uses the fixed block palette. */
public final class PieceItem extends Item {
    public final PieceBlock piece;
    public final String material;

    public PieceItem(PieceBlock block, String material) {
        super(properties(block, material));
        this.piece = block;
        this.material = material;
    }

    private static Item.Properties properties(PieceBlock block, String material) {
        boolean nativeEquivalent = Pieces.nativeState(Catalogue.INSTANCE.materials.get(material),
                block.palette.shape.name().toLowerCase(java.util.Locale.ROOT)) != null;
        boolean hiddenVertical = block.palette.shape == Shape.VERTICAL_SLAB;
        boolean retired = Catalogue.INSTANCE.materials.get(material).replacement() != null;
        Item.Properties properties = new Item.Properties();

        return nativeEquivalent || hiddenVertical || retired ? properties : properties.group(PiecesTab.INSTANCE);
    }

    @Override
    public ITextComponent getDisplayName(ItemStack stack) {
        ItemStack full = Pieces.stack(Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "full"), 1);

        if (full == null) {
            full = Pieces.stack(Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "slab"), 1);
        }

        String namespace = piece.palette.module.equals("mineralogy")
                ? zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.ASSETS
                : piece.palette.namespace;

        return PieceNames.displayName(namespace, material,
                piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT), full.getDisplayName());
    }

    @Override
    public int getBurnTime(ItemStack stack) {
        return Catalogue.INSTANCE.materials.get(material).type.equals("wood") ? 300 : -1;
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        PlayerEntity player = context.getPlayer();

        if (player == null || context.getItem().isEmpty()) {
            return ActionResultType.FAIL;
        }

        BlockPos position = context.getPos();
        Vec3d hit = context.getHitVec();
        Shape shape = piece.palette.shape;

        if (shape == Shape.VERTICAL_SLAB && Pieces.state(material, Shape.SLAB, 1) != null) {
            shape = Shape.SLAB;
        }

        return place(context.getItem(), player, context.getWorld(), position, context.getHand(), context.getFace(),
                (float) (hit.x - position.getX()), (float) (hit.y - position.getY()), (float) (hit.z - position.getZ()),
                material, shape);
    }

    static ActionResultType place(ItemStack stack, PlayerEntity player, World world, BlockPos position, Hand hand,
            Direction face, float hitX, float hitY, float hitZ, String materialId, Shape shape) {
        if (stack == null || stack.isEmpty()) {
            return ActionResultType.FAIL;
        }

        float targetX = hitX;
        float targetY = hitY;
        float targetZ = hitZ;

        BlockState existingState = world.getBlockState(position);

        // Vanilla slabs call a matching half replaceable. Join it before asking the placement context whether this cell
        // can accept a new block.
        if (combine(world, position, existingState, materialId, shape, player, stack, face, hitX, hitY, hitZ)) {
            return ActionResultType.SUCCESS;
        }
        if (!replaceable(world, position, player, hand, stack, face, hitX, hitY, hitZ)) {
            position = position.offset(face);
            existingState = world.getBlockState(position);
            targetX -= face.getXOffset();
            targetY -= face.getYOffset();
            targetZ -= face.getZOffset();

            if (combine(world, position, existingState, materialId, shape, player, stack, face, targetX, targetY,
                    targetZ)) {
                return ActionResultType.SUCCESS;
            }
        }
        if (!player.canPlayerEdit(position, face, stack)
                || !replaceable(world, position, player, hand, stack, face, targetX, targetY, targetZ)) {
            return ActionResultType.FAIL;
        }

        int horizontalRotation = closest(hitX, hitZ);
        int orientation;
        Shape placedShape = shape;

        switch (shape) {
            case SLAB:
                Direction slabSide = SlabPlacement.side(face, hitX, hitY, hitZ);
                placedShape = SlabPlacement.shape(slabSide);
                orientation = SlabPlacement.orientation(slabSide);
                break;
            case VERTICAL_SLAB:
                orientation = SlabPlacement.orientation(SlabPlacement.verticalSide(face, hitX, hitY, hitZ));
                break;
            case HORIZONTAL_STEP:
                orientation = horizontalRotation
                        + (face == Direction.DOWN || face.getAxis() != Direction.Axis.Y && hitY > .5f ? 0 : 4);
                break;
            case VERTICAL_STEP:
                orientation = corner(hitX, hitZ);
                break;
            case STEP:
                orientation = player.isSneaking() ? 8 + corner(hitX, hitZ)
                        : horizontalRotation
                                + (face == Direction.DOWN || face.getAxis() != Direction.Axis.Y && hitY > .5f ? 0 : 4);
                break;
            case CORNER:
                orientation = corner(hitX, hitZ)
                        + (face == Direction.DOWN || face.getAxis() != Direction.Axis.Y && hitY > .5f ? 0 : 4);
                break;
            case STAIRS:
                Direction playerFacing = player.getHorizontalFacing();
                orientation = (playerFacing == Direction.EAST ? 0
                        : playerFacing == Direction.WEST ? 1 : playerFacing == Direction.SOUTH ? 2 : 3)
                        + (face == Direction.DOWN || face.getAxis() != Direction.Axis.Y && hitY > .5f ? 4 : 0);
                break;
            default:
                orientation = 0;
        }

        BlockState target = Pieces.state(materialId, placedShape, orientation);

        if (shape == Shape.HORIZONTAL_STEP && player.isSneaking()) {
            target = Pieces.state(materialId, Shape.VERTICAL_STEP, corner(hitX, hitZ));
        }
        if (target != null && target.has(SlabBlock.WATERLOGGED)) {
            boolean water = world.getFluidState(position).getFluid() == Fluids.WATER
                    || world.getFluidState(position).getFluid() == Fluids.FLOWING_WATER;

            target = target.with(SlabBlock.WATERLOGGED, water);
        }
        // Check the chosen orientation, not the block's default collision box.
        if (target == null || !target.isValidPosition(world, position) || !safe(world, position, target)) {
            return ActionResultType.FAIL;
        }
        if (!world.setBlockState(position, target, 3)) {
            return ActionResultType.FAIL;
        }

        target.getBlock().onBlockPlacedBy(world, position, target, player, stack);
        finish(world, position, target, player, stack);
        return ActionResultType.SUCCESS;
    }

    public boolean canPlaceBlockOnSide(World world, BlockPos position, Direction side, PlayerEntity player,
            ItemStack itemStack) {
        return player.canPlayerEdit(position, side, itemStack);
    }

    /** Combine occupied cells only when their union is a supported shape of the same material. */
    private static boolean combine(World world, BlockPos position, BlockState existingState, String material,
            Shape shape, PlayerEntity player, ItemStack stack, Direction face, float hitX, float hitY, float hitZ) {
        if (!player.canPlayerEdit(position, face, stack) || shape == Shape.WALL || shape == Shape.PANE) {
            return false;
        }

        int existingCells;

        if (existingState.getBlock() instanceof PieceBlock) {
            PieceBlock other = (PieceBlock) existingState.getBlock();

            if (!other.definition(existingState).id.equals(material) || other.palette.shape == Shape.WALL
                    || other.palette.shape == Shape.PANE) {
                return false;
            }

            existingCells = other.mask(existingState);
        } else {
            BlockState nativeSlab = Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "slab");

            if (nativeSlab == null || existingState.getBlock() != nativeSlab.getBlock()) {
                return false;
            }

            existingCells = NativeSlabState.mask(existingState);

            if (existingCells < 0) {
                return false;
            }
        }

        // Nudge seam clicks towards the empty half, including clicks exactly on the centre plane.
        float epsilon = .0001f;
        int clickedCell = (hitX + face.getXOffset() * epsilon >= .5f ? 1 : 0)
                | (hitZ + face.getZOffset() * epsilon >= .5f ? 2 : 0)
                | (hitY + face.getYOffset() * epsilon >= .5f ? 4 : 0);
        BlockState combinedState = null;
        boolean isSlab = shape == Shape.SLAB || shape == Shape.VERTICAL_SLAB;
        Shape[] additions = isSlab ? new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB } : new Shape[] { shape };

        search:
        for (Shape addition : additions) {
            for (int orientation = 0; orientation < addition.states; orientation++) {
                int addedCells = Geometry.mask(addition, orientation);

                if ((addedCells & 1 << clickedCell) == 0 || (addedCells & existingCells) != 0) {
                    continue;
                }

                int combined = addedCells | existingCells;

                if (combined == 255) {
                    combinedState = Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "full");
                } else {
                    for (Shape candidateShape : new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB, Shape.HORIZONTAL_STEP,
                            Shape.VERTICAL_STEP, Shape.STEP, Shape.CORNER, Shape.STAIRS }) {
                        int combinedOrientation = Geometry.orientationFor(candidateShape, combined);

                        if (combinedOrientation >= 0) {
                            combinedState = Pieces.state(material, candidateShape, combinedOrientation);

                            if (combinedState != null) {
                                break;
                            }
                        }
                    }
                }
                if (combinedState != null) {
                    break search;
                }
            }
        }

        if (combinedState != null && combinedState.has(SlabBlock.WATERLOGGED)
                && existingState.has(SlabBlock.WATERLOGGED)) {
            combinedState = combinedState.with(SlabBlock.WATERLOGGED, existingState.get(SlabBlock.WATERLOGGED));
        }
        if (combinedState == null || !safe(world, position, combinedState)
                || !world.setBlockState(position, combinedState, 3)) {
            return false;
        }

        finish(world, position, combinedState, player, stack);
        return true;
    }

    private static boolean safe(World world, BlockPos position, BlockState target) {
        return world.func_217350_a(target, position, ISelectionContext.dummy());
    }

    private static boolean replaceable(World world, BlockPos position, PlayerEntity player, Hand hand, ItemStack stack,
            Direction face, float hitX, float hitY, float hitZ) {
        BlockRayTraceResult hit = new BlockRayTraceResult(
                new Vec3d(position.getX() + hitX, position.getY() + hitY, position.getZ() + hitZ), face, position,
                false);

        return new PlacementContext(world, player, hand, stack, hit).replacingClickedOnBlock();
    }

    private static final class PlacementContext extends BlockItemUseContext {
        PlacementContext(World world, PlayerEntity player, Hand hand, ItemStack stack, BlockRayTraceResult hit) {
            super(world, player, hand, stack, hit);
        }
    }

    private static void finish(World world, BlockPos position, BlockState target, PlayerEntity player,
            ItemStack stack) {
        net.minecraft.block.SoundType sound = target.getBlock().getSoundType(target, world, position, player);

        world.playSound(player, position, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1) / 2,
                sound.getPitch() * .8f);

        if (!player.abilities.isCreativeMode) {
            stack.shrink(1);
        }
    }

    private static int closest(float hitX, float hitZ) {
        float[] dist = { hitZ, 1 - hitX, 1 - hitZ, hitX };
        int best = 0;

        for (int edgeIndex = 1; edgeIndex < 4; edgeIndex++) {
            if (dist[edgeIndex] < dist[best]) {
                best = edgeIndex;
            }
        }

        return best;
    }

    private static int corner(float hitX, float hitZ) {
        return hitZ < .5f ? (hitX < .5f ? 0 : 1) : (hitX < .5f ? 3 : 2);
    }
}
