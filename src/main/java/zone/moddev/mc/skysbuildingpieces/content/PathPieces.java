package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Flatten a piece and its exposed grass support in one shovel action. */
public final class PathPieces {
    @SubscribeEvent
    public void allowShovelUse(PlayerInteractEvent.RightClickBlock event) {
        if (target(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack(),
                event.getFace()) == null) {
            return;
        }
        // Sneaking bypasses block activation. Finish this valid use here so the
        // shovel acts once, including when a step sits above grass.
        if (event.getEntity().isShiftKeyDown() && activate(event.getLevel(), event.getPos(), event.getEntity(),
                event.getItemStack(), event.getFace(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }

        event.setUseBlock(Event.Result.ALLOW);
        event.setUseItem(Event.Result.DENY);
    }

    public static boolean activate(Level world, BlockPos position, Player player, ItemStack shovel,
            Direction face, net.minecraft.world.InteractionHand hand) {
        BlockState path = target(world, position, player, shovel, face);

        if (path == null) {
            return false;
        }
        if (world.isClientSide) {
            return true;
        }

        boolean grassPiece = GrassLifecycle.grass(world.getBlockState(position));

        if (!world.setBlock(position, path, 11)) {
            return false;
        }

        BlockPos support = position.below();

        if (grassPiece && world.isLoaded(support) && world.getBlockState(support).getBlock() == Blocks.GRASS_BLOCK
                && player.mayUseItemAt(support, Direction.UP, shovel)) {
            world.setBlock(support, Blocks.DIRT_PATH.defaultBlockState(), 3);
        }

        world.playSound(null, position, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1, 1);

        if (!player.getAbilities().instabuild) {
            shovel.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(hand));
        }

        return true;
    }

    private static BlockState target(Level world, BlockPos position, Player player, ItemStack shovel,
            Direction face) {
        if (player == null || shovel == null || shovel.isEmpty() || face == null || face == Direction.DOWN) {
            return null;
        }

        if (!shovel.canPerformAction(ToolActions.SHOVEL_FLATTEN) || !world.isEmptyBlock(position.above())
                || !player.mayUseItemAt(position, face, shovel)) {
            return null;
        }

        BlockState source = world.getBlockState(position);

        if (!(source.getBlock() instanceof PieceBlock) || source.getValue(SlabBlock.WATERLOGGED)) {
            return null;
        }

        PieceBlock piece = (PieceBlock) source.getBlock();
        String material = piece.definition(source).id;
        Shape shape = piece.palette.shape;

        if ((!material.equals("minecraft:grass") && !material.equals("minecraft:dirt")) || (shape != Shape.STEP
                && shape != Shape.HORIZONTAL_STEP && shape != Shape.VERTICAL_STEP && shape != Shape.CORNER)) {
            return null;
        }

        return Pieces.state("minecraft:grass_path", shape, piece.orientation(source));
    }

    static BlockState dirt(BlockState path) {
        PieceBlock piece = (PieceBlock) path.getBlock();
        BlockState dirt = Pieces.state("minecraft:dirt", piece.palette.shape, piece.orientation(path));

        return dirt.setValue(SlabBlock.WATERLOGGED, path.getValue(SlabBlock.WATERLOGGED));
    }

    static void checkCover(Level world, BlockPos position, BlockState state) {
        if (world.isClientSide || !(state.getBlock() instanceof PieceBlock)) {
            return;
        }

        PieceBlock piece = (PieceBlock) state.getBlock();

        if (piece.palette.group.equals("path")
                && (state.getValue(SlabBlock.WATERLOGGED) || world.getBlockState(position.above()).isSolid())) {
            world.setBlock(position, dirt(state), 3);
        }
    }
}
