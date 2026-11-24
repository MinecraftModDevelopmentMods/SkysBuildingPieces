package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class LiveStackCountTest {
    @Test
    void savedBytesKeepTheirUnsignedMeaning() {
        CompoundTag saved = new CompoundTag();
        saved.putByte("Count", (byte) 255);

        assertEquals(255, LegacyBridge.stackCount(saved));
    }

    @Test
    void liveCountsKeepAllTheirBits() {
        for (int count : new int[] {1, 127, 128, 255, 256, 1895, Integer.MAX_VALUE}) {
            CompoundTag live = new CompoundTag();
            live.putInt("Count", count);

            assertEquals(count, LegacyBridge.stackCount(live));
        }
    }
}
