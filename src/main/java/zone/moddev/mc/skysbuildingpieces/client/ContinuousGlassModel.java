package zone.moddev.mc.skysbuildingpieces.client;

import java.util.Collection;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.GlassWallTexture;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;

/** Keeps glass borders on exposed ends, posts and joins between different colours. */
public final class ContinuousGlassModel implements BlockStateModel {
    private static final ModelProperty<Integer> JOINS = new ModelProperty<>(
            joins -> joins != null && joins >= 0 && joins < 16);
    private static final Direction[] SIDES = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private final BlockStateModel original;
    private final Map<PartKey, BlockModelPart> cache = new ConcurrentHashMap<>();

    public ContinuousGlassModel(BlockStateModel original) {
        this.original = original;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter world, BlockPos position, BlockState state,
            ModelData existing) {
        PieceBlock wall = (PieceBlock) state.getBlock();
        String material = wall.definition(state).id;
        int joins = 0;

        for (int index = 0; index < SIDES.length; index++) {
            BlockState neighbour = world.getBlockState(position.relative(SIDES[index]));
            if (neighbour.getBlock() instanceof PieceBlock other
                    && other.palette.shape == Shape.WALL
                    && other.definition(neighbour).id.equals(material)) {
                joins |= 1 << index;
            }
        }
        return original.getModelData(world, position, state, existing).derive().with(JOINS, joins).build();
    }

    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> parts) {
        original.collectParts(random, parts);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> parts,
            ModelData data, ChunkSectionLayer layer) {
        int firstPart = parts.size();
        original.collectParts(random, parts, data, layer);
        Integer joins = data.get(JOINS);
        if (joins == null || joins == 0) {
            return;
        }

        for (int index = firstPart; index < parts.size(); index++) {
            BlockModelPart part = parts.get(index);
            PartKey key = new PartKey(part, joins);
            BlockModelPart replacement = cache.get(key);

            if (replacement == null) {
                // Ordinary wall models reuse their parts. Keep replacements bounded
                // in case a resource pack creates new parts for every render.
                replacement = cache.size() < 128
                        ? cache.computeIfAbsent(key, entry -> new JoinedPart(entry.part(), entry.joins()))
                        : new JoinedPart(part, joins);
            }
            parts.set(index, replacement);
        }
    }

    @Override
    public TextureAtlasSprite particleIcon() {
        return original.particleIcon();
    }

    @Override
    public TextureAtlasSprite particleIcon(ModelData data) {
        return original.particleIcon(data);
    }

    @Override
    public Collection<ChunkSectionLayer> getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return original.getRenderTypes(state, random, data);
    }

    private record PartKey(BlockModelPart part, int joins) {}

    private static final class JoinedPart implements BlockModelPart {
        private final BlockModelPart original;
        private final Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
        private final List<BakedQuad> unculled;

        private JoinedPart(BlockModelPart original, int joins) {
            this.original = original;
            unculled = trim(original.getQuads(null), joins);
            for (Direction face : Direction.values()) {
                faces.put(face, trim(original.getQuads(face), joins));
            }
        }

        @Override
        public List<BakedQuad> getQuads(Direction face) {
            return face == null ? unculled : faces.get(face);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return original.useAmbientOcclusion();
        }

        @Override
        public TextureAtlasSprite particleIcon() {
            return original.particleIcon();
        }

        @Override
        public ChunkSectionLayer layer() {
            return original.layer();
        }

        @Override
        public ChunkSectionLayer layerFast() {
            return original.layerFast();
        }
    }

    private static List<BakedQuad> trim(List<BakedQuad> quads, int joins) {
        return quads.stream().map(quad -> trim(quad, joins)).toList();
    }

    private static BakedQuad trim(BakedQuad quad, int joins) {
        // Feed the same coordinates to the shared texture rule as on older versions.
        int[] vertices = new int[20];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 5;
            var position = quad.position(vertex);
            long uv = quad.packedUV(vertex);
            vertices[offset] = Float.floatToRawIntBits(position.x());
            vertices[offset + 1] = Float.floatToRawIntBits(position.y());
            vertices[offset + 2] = Float.floatToRawIntBits(position.z());
            vertices[offset + 3] = Float.floatToRawIntBits(UVPair.unpackU(uv));
            vertices[offset + 4] = Float.floatToRawIntBits(UVPair.unpackV(uv));
        }
        int[] trimmed = GlassWallTexture.trim(vertices, 5, 0, 3, joins);
        if (trimmed == vertices) {
            return quad;
        }

        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                uv(trimmed, 0), uv(trimmed, 1), uv(trimmed, 2), uv(trimmed, 3),
                quad.tintIndex(), quad.direction(), quad.sprite(), quad.shade(),
                quad.lightEmission(), quad.ambientOcclusion());
    }

    private static long uv(int[] vertices, int vertex) {
        int offset = vertex * 5;
        return UVPair.pack(Float.intBitsToFloat(vertices[offset + 3]),
                Float.intBitsToFloat(vertices[offset + 4]));
    }

    static void bake(ModelEvent.ModifyBakingResult event) {
        var results = event.getResults();
        install(results.blockStateModels(), results.missingModels().block());
    }

    static void install(Map<BlockState, BlockStateModel> models, BlockStateModel missing) {
        Map<BlockStateModel, ContinuousGlassModel> replacements = new IdentityHashMap<>();
        models.replaceAll((state, model) -> {
            if (state.getBlock() instanceof PieceBlock piece
                    && piece.palette.shape == Shape.WALL && piece.palette.group.contains("glass")
                    && model != missing && !(model instanceof ContinuousGlassModel)) {
                return replacements.computeIfAbsent(model, ContinuousGlassModel::new);
            }
            return model;
        });
    }
}
