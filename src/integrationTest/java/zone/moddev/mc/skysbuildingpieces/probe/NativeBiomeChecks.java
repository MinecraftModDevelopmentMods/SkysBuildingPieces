package zone.moddev.mc.skysbuildingpieces.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.fixes.CavesAndCliffsRenames;
import net.minecraft.world.level.ChunkPos;

/** Checks the complete saved biome volume, independently of piece placement. */
public final class NativeBiomeChecks {
    private NativeBiomeChecks() {
    }

    private static JsonObject contract() throws Exception {
        JsonObject data = new JsonParser().parse(Files.readString(Path.of("forward-biomes.json"))).getAsJsonObject();
        JsonObject fixture = new JsonParser().parse(Files.readString(Path.of("forward-fixture.json"))).getAsJsonObject();
        require(data.get("format").getAsString().equals("zlib-u8-quart-volume-v1"), "Unknown biome contract");
        require(data.get("source_world_sha256").equals(fixture.get("archive_sha256")), "Wrong source biome archive");
        require(data.get("source_minecraft").equals(fixture.get("minecraft")), "Wrong source biome version");
        return data;
    }

    public static void prepare(ServerLevel world, Set<ChunkPos> tracked) throws Exception {
        for (JsonElement value : contract().getAsJsonArray("chunks")) {
            JsonObject row = value.getAsJsonObject();
            ChunkPos position = new ChunkPos(row.get("x").getAsInt(), row.get("z").getAsInt());
            if (tracked.add(position)) {
                world.setChunkForced(position.x, position.z, true);
                world.getChunk(position.x, position.z);
            }
        }
    }

    public static void verify(ServerLevel world) throws Exception {
        JsonObject data = contract();
        JsonObject names = data.getAsJsonObject("source_names");
        var registry = world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        long cells = 0;
        long renamed = 0;
        for (JsonElement entry : data.getAsJsonArray("chunks")) {
            JsonObject row = entry.getAsJsonObject();
            int chunkX = row.get("x").getAsInt();
            int chunkZ = row.get("z").getAsInt();
            byte[] source;
            try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(
                    Base64.getDecoder().decode(row.get("cells").getAsString())))) {
                source = input.readAllBytes();
            }
            require(source.length == 1024, "The source biome volume changed");
            var chunk = world.getChunk(chunkX, chunkZ);
            for (int cell = 0; cell < source.length; cell++) {
                String oldName = names.get(Integer.toString(Byte.toUnsignedInt(source[cell]))).getAsString();
                String expected = CavesAndCliffsRenames.RENAMES.getOrDefault(oldName, oldName);
                if (expected.equals("minecraft:deep_warm_ocean")) {
                    expected = "minecraft:warm_ocean";
                }
                var actual = registry.getKey(chunk.getNoiseBiome((chunkX << 2) + (cell & 3),
                        cell >> 4, (chunkZ << 2) + (cell >> 2 & 3)).value());
                require(actual != null && actual.toString().equals(expected),
                        "Native biome conversion at " + chunkX + "," + chunkZ + " cell=" + cell
                                + " expected=" + expected + " actual=" + actual);
                cells++;
                if (!oldName.equals(expected)) {
                    renamed++;
                }
            }
        }
        require(cells == data.get("checked_cells").getAsLong(), "Incomplete biome audit");
        require(renamed == data.get("native_renamed_cells").getAsLong(), "Native rename coverage changed");
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BUILDING_PIECES_NATIVE_BIOME_PASS source={} cells={} renamed={}",
                data.get("source_minecraft").getAsString(), cells, renamed);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
