package zone.moddev.mc.skysbuildingpieces.content;

import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Shovel paths use the original footprint, with the exposed top lowered by one pixel. */
public final class PathPieces {
    @SubscribeEvent
    public void allowShovelUse(PlayerInteractEvent.RightClickBlock event) {
        if (target(event.getWorld(), event.getPos(), event.getEntityPlayer(), event.getItemStack(), event.getFace()) == null) {
            return;
        }

        // Let block activation report SUCCESS, including while sneaking. Cancelling
        // this event reports PASS in Forge 1.10 and can also try the other hand.
        event.setUseBlock(Event.Result.ALLOW);
        event.setUseItem(Event.Result.DENY);
    }

    public static boolean activate(World world, BlockPos position, EntityPlayer player,
            ItemStack shovel, EnumFacing face) {
        IBlockState path = target(world, position, player, shovel, face);
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
        if (grassPiece && world.isBlockLoaded(support)
                && world.getBlockState(support).getBlock() == Blocks.GRASS
                && player.canPlayerEdit(support, EnumFacing.UP, shovel)) {
            // Flatten the exposed grass around the piece in the same shovel action.
            world.setBlockState(support, Blocks.GRASS_PATH.getDefaultState(), 3);
        }
        world.playSound(null, position, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1, 1);
        if (!player.capabilities.isCreativeMode) {
            shovel.damageItem(1, player);
        }
        return true;
    }

    private static IBlockState target(World world, BlockPos position, EntityPlayer player,
            ItemStack shovel, EnumFacing face) {
        if (shovel == null || face == null || face == EnumFacing.DOWN) {
            return null;
        }
        Set<String> tools = shovel.getItem().getToolClasses(shovel);
        if (tools == null || !tools.contains("shovel")) {
            return null;
        }
        if (!world.isAirBlock(position.up()) || !player.canPlayerEdit(position, face, shovel)) {
            return null;
        }
        return path(world.getBlockState(position));
    }

    private static IBlockState path(IBlockState source) {
        if (!(source.getBlock() instanceof PieceBlock)) {
            return null;
        }
        PieceBlock piece = (PieceBlock) source.getBlock();
        String material = piece.definition(source).id;
        Shape shape = piece.palette.shape;
        if ((!material.equals("minecraft:grass") && !material.equals("minecraft:dirt"))
                || (shape != Shape.STEP && shape != Shape.HORIZONTAL_STEP
                        && shape != Shape.VERTICAL_STEP && shape != Shape.CORNER)) {
            return null;
        }
        return Pieces.state("minecraft:grass_path", shape, piece.orientation(source));
    }

    static IBlockState dirt(IBlockState path) {
        PieceBlock piece = (PieceBlock) path.getBlock();
        return Pieces.state("minecraft:dirt", piece.palette.shape, piece.orientation(path));
    }

    static void checkCover(World world, BlockPos position, IBlockState state) {
        PieceBlock piece = (PieceBlock) state.getBlock();
        if (!world.isRemote && piece.palette.group.equals("path")
                && world.getBlockState(position.up()).getMaterial().isSolid()) {
            world.setBlockState(position, dirt(state), 3);
        }
    }
}
