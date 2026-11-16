package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Standalone equivalent of Grass Slabs' calculated snow cap. */
final class SnowAppearance {
    private SnowAppearance() {
    }

    static boolean hasNearbySnow(IBlockAccess access, BlockPos pos) {
        if (SnowCover.isSnowPiece(readState(access, pos.up()))) {
            return true;
        }
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos neighbourPos = pos.offset(facing);
            IBlockState neighbour = readState(access, neighbourPos);
            if (SnowCover.isSnowPiece(neighbour) || neighbour.isFullCube()
                    && SnowCover.isSnowPiece(readState(access, neighbourPos.up()))) {
                return true;
            }
        }
        World world = weatherWorld(access);
        return world != null && pos.getY() >= 0 && pos.getY() < 256 && world.isBlockLoaded(pos)
                && !world.provider.hasNoSky() && world.getBiome(pos).getFloatTemperature(pos) < 0.15F
                && world.getPrecipitationHeight(pos).getY() <= pos.getY() + 1
                && world.getLightFor(EnumSkyBlock.BLOCK, pos.up()) < 10;
    }

    private static World weatherWorld(IBlockAccess access) {
        if (access instanceof World) {
            return (World) access;
        }
        if (access instanceof ChunkCache) {
            try {
                return (World) CacheWorld.WORLD.get(access);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read weather from Minecraft's chunk cache", failure);
            }
        }
        return null;
    }

    private static final class CacheWorld {
        static final java.lang.reflect.Field WORLD = ReflectionHelper.findField(
                ChunkCache.class, "world", "field_72815_e");
    }

    private static IBlockState readState(IBlockAccess world, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= 256
                || world instanceof World && !((World) world).isBlockLoaded(pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return world.getBlockState(pos);
    }
}
