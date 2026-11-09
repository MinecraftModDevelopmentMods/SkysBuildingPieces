package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;

/** Missing mappings are posted on Forge's gameplay bus. */
public final class MissingMappingEvents {
    public static void missing(MissingMappingsEvent event) {
        if (event.getKey().equals(ForgeRegistries.Keys.BLOCKS)) {
            RegistryEvents.missingBlocks(event);
        }
        if (event.getKey().equals(ForgeRegistries.Keys.ITEMS)) {
            RegistryEvents.missingItems(event);
        }
        if (event.getKey().equals(ForgeRegistries.Keys.SOUND_EVENTS)) {
            RegistryEvents.missingSounds(event);
        }
    }

    private MissingMappingEvents() {
    }
}
