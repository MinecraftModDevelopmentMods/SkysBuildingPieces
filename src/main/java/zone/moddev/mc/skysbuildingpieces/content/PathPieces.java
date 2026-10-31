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
        // Forge 28 still bypasses block activation while sneaking, even with UseBlock.ALLOW. Finish this valid action
        // here and stop the vanilla call.
        if (event.getPlayer().isSneaking() && activate(event.getWorld(), event.getPos(), event.getPlayer(),
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
        if (world.isRemote) {
            return true;
        }

        boolean grassPiece = GrassLifecycle.grass(world.getBlockState(position));

        if (!world.setBlockState(position, path, 11)) {
            return false;
        }

        BlockPos support = position.down();

        if (grassPiece && world.isBlockPresent(support) && world.getBlockState(support).getBlock() == Blocks.GRASS_BLOCK
                && player.canPlayerEdit(support, Direction.UP, shovel)) {
            world.setBlockState(support, Blocks.GRASS_PATH.getDefaultState(), 3);
        }

        world.playSound(null, position, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1, 1);

        if (!player.abilities.isCreativeMode) {
            shovel.damageItem(1, player, entity -> entity.sendBreakAnimation(hand));
        }

        return true;
    }

    private static BlockState target(World world, BlockPos position, PlayerEntity player, ItemStack shovel,
            Direction face) {
        if (player == null || shovel == null || shovel.isEmpty() || face == null || face == Direction.DOWN) {
            return null;
        }

        Set<ToolType> tools = shovel.getItem().getToolTypes(shovel);

        if (tools == null || !tools.contains(ToolType.SHOVEL) || !world.isAirBlock(position.up())
                || !player.canPlayerEdit(position, face, shovel)) {
            return null;
        }

        BlockState source = world.getBlockState(position);

        if (!(source.getBlock() instanceof PieceBlock) || source.get(SlabBlock.WATERLOGGED)) {
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

        return dirt.with(SlabBlock.WATERLOGGED, path.get(SlabBlock.WATERLOGGED));
    }

    static void checkCover(World world, BlockPos position, BlockState state) {
        if (world.isRemote || !(state.getBlock() instanceof PieceBlock)) {
            return;
        }

        PieceBlock piece = (PieceBlock) state.getBlock();

        if (piece.palette.group.equals("path")
                && (state.get(SlabBlock.WATERLOGGED) || world.getBlockState(position.up()).getMaterial().isSolid())) {
            world.setBlockState(position, dirt(state), 3);
        }
    }
}
