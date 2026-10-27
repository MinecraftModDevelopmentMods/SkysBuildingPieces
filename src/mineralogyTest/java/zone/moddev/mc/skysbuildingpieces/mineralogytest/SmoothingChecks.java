package zone.moddev.mc.skysbuildingpieces.mineralogytest;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.content.PieceBlock;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

final class SmoothingChecks {
    private SmoothingChecks() {
    }

    /** Audit pieces produced during normal spawn generation, before the placement tests write anything. */
    static String generated(WorldServer world) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        int steps = 0;
        int corners = 0;
        int centerX = world.getSpawnPoint().getX() >> 4;
        int centerZ = world.getSpawnPoint().getZ() >> 4;
        for (int chunkZ = centerZ - 4; chunkZ <= centerZ + 4; chunkZ++) {
            for (int chunkX = centerX - 4; chunkX <= centerX + 4; chunkX++) {
                // The exhaustive placement grid uses chunk zero, not natural terrain.
                if (chunkX == 0 && chunkZ == 0) continue;
                net.minecraft.world.chunk.Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                RuntimeProbe.require(chunk != null, "The spawn audit would load a missing chunk");
                for (int height = 0; height < 256; height++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int localX = 0; localX < 16; localX++) {
                            IBlockState state = chunk.getBlockState(new net.minecraft.util.math.BlockPos(localX, height, localZ));
                            if (!state.getBlock().getRegistryName().toString().startsWith("skysbuildingpieces:mineralogy_")) continue;
                            PieceBlock piece = (PieceBlock) state.getBlock();
                            int cells = Integer.bitCount(piece.mask(state));
                            if (cells == 1) corners++; else if (cells == 2) steps++;
                            String entry = chunkX + "," + chunkZ + "," + localX + "," + height + "," + localZ + ":" + state + "\n";
                            digest.update(entry.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        }
        int level = Integer.getInteger("mineralogycore.smoothingLevel", 3);
        boolean enabled = Integer.getInteger("mineralogycore.expectedMappings", 24) != 0;
        RuntimeProbe.require(level > 1 && enabled || steps == 0, "Level one or disabled integration generated steps");
        RuntimeProbe.require(level == 3 && enabled || corners == 0, "A lower level generated corners");
        if (enabled && level >= 2) RuntimeProbe.require(steps > 0, "Normal terrain generation produced no Mineralogy steps");
        if (enabled && level == 3) RuntimeProbe.require(corners > 0, "Normal terrain generation produced no Mineralogy corners");
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format("%02x", value & 255));
        System.out.println("MINERALOGY_GENERATION_PASS level=" + level + " steps=" + steps + " corners=" + corners + " digest=" + hash);
        return steps + "/" + corners + "/" + hash;
    }

    static int run(WorldServer world) {
        int mappings = 0;
        for (String identity : Catalogue.INSTANCE.modules.get("mineralogy").materials.keySet()) {
            TerrainMaterial material = MaterialCatalogue.instance().material(new ResourceLocation(identity));
            if (material == null) continue;
            RuntimeProbe.require(material.slab.getBlock().getRegistryName().getResourceDomain().equals("mineralogy"),
                    "The native slab was replaced");
            for (int direction = 0; direction < 4; direction++) {
                IBlockState step = material.step(direction);
                IBlockState corner = material.corner(direction);
                RuntimeProbe.require(step != null && corner != null, "Missing finer smoothing shapes: " + identity);
                RuntimeProbe.require(step.getBlock().getRegistryName().toString().startsWith("skysbuildingpieces:mineralogy_"),
                        "Unexpected step owner");
                RuntimeProbe.require(Integer.bitCount(((PieceBlock) step.getBlock()).mask(step)) == 2,
                        "A step is not quarter volume");
                RuntimeProbe.require(Integer.bitCount(((PieceBlock) corner.getBlock()).mask(corner)) == 1,
                        "A corner is not eighth volume");
                RuntimeProbe.require((((PieceBlock) step.getBlock()).mask(step) & 240) == 0,
                        "A smoothing step is upside-down");
            }
            mappings++;
        }
        int expected = Integer.getInteger("mineralogycore.expectedMappings", 24);
        RuntimeProbe.require(mappings == expected, "Expected " + expected + " existing Mineralogy mappings, got " + mappings);
        System.out.println("MINERALOGY_FINER_SHAPES_PASS mappings=" + mappings);
        return mappings;
    }
}
