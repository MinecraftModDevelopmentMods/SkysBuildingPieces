package zone.moddev.mc.skysbuildingpieces.content;

import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Flatten a piece and its exposed grass support in one shovel action. */
public final class PathPieces {
    @SubscribeEvent
    public void allowShovelUse(PlayerInteractEvent.RightClickBlock event) {
        if (target(event.getWorld(), event.getPos(), event.getPlayer(), event.getItemStack(),
                event.getFace()) == null) {
            return;
        }
        // Forge 36 still bypasses block activation while sneaking, even with UseBlock.ALLOW. Finish this valid action
        // here and stop the vanilla call.
        if (event.getPlayer().isShiftKeyDown() && activate(event.getWorld(), event.getPos(), event.getPlayer(),
                event.getItemStack(), event.getFace(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.util.ActionResultType.SUCCESS);
            return;
        }

        event.setUseBlock(Event.Result.ALLOW);
        event.setUseItem(Event.Result.DENY);
    }

    public static boolean activate(World world, BlockPos position, PlayerEntity player, ItemStack shovel,
            Direction face, net.minecraft.util.Hand hand) {
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
            world.setBlock(support, Blocks.GRASS_PATH.defaultBlockState(), 3);
        }

        world.playSound(null, position, SoundEvents.SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1, 1);

        if (!player.abilities.instabuild) {
            shovel.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(hand));
        }

        return true;
    }

    private static BlockState target(World world, BlockPos position, PlayerEntity player, ItemStack shovel,
            Direction face) {
        if (player == null || shovel == null || shovel.isEmpty() || face == null || face == Direction.DOWN) {
            return null;
        }

        Set<ToolType> tools = shovel.getItem().getToolTypes(shovel);

        if (tools == null || !tools.contains(ToolType.SHOVEL) || !world.isEmptyBlock(position.above())
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

    static void checkCover(World world, BlockPos position, BlockState state) {
        if (world.isClientSide || !(state.getBlock() instanceof PieceBlock)) {
            return;
        }

        PieceBlock piece = (PieceBlock) state.getBlock();

        if (piece.palette.group.equals("path")
                && (state.getValue(SlabBlock.WATERLOGGED) || world.getBlockState(position.above()).getMaterial().isSolid())) {
            world.setBlock(position, dirt(state), 3);
        }
    }
}
