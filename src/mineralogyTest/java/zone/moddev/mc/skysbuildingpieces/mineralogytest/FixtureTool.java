package zone.moddev.mc.skysbuildingpieces.mineralogytest;

import com.google.gson.JsonElement;
import java.io.*;
import net.minecraft.nbt.*;
import net.minecraft.world.chunk.storage.RegionFile;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.compat.MineralogyCompat;

/** Adds inventory and delayed-chunk cases to an already copied legacy fixture. Never run on a source world. */
public final class FixtureTool {
    private FixtureTool() {
    }

    public static void main(String[] arguments) throws Exception {
        File world = new File(arguments[0]);
        if (!new File(world, "disposable-fixture.txt").isFile()) {
            throw new IllegalArgumentException("Make and mark a disposable world copy before preparing this fixture.");
        }
        NBTTagCompound level;
        try (InputStream input = new FileInputStream(new File(world, "level.dat"))) {
            level = CompressedStreamTools.readCompressed(input);
        }
        int chestId = -1;
        NBTTagList ids = level.getCompoundTag("FML").getCompoundTag("Registries")
                .getCompoundTag("minecraft:blocks").getTagList("ids", 10);
        for (int index = 0; index < ids.tagCount(); index++) {
            NBTTagCompound entry = ids.getCompoundTagAt(index);
            if (entry.getString("K").equals("minecraft:chest")) {
                chestId = entry.getInteger("V");
            }
        }
        if (chestId < 0) {
            throw new IllegalStateException("The fixture has no saved chest registry identity.");
        }

        File regionPath = new File(world, "region/r.0.0.mca");
        RegionFile region = new RegionFile(regionPath);
        NBTTagCompound chunk;
        try {
            try (DataInputStream input = region.getChunkDataInputStream(0, 0)) {
                chunk = CompressedStreamTools.read(input);
            }
            NBTTagCompound chunkLevel = chunk.getCompoundTag("Level");
            NBTTagList sections = chunkLevel.getTagList("Sections", 10);
            NBTTagCompound section = null;
            for (int index = 0; index < sections.tagCount(); index++) {
                if (sections.getCompoundTagAt(index).getByte("Y") == 6) {
                    section = sections.getCompoundTagAt(index);
                }
            }
            if (section == null) throw new IllegalStateException("The legacy orientation grid is missing.");

            NBTTagList tiles = chunkLevel.getTagList("TileEntities", 10);
            int state = 0;
            NBTTagList contents = new NBTTagList();
            for (JsonElement entry : Catalogue.read(FixtureTool.class,
                    "/assets/" + MineralogyCompat.ASSETS + "/catalogue/palettes.json")) {
                com.google.gson.JsonObject palette = entry.getAsJsonObject();
                int variants = palette.getAsJsonArray("materials").size() * palette.get("states").getAsInt();
                for (int metadata = 0; metadata < variants; metadata++) {
                    NBTTagCompound stack = new NBTTagCompound();
                    stack.setString("id", MineralogyCompat.ASSETS + ":" + palette.get("id").getAsString());
                    stack.setByte("Count", (byte) (1 + state % 64));
                    stack.setShort("Damage", (short) metadata);
                    stack.setByte("Slot", (byte) (state % 27));
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setInteger("fixture_index", state);
                    NBTTagCompound display = new NBTTagCompound();
                    display.setString("Name", "Fixture rock piece " + state);
                    tag.setTag("display", display);
                    NBTTagList enchantments = new NBTTagList();
                    NBTTagCompound enchantment = new NBTTagCompound();
                    enchantment.setShort("id", (short) 17);
                    enchantment.setShort("lvl", (short) 2);
                    enchantments.appendTag(enchantment);
                    tag.setTag("ench", enchantments);
                    stack.setTag("tag", tag);
                    contents.appendTag(stack);
                    state++;
                    if (contents.tagCount() == 27) {
                        int chest = (state - 1) / 27;
                        int x = chest & 15;
                        int z = chest >> 4;
                        int blockIndex = (108 & 15) << 8 | z << 4 | x;
                        section.getByteArray("Blocks")[blockIndex] = (byte) chestId;
                        byte[] stateMetadata = section.getByteArray("Data");
                        int metadataShift = (blockIndex & 1) * 4;
                        // Chest metadata zero has no valid block-state ID in these versions.
                        stateMetadata[blockIndex >> 1] = (byte) ((stateMetadata[blockIndex >> 1] & ~(15 << metadataShift))
                                | 2 << metadataShift);
                        if (section.hasKey("Add")) {
                            byte[] add = section.getByteArray("Add");
                            int shift = (blockIndex & 1) * 4;
                            add[blockIndex >> 1] = (byte) ((add[blockIndex >> 1] & ~(15 << shift))
                                    | (chestId >> 8) << shift);
                        }
                        NBTTagCompound tile = new NBTTagCompound();
                        tile.setString("id", arguments[1].equals("1.10.2") ? "Chest" : "minecraft:chest");
                        tile.setInteger("x", x);
                        tile.setInteger("y", 108);
                        tile.setInteger("z", z);
                        tile.setTag("Items", contents);
                        tiles.appendTag(tile);
                        contents = new NBTTagList();
                    }
                }
            }
            if (state != 540 || contents.tagCount() != 0) throw new IllegalStateException("Incomplete inventory fixture");
            chunkLevel.setTag("TileEntities", tiles);
            try (DataOutputStream output = region.getChunkDataOutputStream(0, 0)) {
                CompressedStreamTools.write(chunk, output);
            }
        } finally {
            region.close();
        }

        NBTTagCompound distant = chunk.copy();
        NBTTagCompound distantLevel = distant.getCompoundTag("Level");
        distantLevel.setInteger("xPos", 40);
        distantLevel.setInteger("zPos", 40);
        distantLevel.setTag("Entities", new NBTTagList());
        NBTTagList distantTiles = distantLevel.getTagList("TileEntities", 10);
        for (int index = 0; index < distantTiles.tagCount(); index++) {
            NBTTagCompound tile = distantTiles.getCompoundTagAt(index);
            tile.setInteger("x", tile.getInteger("x") + 640);
            tile.setInteger("z", tile.getInteger("z") + 640);
        }
        RegionFile distantRegion = new RegionFile(new File(world, "region/r.1.1.mca"));
        try {
            try (DataOutputStream output = distantRegion.getChunkDataOutputStream(8, 8)) {
                CompressedStreamTools.write(distant, output);
            }
        } finally {
            distantRegion.close();
        }
        System.out.println("MINERALOGY_LEGACY_FIXTURE_READY states=540 stacks=540 chest_metadata=2 delayed_chunk=40,40");
    }
}
