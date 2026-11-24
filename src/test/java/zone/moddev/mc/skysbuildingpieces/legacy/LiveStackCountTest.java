package zone.moddev.mc.skysbuildingpieces.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

class LiveStackCountTest {
    @Test
    void savedBytesKeepTheirUnsignedMeaning() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setByte("Count", (byte) 255);

        assertEquals(255, LegacyBridge.stackCount(saved));
    }

    @Test
    void liveCountsKeepAllTheirBits() {
        for (int count : new int[] {1, 127, 128, 255, 256, 1895, Integer.MAX_VALUE}) {
            NBTTagCompound live = new NBTTagCompound();
            live.setInteger("Count", count);

            assertEquals(count, LegacyBridge.stackCount(live));
        }
    }
}
