package zone.moddev.mc.skysbuildingpieces;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.compat.NativeStateHooks;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import static org.junit.jupiter.api.Assertions.*;

class NativeHardnessHookTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.registerVanilla();
    }

    @Test
    void materialHardnessChangesOnlyPieceStates() {
        var palette = Catalogue.INSTANCE.palettes.stream()
                .filter(entry -> entry.materials.contains("minecraft:obsidian")).findFirst().orElseThrow();
        PieceBlock block = new PieceBlock(palette);
        var state = block.getStateFromMeta(palette.materials.indexOf("minecraft:obsidian") * palette.shape.states);
        var world = EmptyBlockGetter.INSTANCE;
        float obsidian = Blocks.OBSIDIAN.defaultBlockState().getDestroySpeed(world, BlockPos.ZERO);
        assertEquals(obsidian, NativeStateHooks.destroySpeed(1, state, world, BlockPos.ZERO));
        assertEquals(7, NativeStateHooks.destroySpeed(7, Blocks.STONE.defaultBlockState(), world, BlockPos.ZERO));
    }

    @Test
    void nativeGetterStillHasTheRequiredReturnType() throws Exception {
        LoaderHooksTest.assertNativeReturn("net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase",
                "getDestroySpeed", "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
                org.objectweb.asm.Opcodes.FRETURN);
    }
}
