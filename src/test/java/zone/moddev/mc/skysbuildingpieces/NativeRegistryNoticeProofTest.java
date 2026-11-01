package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independently checks the raw saved content behind the narrow log profiles. */
class NativeRegistryNoticeProofTest {
    @Test
    void permittedVanillaRegistryNamesAreUnusedInEverySavedRecord() throws Exception {
        JsonObject profiles = new JsonParser().parse(new String(
                Files.readAllBytes(Paths.get("tools/native-log-profiles.json")), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("profiles");
        assertEquals(2, profiles.size());

        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : profiles.entrySet()) {
            JsonObject profile = entry.getValue().getAsJsonObject();
            Set<String> names = new HashSet<>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> registry
                    : profile.getAsJsonObject("mappings").entrySet()) {
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> mapping
                        : registry.getValue().getAsJsonObject().entrySet()) {
                    names.add(mapping.getKey());
                }
            }
            assertEquals(9, names.size());
            int records = 0;

            try (ZipFile archive = new ZipFile(profile.get("fixture").getAsString())) {
                java.util.Enumeration<? extends ZipEntry> members = archive.entries();
                while (members.hasMoreElements()) {
                    ZipEntry member = members.nextElement();
                    String label = member.getName();
                    try (InputStream input = archive.getInputStream(member)) {
                        if (label.endsWith(".dat") || label.endsWith(".dat_old")) {
                            CompoundNBT root = CompressedStreamTools.readCompressed(input);
                            // Registry availability is not a saved block, entity or inventory use.
                            root.remove("FML");
                            root.remove("fml");
                            inspect(root, names, label);
                            records++;
                        } else if (label.endsWith(".mca")) {
                            records += region(readAll(input), names, label);
                        }
                    }
                }
            }
            assertEquals(profile.get("inspected_nbt_records").getAsInt(), records);
            assertEquals(0, profile.get("saved_uses").getAsInt());
        }
    }

    private static int region(byte[] bytes, Set<String> names, String label) throws Exception {
        if (bytes.length == 0) {
            return 0;
        }
        assertTrue(bytes.length >= 8192, label);
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int records = 0;
        for (int slot = 0; slot < 1024; slot++) {
            int location = header.getInt(slot * 4);
            if (location == 0) {
                continue;
            }
            int offset = (location >>> 8) * 4096;
            int length = header.getInt(offset);
            int compression = bytes[offset + 4] & 255;
            InputStream payload = new ByteArrayInputStream(bytes, offset + 5, length - 1);
            if (compression == 1) {
                payload = new GZIPInputStream(payload);
            } else if (compression == 2) {
                payload = new InflaterInputStream(payload);
            } else {
                assertEquals(3, compression, "Unexpected fixture compression: " + label);
            }
            try (DataInputStream input = new DataInputStream(payload)) {
                inspect(CompressedStreamTools.read(input), names, label + ":" + slot);
            }
            records++;
        }
        return records;
    }

    private static void inspect(INBT value, Set<String> names, String label) {
        if (value instanceof CompoundNBT) {
            CompoundNBT compound = (CompoundNBT) value;
            for (String key : compound.getAllKeys()) {
                assertFalse(names.contains(key), "Saved registry-name key in " + label + ": " + key);
                inspect(compound.get(key), names, label + "/" + key);
            }
        } else if (value instanceof ListNBT) {
            for (INBT entry : (ListNBT) value) {
                inspect(entry, names, label);
            }
        } else if (value instanceof StringNBT) {
            assertFalse(names.contains(value.getAsString()), "Saved registry-name value in " + label);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
