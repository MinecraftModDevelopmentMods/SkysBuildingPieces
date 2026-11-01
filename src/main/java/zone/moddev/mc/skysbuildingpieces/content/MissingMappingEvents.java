package zone.moddev.mc.skysbuildingpieces.content;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;

/** Missing mappings are posted on Forge's gameplay bus in this version. */
@Mod.EventBusSubscriber(modid = SkysBuildingPieces.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MissingMappingEvents {
    private MissingMappingEvents() {
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.MissingMappings<Block> event) {
        RegistryEvents.missingBlocks(event);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.MissingMappings<Item> event) {
        RegistryEvents.missingItems(event);
    }

    @SubscribeEvent
    public static void sounds(RegistryEvent.MissingMappings<SoundEvent> event) {
        RegistryEvents.missingSounds(event);
    }
}
