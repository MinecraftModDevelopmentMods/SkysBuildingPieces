package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Checks raw saves before absent-mod loading can discard unsupported content. */
final class LegacyPreflight {
    static void validate(File world, Map<Integer, String> registryIds) throws IOException {
        final boolean[] candidates = new boolean[4096];

        for (Integer blockId : registryIds.keySet()) {
            String name = registryIds.get(blockId);

            if (blockId >= 0 && blockId < 4096 && (LegacyBridge.palette(name) != null
                    || HistoricalFullBlocks.historical(name) || name.startsWith("buildingbricks:") || skyOwned(name))) {
                candidates[blockId] = true;
            }
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(world.toPath())) {
            Iterator<Path> files = paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")).iterator();

            while (files.hasNext()) {
                File file = files.next().toFile();

                try (RandomAccessFile region = new RandomAccessFile(file, "r")) {
                    if (region.length() < 8192) {
                        throw blocked("malformed region header in " + file.getName());
                    }

                    int[] locations = new int[1024];

                    for (int chunkEntryIndex = 0; chunkEntryIndex < 1024; chunkEntryIndex++) {
                        locations[chunkEntryIndex] = region.readInt();
                    }
                    for (int location : locations) {
                        if (location != 0) {
                            long offset = (location >>> 8) * 4096L;

                            region.seek(offset);

                            int length = region.readInt();
                            int compression = region.readUnsignedByte();

                            if (length < 1 || length > (location & 255) * 4096 - 4
                                    || offset + 4 + length > region.length()) {
                                throw blocked("malformed chunk extent in " + file.getName());
                            }

                            byte[] payload = new byte[length - 1];

                            region.readFully(payload);

                            InputStream bytes = new ByteArrayInputStream(payload);

                            if (compression == 1) {
                                bytes = new java.util.zip.GZIPInputStream(bytes);
                            } else if (compression == 2) {
                                bytes = new java.util.zip.InflaterInputStream(bytes);
                            } else if (compression != 3) {
                                throw blocked("unknown region compression in " + file.getName());
                            }

                            CompoundNBT root;

                            try (DataInputStream input = new DataInputStream(bytes)) {
                                root = CompressedStreamTools.read(input);
                            }

                            CompoundNBT level = root.getCompound("Level");

                            HistoricalBiomes.validate(root);

                            {
                                Map<Integer, CompoundNBT> tiles = new HashMap<Integer, CompoundNBT>();
                                ListNBT tileList = level.getList("TileEntities", 10);

                                for (int tileIndex = 0; tileIndex < tileList.size(); tileIndex++) {
                                    CompoundNBT compoundTag = tileList.getCompound(tileIndex);

                                    tiles.put((compoundTag.getInt("x") & 15) | ((compoundTag.getInt("z") & 15) << 4)
                                            | (compoundTag.getInt("y") << 8), compoundTag);
                                }

                                ListNBT sections = level.getList("Sections", 10);

                                for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                                    CompoundNBT section = sections.getCompound(sectionIndex);

                                    if (!section.contains("Blocks", 7)) {
                                        continue; // Named 1.13+ states do not use the numeric bridge.
                                    }

                                    byte[] blockIdLowBytes = section.getByteArray("Blocks");
                                    byte[] blockIdHighNibbles = section.getByteArray("Add");
                                    byte[] meta = section.getByteArray("Data");

                                    if (blockIdLowBytes.length != 4096 || meta.length != 2048
                                            || blockIdHighNibbles.length != 0 && blockIdHighNibbles.length != 2048) {
                                        throw blocked("malformed section in " + file.getName());
                                    }
                                    for (int blockIndex = 0; blockIndex < 4096; blockIndex++) {
                                        int blockId = (blockIdLowBytes[blockIndex] & 255)
                                                | (LegacyBridge.nibble(blockIdHighNibbles, blockIndex) << 8);

                                        if (!candidates[blockId]) {
                                            continue;
                                        }

                                        String name = registryIds.get(blockId);
                                        CompoundNBT tile = tiles.get((blockIndex & 255)
                                                | (((section.getByte("Y") & 255) * 16 + (blockIndex >> 8)) << 8));

                                        zone.moddev.mc.skysbuildingpieces.content.PieceBlock palette = LegacyBridge
                                                .palette(name);

                                        if (palette != null) {
                                            palette.palette.material(LegacyBridge.nibble(meta, blockIndex));
                                            continue;
                                        }
                                        if ((!LegacyMapping.owned(name) && name.startsWith("buildingbricks:")
                                                && !grassOwned(name)
                                                && !net.minecraftforge.fml.ModList.get().isLoaded("buildingbricks"))
                                                || skyOwned(name) && !HistoricalFullBlocks.historical(name)) {
                                            throw blocked(name + " placed block");
                                        }
                                        if (grassOwned(name)) {
                                            continue;
                                        }
                                        if (HistoricalFullBlocks.historical(name)) {
                                            HistoricalFullBlocks.resolve(name, LegacyBridge.nibble(meta, blockIndex));
                                            continue;
                                        }
                                        if (!LegacyMapping.owned(name) || !LegacyBridge.active()) {
                                            continue;
                                        }

                                        String material = LegacyMapping
                                                .material(tile == null ? "" : tile.getString("material"));
                                        Shape shape = LegacyMapping.shape(name, LegacyBridge.nibble(meta, blockIndex));

                                        if (!LegacyMapping.allowedMaterial(material, shape)
                                                || Pieces.state(material, shape, LegacyMapping.orientation(shape,
                                                        LegacyBridge.nibble(meta, blockIndex))) == null) {
                                            throw blocked(name + " / " + material + " / " + shape + " in "
                                                    + file.getName() + " chunk " + level.getInt("xPos") + ", "
                                                    + level.getInt("zPos"));
                                        }
                                    }
                                }
                            }
                            stacks(root);
                        }
                    }
                }
            }
        }

        File levelFile = new File(world, "level.dat");

        if (levelFile.isFile()) {
            try (InputStream input = new FileInputStream(levelFile)) {
                stacks(CompressedStreamTools.readCompressed(input));
            }
        }

        File players = new File(world, "playerdata");
        File[] files = players.listFiles((directory, name) -> name.endsWith(".dat"));

        if (files != null) {
            for (File file : files) {
                try (InputStream input = new FileInputStream(file)) {
                    stacks(CompressedStreamTools.readCompressed(input));
                }
            }
        }
    }

    private static void stacks(INBT value) {
        inspectStacks(value instanceof CompoundNBT ? HistoricalItems.prepareCopy((CompoundNBT) value) : value);
    }

    private static void inspectStacks(INBT value) {
        if (value instanceof CompoundNBT) {
            CompoundNBT compoundTag = (CompoundNBT) value;
            String registryId = compoundTag.getString("id");

            if (compoundTag.contains("Count", 99)) {
                HistoricalFullBlocks.resolve(registryId, compoundTag.getShort("Damage"));

                zone.moddev.mc.skysbuildingpieces.content.PieceBlock palette = LegacyBridge.palette(registryId);

                if (palette != null) {
                    palette.palette.material(compoundTag.getShort("Damage"));
                }
                if (skyOwned(registryId) && palette == null && !HistoricalFullBlocks.historical(registryId)
                        && !net.minecraftforge.registries.ForgeRegistries.ITEMS
                                .containsKey(new net.minecraft.util.ResourceLocation(registryId))) {
                    throw blocked(registryId + " item; restore its Sky add-on and dependencies");
                }
                if (!net.minecraftforge.fml.ModList.get().isLoaded("buildingbricks")
                        && registryId.startsWith("buildingbricks:") && !LegacyMapping.owned(registryId)
                        && !grassOwned(registryId)) {
                    throw blocked(registryId + " item");
                }
            }
            if (compoundTag.contains("Count", 99) && LegacyBridge.active() && LegacyMapping.owned(registryId)) {
                String material = LegacyMapping.material(compoundTag.getCompound("tag").getString("material"));
                Shape shape = LegacyMapping.shape(registryId, 0);

                if (!LegacyMapping.allowedMaterial(material, shape)
                        || Pieces.state(material, shape, shape == Shape.SLAB ? 1 : 0) == null) {
                    throw blocked(registryId + " / " + material + " / " + shape + " item");
                }
            }
            for (String key : compoundTag.keySet()) {
                if (!key.equals("retained_tiles")) {
                    inspectStacks(compoundTag.get(key));
                }
            }
        } else if (value instanceof ListNBT) {
            ListNBT list = (ListNBT) value;

            for (int entryIndex = 0; entryIndex < list.size(); entryIndex++) {
                inspectStacks(list.get(entryIndex));
            }
        }
    }

    static boolean skyOwned(String id) {
        return id.startsWith("skysbuildingpieces:") || id.startsWith("skysbuildingpiecesbop:")
                || id.startsWith("skysbuildingpiecesmineralogy:");
    }

    private static boolean grassOwned(String id) {
        return id.equals("buildingbricks:grass_slab") || id.equals("buildingbricks:dirt_slab")
                || id.equals("buildingbricks:turf_slab");
    }

    private static LegacyBridge.RecoveryAbort blocked(String detail) {
        return new LegacyBridge.RecoveryAbort("World upgrade stopped before chunk loading: " + detail
                + " has no safe target. Restore the original modpack and use a backup. No unsupported content was substituted.",
                null);
    }
}
