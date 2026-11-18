package zone.moddev.mc.skysbuildingpieces.client;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
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
    private final Map<PartKey, BlockStateModelPart> cache = new ConcurrentHashMap<>();

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
        return original.getModelData(world, position, state, existing == null ? ModelData.EMPTY : existing)
                .derive().with(JOINS, joins).build();
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        original.collectParts(random, parts);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts,
            ModelData data) {
        data = data == null ? ModelData.EMPTY : data;
        int firstPart = parts.size();
        original.collectParts(random, parts, data);
        Integer joins = data.get(JOINS);
        if (joins == null || joins == 0) {
            return;
        }

        for (int index = firstPart; index < parts.size(); index++) {
            BlockStateModelPart part = parts.get(index);
            PartKey key = new PartKey(part, joins);
            BlockStateModelPart replacement = cache.get(key);

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
    public Material.Baked particleMaterial() {
        return original.particleMaterial();
    }

    @Override
    public Material.Baked particleMaterial(ModelData data) {
        return original.particleMaterial(data == null ? ModelData.EMPTY : data);
    }

    @Override
    public int materialFlags() {
        return original.materialFlags();
    }

    private record PartKey(BlockStateModelPart part, int joins) {}

    private static final class JoinedPart implements BlockStateModelPart {
        private final BlockStateModelPart original;
        private final Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
        private final List<BakedQuad> unculled;

        private JoinedPart(BlockStateModelPart original, int joins) {
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
        public Material.Baked particleMaterial() {
            return original.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return original.materialFlags();
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
                quad.direction(), quad.materialInfo());
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
