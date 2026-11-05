package zone.moddev.mc.skysbuildingpieces.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.MinecraftTestBootstrap;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousGlassBakeTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.registerVanilla();
    }

    @Test
    void onlyGlassWallBlockStatesAreWrappedAndAliasesStayShared() {
        Map<BlockState, BlockStateModel> models = new LinkedHashMap<>();
        BlockStateModel base = new EmptyModel();
        BlockStateModel missing = new EmptyModel();
        PieceBlock clear = wall("clear_glass");
        BlockState wall = clear.defaultBlockState();
        BlockState alias = wall.setValue(PieceBlock.CONNECTIONS, 10);
        BlockState absent = wall("stained_glass").defaultBlockState();
        BlockState other = Blocks.COBBLESTONE_WALL.defaultBlockState();
        models.put(wall, base);
        models.put(alias, base);
        models.put(other, base);
        models.put(absent, missing);

        ContinuousGlassModel.install(models, missing);

        BlockStateModel wrapped = models.get(wall);
        assertInstanceOf(ContinuousGlassModel.class, wrapped);
        assertSame(wrapped, models.get(alias));
        assertSame(base, models.get(other));
        assertSame(missing, models.get(absent));
        ContinuousGlassModel.install(models, missing);
        assertSame(wrapped, models.get(wall));
    }

    @Test
    void aResourceReloadUsesTheNewBaseModel() {
        BlockState state = wall("clear_glass").defaultBlockState();
        Map<BlockState, BlockStateModel> models = new LinkedHashMap<>();
        models.put(state, new EmptyModel());
        ContinuousGlassModel.install(models, null);
        BlockStateModel previous = models.get(state);

        models.put(state, new EmptyModel());
        ContinuousGlassModel.install(models, null);
        assertInstanceOf(ContinuousGlassModel.class, models.get(state));
        assertNotSame(previous, models.get(state));
    }

    private static PieceBlock wall(String materialGroup) {
        Catalogue.Palette palette = Catalogue.INSTANCE.palettes.stream()
                .filter(entry -> entry.shape == Shape.WALL && entry.group.equals(materialGroup))
                .findFirst().orElseThrow();
        return new PieceBlock(palette);
    }

    private static final class EmptyModel implements BlockStateModel {
        @Override
        public void collectParts(RandomSource random, List<BlockModelPart> parts) {
        }

        @Override
        public TextureAtlasSprite particleIcon() {
            return null;
        }
    }
}
