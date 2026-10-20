package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;

/** Direct placement, deliberately not delegated to BuildingBricks' ItemBlock interception. */
public final class PieceItem extends ItemBlock {
    public final PieceBlock piece;

    public PieceItem(PieceBlock block) {
        super(block);
        piece = block;
        setHasSubtypes(true);
    }

    public int getMetadata(int damage) {
        return damage;
    }

    public String getTranslationKey(ItemStack itemStack) {
        return "tile." + piece.palette.namespace + "."
                + new ResourceLocation(piece.palette.material(itemStack.getMetadata())).getPath() + "."
                + piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT);
    }

    public String getItemStackDisplayName(ItemStack stack) {
        Catalogue.MaterialDef materialDefinition = Catalogue.INSTANCE.materials
                .get(piece.palette.material(stack.getMetadata()));
        ItemStack full = Pieces.stack(Pieces.nativeState(materialDefinition, "full"), 1);
        String material = full == null
                ? net.minecraft.util.text.translation.I18n.translateToLocal("tile.stoneSlab.stone.name")
                : full.getDisplayName();

        return PieceNames.displayName(piece.palette.namespace, materialDefinition.id,
                piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT), material);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos position, EnumHand hand,
            EnumFacing face, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);

        if (stack.isEmpty()) {
            return EnumActionResult.FAIL;
        }

        String materialId = piece.palette.material(stack.getMetadata());
        Shape shape = piece.palette.shape;

        if (shape == Shape.VERTICAL_SLAB && Pieces.state(materialId, Shape.SLAB, 1) != null) {
            shape = Shape.SLAB;
        }

        return place(stack, player, world, position, hand, face, hitX, hitY, hitZ, materialId, shape);
    }

    static EnumActionResult place(ItemStack stack, EntityPlayer player, World world, BlockPos position, EnumHand hand,
            EnumFacing face, float hitX, float hitY, float hitZ, String materialId, Shape shape) {
        if (stack == null || stack.isEmpty()) {
            return EnumActionResult.FAIL;
        }

        float targetX = hitX;
        float targetY = hitY;
        float targetZ = hitZ;

        IBlockState existingState = world.getBlockState(position);

        if (!existingState.getBlock().isReplaceable(world, position)) {
            if (combine(world, position, existingState, materialId, shape, player, stack, face, hitX, hitY, hitZ)) {
                return EnumActionResult.SUCCESS;
            }

            position = position.offset(face);
            existingState = world.getBlockState(position);
            // Moving to the next block reverses the clicked boundary's local coordinates.
            targetX -= face.getXOffset();
            targetY -= face.getYOffset();
            targetZ -= face.getZOffset();
        }
        if (!player.canPlayerEdit(position, face, stack)) {
            return EnumActionResult.FAIL;
        }
        if (!existingState.getBlock().isReplaceable(world, position)) {
            return combine(world, position, existingState, materialId, shape, player, stack, face, targetX, targetY,
                    targetZ) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
        }

        int horizontalRotation = closest(hitX, hitZ);
        int orientation;
        Shape placedShape = shape;

        switch (shape) {
            case SLAB:
                EnumFacing slabSide = SlabPlacement.side(face, hitX, hitY, hitZ);
                placedShape = SlabPlacement.shape(slabSide);
                orientation = SlabPlacement.orientation(slabSide);
                break;
            case VERTICAL_SLAB:
                orientation = SlabPlacement.orientation(SlabPlacement.verticalSide(face, hitX, hitY, hitZ));
                break;
            case HORIZONTAL_STEP:
                orientation = horizontalRotation
                        + (face == EnumFacing.DOWN || face.getAxis() != EnumFacing.Axis.Y && hitY > .5f ? 0 : 4);
                break;
            case VERTICAL_STEP:
                orientation = corner(hitX, hitZ);
                break;
            case STEP:
                orientation = player.isSneaking() ? 8 + corner(hitX, hitZ)
                        : horizontalRotation
                                + (face == EnumFacing.DOWN || face.getAxis() != EnumFacing.Axis.Y && hitY > .5f ? 0
                                        : 4);
                break;
            case CORNER:
                orientation = corner(hitX, hitZ)
                        + (face == EnumFacing.DOWN || face.getAxis() != EnumFacing.Axis.Y && hitY > .5f ? 0 : 4);
                break;
            case STAIRS:
                EnumFacing playerFacing = player.getHorizontalFacing();
                orientation = (playerFacing == EnumFacing.EAST ? 0
                        : playerFacing == EnumFacing.WEST ? 1 : playerFacing == EnumFacing.SOUTH ? 2 : 3)
                        + (face == EnumFacing.DOWN || face.getAxis() != EnumFacing.Axis.Y && hitY > .5f ? 4 : 0);
                break;
            default:
                orientation = 0;
        }

        IBlockState target = Pieces.state(materialId, placedShape, orientation);

        if (shape == Shape.HORIZONTAL_STEP && player.isSneaking()) {
            target = Pieces.state(materialId, Shape.VERTICAL_STEP, corner(hitX, hitZ));
        }
        // Check the chosen orientation, not the block's default collision box.
        if (target == null || !world.mayPlace(target.getBlock(), position, true, face, player)
                || !safe(world, position, target)) {
            return EnumActionResult.FAIL;
        }
        if (!world.setBlockState(position, target, 3)) {
            return EnumActionResult.FAIL;
        }

        target.getBlock().onBlockPlacedBy(world, position, target, player, stack);
        finish(world, position, target, player, stack);
        return EnumActionResult.SUCCESS;
    }

    public boolean canPlaceBlockOnSide(World world, BlockPos position, EnumFacing side, EntityPlayer player,
            ItemStack itemStack) {
        return player.canPlayerEdit(position, side, itemStack);
    }

    /** Combine occupied cells only when their union is a supported shape of the same material. */
    private static boolean combine(World world, BlockPos position, IBlockState existingState, String material,
            Shape shape, EntityPlayer player, ItemStack stack, EnumFacing face, float hitX, float hitY, float hitZ) {
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
            IBlockState nativeSlab = Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "slab");

            if (nativeSlab == null || !(existingState.getBlock() instanceof net.minecraft.block.BlockSlab)
                    || existingState.getBlock() != nativeSlab.getBlock()
                    || !existingState
                            .withProperty(net.minecraft.block.BlockSlab.HALF,
                                    net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM)
                            .equals(nativeSlab.withProperty(net.minecraft.block.BlockSlab.HALF,
                                    net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM))) {
                return false;
            }

            existingCells = Geometry.mask(Shape.SLAB,
                    existingState.getValue(
                            net.minecraft.block.BlockSlab.HALF) == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP ? 0
                                    : 1);
        }

        // Nudge seam clicks towards the empty half, including clicks exactly on the centre plane.
        float epsilon = .0001f;
        int clickedCell = (hitX + face.getXOffset() * epsilon >= .5f ? 1 : 0)
                | (hitZ + face.getZOffset() * epsilon >= .5f ? 2 : 0)
                | (hitY + face.getYOffset() * epsilon >= .5f ? 4 : 0);
        IBlockState combinedState = null;
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

        if (combinedState == null || !safe(world, position, combinedState)
                || !world.setBlockState(position, combinedState, 3)) {
            return false;
        }

        finish(world, position, combinedState, player, stack);
        return true;
    }

    private static boolean safe(World world, BlockPos position, IBlockState target) {
        if (target.getBlock() instanceof PieceBlock) {
            for (net.minecraft.util.math.AxisAlignedBB boundingBox : ((PieceBlock) target.getBlock())
                    .boxes(target.getBlock().getActualState(target, world, position))) {
                if (!world.checkNoEntityCollision(boundingBox.offset(position))) {
                    return false;
                }
            }
        }

        return !(target.getBlock() instanceof PieceBlock)
                ? world.checkNoEntityCollision(new net.minecraft.util.math.AxisAlignedBB(position))
                : true;
    }

    private static void finish(World world, BlockPos position, IBlockState target, EntityPlayer player,
            ItemStack stack) {
        net.minecraft.block.SoundType sound = target.getBlock().getSoundType(target, world, position, player);

        world.playSound(player, position, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1) / 2,
                sound.getPitch() * .8f);

        if (!player.capabilities.isCreativeMode) {
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
