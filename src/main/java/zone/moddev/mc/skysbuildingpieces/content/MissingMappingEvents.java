package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;

/** Missing mappings are posted on Forge's gameplay bus. */
@Mod.EventBusSubscriber(modid = SkysBuildingPieces.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MissingMappingEvents {
    @SubscribeEvent
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
