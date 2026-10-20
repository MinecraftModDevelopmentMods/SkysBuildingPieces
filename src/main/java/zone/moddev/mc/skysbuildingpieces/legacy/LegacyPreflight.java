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
            if (blockId >= 0 && blockId < 4096) {
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

                            NBTTagCompound root;

                            try (DataInputStream input = new DataInputStream(bytes)) {
                                root = CompressedStreamTools.read(input);
                            }

                            NBTTagCompound level = root.getCompoundTag("Level");

                            {
                                Map<Integer, NBTTagCompound> tiles = new HashMap<Integer, NBTTagCompound>();
                                NBTTagList tileList = level.getTagList("TileEntities", 10);

                                for (int tileIndex = 0; tileIndex < tileList.tagCount(); tileIndex++) {
                                    NBTTagCompound compoundTag = tileList.getCompoundTagAt(tileIndex);

                                    tiles.put((compoundTag.getInteger("x") & 15)
                                            | ((compoundTag.getInteger("z") & 15) << 4)
                                            | (compoundTag.getInteger("y") << 8), compoundTag);
                                }

                                NBTTagList sections = level.getTagList("Sections", 10);

                                for (int sectionIndex = 0; sectionIndex < sections.tagCount(); sectionIndex++) {
                                    NBTTagCompound section = sections.getCompoundTagAt(sectionIndex);
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
                                        NBTTagCompound tile = tiles.get((blockIndex & 255)
                                                | (((section.getByte("Y") & 255) * 16 + (blockIndex >> 8)) << 8));

                                        if (HistoricalFullBlocks.historical(name)) {
                                            HistoricalFullBlocks.resolve(name, LegacyBridge.nibble(meta, blockIndex));
                                            continue;
                                        }
                                        if (!LegacyBridge.active()) {
                                            continue;
                                        }

                                        String material = LegacyMapping
                                                .material(tile == null ? "" : tile.getString("material"));
                                        Shape shape = LegacyMapping.shape(name, LegacyBridge.nibble(meta, blockIndex));

                                        if (!LegacyMapping.allowedMaterial(material, shape)
                                                || Pieces.state(material, shape, LegacyMapping.orientation(shape,
                                                        LegacyBridge.nibble(meta, blockIndex))) == null) {
                                            throw blocked(name + " / " + material + " / " + shape + " in "
                                                    + file.getName() + " chunk " + level.getInteger("xPos") + ", "
                                                    + level.getInteger("zPos"));
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

    private static void stacks(NBTBase value) {
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compoundTag = (NBTTagCompound) value;
            String registryId = compoundTag.getString("id");

            if (compoundTag.hasKey("Count", 99)) {
                HistoricalFullBlocks.resolve(registryId, compoundTag.getShort("Damage"));
            }
            if (compoundTag.hasKey("Count", 99) && LegacyBridge.active() && LegacyMapping.owned(registryId)) {
                String material = LegacyMapping.material(compoundTag.getCompoundTag("tag").getString("material"));
                Shape shape = LegacyMapping.shape(registryId, 0);

                if (!LegacyMapping.allowedMaterial(material, shape)
                        || Pieces.state(material, shape, shape == Shape.SLAB ? 1 : 0) == null) {
                    throw blocked(registryId + " / " + material + " / " + shape + " item");
                }
            }
            for (String key : compoundTag.getKeySet()) {
                if (!key.equals("retained_tiles")) {
                    stacks(compoundTag.getTag(key));
                }
            }
        } else if (value instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) value;

            for (int entryIndex = 0; entryIndex < list.tagCount(); entryIndex++) {
                stacks(list.get(entryIndex));
            }
        }
    }

    private static LegacyBridge.RecoveryAbort blocked(String detail) {
        return new LegacyBridge.RecoveryAbort("World upgrade stopped before chunk loading: " + detail
                + " has no safe target. Restore BuildingBricks and its material add-ons, then use a backup. No unsupported material was substituted.",
                null);
    }
}
