package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;

/** Keeps the source biome table available after Forge's first level save. */
final class SourceLevelData {
    private static Path sourceWorld;
    private static CompoundNBT snapshot = new CompoundNBT();

    private SourceLevelData() {
    }

    static synchronized void capture(Path directory) {
        Path world = directory.toAbsolutePath().normalize();

        if (world.equals(sourceWorld) && !snapshot.isEmpty()) {
            return;
        }

        clear();
        Path level = world.resolve("level.dat");
        CompoundNBT captured = new CompoundNBT();

        if (Files.isRegularFile(level)) {
            try (InputStream input = Files.newInputStream(level)) {
                CompoundNBT root = CompressedStreamTools.readCompressed(input);
                captured.putInt("DataVersion", root.getCompound("Data").getInt("DataVersion"));
                captured.put("Registry", root.getCompound("fml").getCompound("Registries")
                        .getCompound("minecraft:biome").copy());
            } catch (IOException failure) {
                throw new LegacyBridge.RecoveryAbort(
                        "Cannot preserve the source biome registry. Restore the original world backup.", failure);
            }
        }

        sourceWorld = world;
        snapshot = captured;
    }

    static synchronized CompoundNBT forWorld(Path directory) {
        return directory.toAbsolutePath().normalize().equals(sourceWorld) ? snapshot.copy() : new CompoundNBT();
    }

    static synchronized void clear() {
        sourceWorld = null;
        snapshot = new CompoundNBT();
    }
}
