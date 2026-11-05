package zone.moddev.mc.skysbuildingpieces;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

class LoaderHooksTest {
    @Test
    void theNativeChunkAndInventoryTargetsMatchThisForgeBuild() throws Exception {
        assertNativeReturn("net.minecraft.world.level.chunk.storage.SimpleRegionStorage", "upgradeChunkTag",
                "(Lnet/minecraft/nbt/CompoundTag;ILnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;",
                Opcodes.ARETURN);
        assertNativeReturn("net.minecraft.util.datafix.DataFixTypes", "update",
                "(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;",
                Opcodes.ARETURN);
        assertNativeReturn("net.minecraft.world.level.chunk.storage.SerializableChunkData", "copyOf",
                "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)"
                        + "Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;",
                Opcodes.ARETURN);
        assertNativeReturn("net.minecraft.util.filefix.FileFixerUpper", "fix",
                "(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
                        + "Lcom/mojang/serialization/Dynamic;Lnet/minecraft/util/worldupdate/UpgradeProgress;)"
                        + "Lcom/mojang/serialization/Dynamic;",
                Opcodes.ARETURN);
        assertNativeReturn("net.minecraftforge.common.ForgeHooks", "readAdditionalLevelSaveData",
                "(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
                        + "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelDirectory;)V",
                Opcodes.RETURN);
    }

    @Test
    void missingMixinsAreAnErrorInsteadOfSilentlySkippingRecovery() throws Exception {
        Path resources = Path.of("src/main/resources");
        var config = JsonParser.parseString(Files.readString(resources.resolve("skysbuildingpieces.mixins.json")))
                .getAsJsonObject();
        assertTrue(config.get("required").getAsBoolean());
        assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        assertFalse(Files.exists(resources.resolve("META-INF/coremods.json")));
    }

    static void assertNativeReturn(String type, String method, String descriptor, int opcode) throws Exception {
        String resource = "/" + type.replace('.', '/') + ".class";
        try (var stream = LoaderHooksTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, type);
            ClassNode nativeClass = new ClassNode();
            new ClassReader(stream).accept(nativeClass, 0);
            var methods = nativeClass.methods.stream()
                    .filter(candidate -> candidate.name.equals(method) && candidate.desc.equals(descriptor))
                    .toList();
            assertEquals(1, methods.size(), type + "." + method);
            boolean returns = false;
            for (var instruction : methods.getFirst().instructions) {
                returns |= instruction.getOpcode() == opcode;
            }
            assertTrue(returns, type + "." + method);
        }
    }
}
