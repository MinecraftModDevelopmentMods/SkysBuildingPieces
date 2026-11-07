package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

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
                    // Native region storage creates an empty file before its first chunk write.
                    // A nonempty short header still signals damage and must stop recovery.
                    if (region.length() == 0) {
                        continue;
                    }
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

                            CompoundTag root;

                            try (DataInputStream input = new DataInputStream(bytes)) {
                                root = NbtIo.read(input);
                            }

                            CompoundTag level = root.getCompound("Level");

                            HistoricalBiomes.validate(root);

                            {
                                Map<Integer, CompoundTag> tiles = new HashMap<Integer, CompoundTag>();
                                ListTag tileList = level.getList("TileEntities", 10);

                                for (int tileIndex = 0; tileIndex < tileList.size(); tileIndex++) {
                                    CompoundTag compoundTag = tileList.getCompound(tileIndex);

                                    tiles.put((compoundTag.getInt("x") & 15) | ((compoundTag.getInt("z") & 15) << 4)
                                            | (compoundTag.getInt("y") << 8), compoundTag);
                                }

                                ListTag sections = level.getList("Sections", 10);

                                for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                                    CompoundTag section = sections.getCompound(sectionIndex);

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
                                        CompoundTag tile = tiles.get((blockIndex & 255)
                                                | (((section.getByte("Y") & 255) * 16 + (blockIndex >> 8)) << 8));

                                        zone.moddev.mc.skysbuildingpieces.content.PieceBlock palette = LegacyBridge
                                                .palette(name);

                                        if (palette != null) {
                                            palette.palette.material(LegacyBridge.nibble(meta, blockIndex));
                                            continue;
                                        }
                                        if ((!LegacyMapping.owned(name) && name.startsWith("buildingbricks:")
                                                && !grassOwned(name)
                                                && !net.neoforged.fml.ModList.get().isLoaded("buildingbricks"))
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
                stacks(NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap()));
            }
        }

        File players = new File(world, "playerdata");
        File[] files = players.listFiles((directory, name) -> name.endsWith(".dat"));

        if (files != null) {
            for (File file : files) {
                try (InputStream input = new FileInputStream(file)) {
                    stacks(NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap()));
                }
            }
        }
    }

    private static void stacks(Tag value) {
        inspectStacks(value instanceof CompoundTag ? HistoricalItems.prepareCopy((CompoundTag) value) : value);
    }

    private static void inspectStacks(Tag value) {
        if (value instanceof CompoundTag) {
            CompoundTag compoundTag = (CompoundTag) value;
            String registryId = compoundTag.getString("id");

            if (HistoricalItems.isStack(compoundTag)) {
                HistoricalFullBlocks.resolve(registryId, compoundTag.getShort("Damage"));

                zone.moddev.mc.skysbuildingpieces.content.PieceBlock palette = LegacyBridge.palette(registryId);

                if (palette != null) {
                    palette.palette.material(compoundTag.getShort("Damage"));
                }
                if (skyOwned(registryId) && palette == null && !HistoricalFullBlocks.historical(registryId)
                        && !net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .containsKey(net.minecraft.resources.ResourceLocation.parse(registryId))) {
                    throw blocked(registryId + " item; restore its Sky add-on and dependencies");
                }
                if (!net.neoforged.fml.ModList.get().isLoaded("buildingbricks")
                        && registryId.startsWith("buildingbricks:") && !LegacyMapping.owned(registryId)
                        && !grassOwned(registryId)) {
                    throw blocked(registryId + " item");
                }
            }
            if (HistoricalItems.isStack(compoundTag) && LegacyBridge.active() && LegacyMapping.owned(registryId)) {
                String material = LegacyMapping.material(HistoricalItems.customData(compoundTag).getString("material"));
                Shape shape = LegacyMapping.shape(registryId, 0);

                if (!LegacyMapping.allowedMaterial(material, shape)
                        || Pieces.state(material, shape, shape == Shape.SLAB ? 1 : 0) == null) {
                    throw blocked(registryId + " / " + material + " / " + shape + " item");
                }
            }
            for (String key : compoundTag.getAllKeys()) {
                if (!key.equals("retained_tiles")) {
                    inspectStacks(compoundTag.get(key));
                }
            }
        } else if (value instanceof ListTag) {
            ListTag list = (ListTag) value;

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
