package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.config.BuildingPiecesConfig;

/** One material and shape per inventory item. */
public final class PieceItem extends BlockItem {
    public final PieceBlock piece;
    public final String material;

    public PieceItem(PieceBlock block, String material) {
        super(block, new Item.Properties());
        this.piece = block;
        this.material = material;
    }

    boolean visibleInCreativeTab() {
        Catalogue.MaterialDef definition = Catalogue.INSTANCE.materials.get(material);
        return piece.palette.shape != Shape.VERTICAL_SLAB && definition.replacement() == null
                && Pieces.nativeState(definition, piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT)) == null;
    }

    @Override
    public Component getName(ItemStack stack) {
        ItemStack full = Pieces.stack(Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "full"), 1);

        if (full == null) {
            full = Pieces.stack(Pieces.nativeState(Catalogue.INSTANCE.materials.get(material), "slab"), 1);
        }

        String namespace = piece.palette.module.equals("mineralogy")
                ? zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat.ASSETS
                : piece.palette.namespace;

        return PieceNames.displayName(namespace, material,
                piece.palette.shape.name().toLowerCase(java.util.Locale.ROOT), full.getHoverName());
    }

    @Override
    public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> recipeType) {
        Catalogue.MaterialDef definition = Catalogue.INSTANCE.materials.get(material);
        if (!definition.type.equals("wood")) {
            // NeoForge rejects negative burn times. Keep its normal data-map lookup
            // so non-fuels return zero and modpacks can still configure other fuels.
            return super.getBurnTime(stack, recipeType);
        }
        ItemStack full = Pieces.stack(Pieces.nativeState(definition, "full"), 1);
        int fullBurnTime = full == null ? 0 : Math.max(0, full.getBurnTime(recipeType));
        // Cutting a plank into smaller pieces must not create extra furnace fuel.
        return switch (piece.palette.shape) {
            case SLAB, VERTICAL_SLAB -> fullBurnTime / 2;
            case HORIZONTAL_STEP, VERTICAL_STEP, STEP -> fullBurnTime / 4;
            case CORNER -> fullBurnTime / 8;
            default -> fullBurnTime;
        };
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();

        if (player == null || context.getItemInHand().isEmpty()) {
            return InteractionResult.FAIL;
        }

        BlockPos position = context.getClickedPos();
        Vec3 hit = context.getClickLocation();
        Shape shape = piece.palette.shape;

        if (shape == Shape.VERTICAL_SLAB && Pieces.state(material, Shape.SLAB, 1) != null) {
            shape = Shape.SLAB;
        }

        return place(context.getItemInHand(), player, context.getLevel(), position, context.getHand(), context.getClickedFace(),
                (float) (hit.x - position.getX()), (float) (hit.y - position.getY()), (float) (hit.z - position.getZ()),
                material, shape);
    }

    static InteractionResult place(ItemStack stack, Player player, Level world, BlockPos position, InteractionHand hand,
            Direction face, float hitX, float hitY, float hitZ, String materialId, Shape shape) {
        if (stack == null || stack.isEmpty()) {
            return InteractionResult.FAIL;
        }

        float targetX = hitX;
        float targetY = hitY;
        float targetZ = hitZ;

        BlockState existingState = world.getBlockState(position);

        // Vanilla slabs call a matching half replaceable. Join it before asking the placement context whether this cell
        // can accept a new block.
        if (combine(world, position, existingState, materialId, shape, player, stack, face, hitX, hitY, hitZ)) {
            return InteractionResult.SUCCESS;
        }
        if (!replaceable(world, position, player, hand, stack, face, hitX, hitY, hitZ)) {
            position = position.relative(face);
            existingState = world.getBlockState(position);
            targetX -= face.getStepX();
            targetY -= face.getStepY();
            targetZ -= face.getStepZ();

            if (combine(world, position, existingState, materialId, shape, player, stack, face, targetX, targetY,
                    targetZ)) {
                return InteractionResult.SUCCESS;
            }
        }
        if (!player.mayUseItemAt(position, face, stack)
                || !replaceable(world, position, player, hand, stack, face, targetX, targetY, targetZ)) {
            return InteractionResult.FAIL;
        }

        int horizontalRotation = closest(hitX, hitZ);
        int orientation;
        Shape placedShape = shape;

        switch (shape) {
            case SLAB:
                Direction slabSide = BuildingPiecesConfig.directionalSlabPlacement()
                        ? SlabPlacement.side(face, hitX, hitY, hitZ) : SlabPlacement.horizontalSide(face, hitY);
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
                orientation = player.isShiftKeyDown() ? 8 + corner(hitX, hitZ)
                        : horizontalRotation
                                + (face == Direction.DOWN || face.getAxis() != Direction.Axis.Y && hitY > .5f ? 0 : 4);
                break;
            case CORNER:
                orientation = corner(hitX, hitZ)
                        + (face == Direction.DOWN || face.getAxis() != Direction.Axis.Y && hitY > .5f ? 0 : 4);
                break;
            case STAIRS:
                Direction playerFacing = player.getDirection();
                orientation = (playerFacing == Direction.EAST ? 0
                        : playerFacing == Direction.WEST ? 1 : playerFacing == Direction.SOUTH ? 2 : 3)
                        + (face == Direction.DOWN || face.getAxis() != Direction.Axis.Y && hitY > .5f ? 4 : 0);
                break;
            default:
                orientation = 0;
        }

        BlockState target = Pieces.state(materialId, placedShape, orientation);

        if (shape == Shape.HORIZONTAL_STEP && player.isShiftKeyDown()) {
            target = Pieces.state(materialId, Shape.VERTICAL_STEP, corner(hitX, hitZ));
        }
        if (target != null && target.hasProperty(SlabBlock.WATERLOGGED)) {
            boolean water = world.getFluidState(position).getType() == Fluids.WATER
                    || world.getFluidState(position).getType() == Fluids.FLOWING_WATER;

            target = target.setValue(SlabBlock.WATERLOGGED, water);
        }
        // Check the chosen orientation, not the block's default collision box.
        if (target == null || !target.canSurvive(world, position) || !safe(world, position, target)) {
            return InteractionResult.FAIL;
        }
        if (!world.setBlock(position, target, 3)) {
            return InteractionResult.FAIL;
        }

        target.getBlock().setPlacedBy(world, position, target, player, stack);
        finish(world, position, target, player, stack);
        return InteractionResult.SUCCESS;
    }

    public boolean canPlaceBlockOnSide(Level world, BlockPos position, Direction side, Player player,
            ItemStack itemStack) {
        return player.mayUseItemAt(position, side, itemStack);
    }

    /** Combine occupied cells only when their union is a supported shape of the same material. */
    private static boolean combine(Level world, BlockPos position, BlockState existingState, String material,
            Shape shape, Player player, ItemStack stack, Direction face, float hitX, float hitY, float hitZ) {
        if (!player.mayUseItemAt(position, face, stack) || shape == Shape.WALL || shape == Shape.PANE) {
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
        int clickedCell = (hitX + face.getStepX() * epsilon >= .5f ? 1 : 0)
                | (hitZ + face.getStepZ() * epsilon >= .5f ? 2 : 0)
                | (hitY + face.getStepY() * epsilon >= .5f ? 4 : 0);
        BlockState combinedState = null;
        boolean isSlab = shape == Shape.SLAB || shape == Shape.VERTICAL_SLAB;
        boolean directionalSlabs = isSlab && BuildingPiecesConfig.directionalSlabPlacement();
        if (isSlab && !directionalSlabs && Geometry.orientationFor(shape, existingCells) < 0) {
            return false;
        }
        boolean horizontalJoin = shape == Shape.SLAB && !directionalSlabs;
        if (horizontalJoin
                && (existingCells == 15 ? face == Direction.DOWN || face.getAxis().isHorizontal() && hitY <= .5f
                        : face == Direction.UP || face.getAxis().isHorizontal() && hitY > .5f)) {
            return false;
        }
        Shape[] additions = directionalSlabs ? new Shape[] { Shape.SLAB, Shape.VERTICAL_SLAB } : new Shape[] { shape };

        search:
        for (Shape addition : additions) {
            for (int orientation = 0; orientation < addition.states; orientation++) {
                int addedCells = Geometry.mask(addition, orientation);

                if ((!horizontalJoin && (addedCells & 1 << clickedCell) == 0)
                        || (addedCells & existingCells) != 0) {
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

        if (combinedState != null && combinedState.hasProperty(SlabBlock.WATERLOGGED)
                && existingState.hasProperty(SlabBlock.WATERLOGGED)) {
            combinedState = combinedState.setValue(SlabBlock.WATERLOGGED, existingState.getValue(SlabBlock.WATERLOGGED));
        }
        if (combinedState == null || !safe(world, position, combinedState)
                || !world.setBlock(position, combinedState, 3)) {
            return false;
        }

        finish(world, position, combinedState, player, stack);
        return true;
    }

    private static boolean safe(Level world, BlockPos position, BlockState target) {
        return world.isUnobstructed(target, position, CollisionContext.empty());
    }

    private static boolean replaceable(Level world, BlockPos position, Player player, InteractionHand hand, ItemStack stack,
            Direction face, float hitX, float hitY, float hitZ) {
        BlockHitResult hit = new BlockHitResult(
                new Vec3(position.getX() + hitX, position.getY() + hitY, position.getZ() + hitZ), face, position,
                false);

        return new PlacementContext(world, player, hand, stack, hit).replacingClickedOnBlock();
    }

    private static final class PlacementContext extends BlockPlaceContext {
        PlacementContext(Level world, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
            super(world, player, hand, stack, hit);
        }
    }

    private static void finish(Level world, BlockPos position, BlockState target, Player player,
            ItemStack stack) {
        net.minecraft.world.level.block.SoundType sound = target.getBlock().getSoundType(target, world, position, player);

        world.playSound(player, position, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2,
                sound.getPitch() * .8f);

        if (!player.getAbilities().instabuild) {
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
