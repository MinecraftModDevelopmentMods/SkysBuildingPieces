package zone.moddev.mc.skysbuildingpieces.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Keeps the source biome table available after Forge's first level save. */
final class SourceLevelData {
    private static Path sourceWorld;
    private static CompoundTag snapshot = new CompoundTag();

    private SourceLevelData() {
    }

    static synchronized void capture(Path directory) {
        Path world = directory.toAbsolutePath().normalize();

        if (world.equals(sourceWorld) && !snapshot.isEmpty()) {
            return;
        }

        clear();
        Path level = world.resolve("level.dat");
        CompoundTag captured = new CompoundTag();

        if (Files.isRegularFile(level)) {
            try (InputStream input = Files.newInputStream(level)) {
                CompoundTag root = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                captured.putInt("DataVersion", root.getCompound("Data").getInt("DataVersion"));
                CompoundTag registries = root.getCompound("fml").getCompound("Registries");
                CompoundTag biomes = registries.getCompound("minecraft:worldgen/biome");
                if (biomes.isEmpty()) {
                    biomes = registries.getCompound("minecraft:biome");
                }
                captured.put("Registry", biomes.copy());
            } catch (IOException failure) {
                throw new LegacyBridge.RecoveryAbort(
                        "Cannot preserve the source biome registry. Restore the original world backup.", failure);
            }
        }

        sourceWorld = world;
        snapshot = captured;
    }

    static synchronized CompoundTag forWorld(Path directory) {
        return directory.toAbsolutePath().normalize().equals(sourceWorld) ? snapshot.copy() : new CompoundTag();
    }

    static synchronized void clear() {
        sourceWorld = null;
        snapshot = new CompoundTag();
    }
}
