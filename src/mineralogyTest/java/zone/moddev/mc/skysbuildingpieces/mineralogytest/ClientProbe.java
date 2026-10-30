package zone.moddev.mc.skysbuildingpieces.mineralogytest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

public final class ClientProbe extends ProbeProxy {
    private boolean checked;

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            if (checked) {
                if (RuntimeProbe.complete) {
                    writeMarker(minecraft);
                    minecraft.shutdown();
                }
                return;
            }
            checked = true;
            int models = 0;
            for (Catalogue.Palette palette : Catalogue.INSTANCE.modules.get("mineralogy").palettes) {
                for (String material : palette.materials) {
                    for (int orientation = 0; orientation < palette.shape.states; orientation++) {
                        net.minecraft.block.state.IBlockState state = Pieces.state(material, palette.shape, orientation);
                        IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);
                        RuntimeProbe.require(model != minecraft.getRenderItem().getItemModelMesher().getModelManager().getMissingModel(),
                                "Missing piece model: " + material + "/" + palette.shape + "/" + orientation);
                        RuntimeProbe.require(!model.getParticleTexture().getIconName().contains("missingno"),
                                "Missing Mineralogy texture: " + material);
                        models++;
                    }
                    ItemStack stack = Pieces.stack(Pieces.state(material, palette.shape, 0), 1);
                    IBakedModel item = minecraft.getRenderItem().getItemModelMesher().getItemModel(stack);
                    RuntimeProbe.require(!item.getParticleTexture().getIconName().contains("missingno"),
                            "Missing piece item model: " + material);
                }
            }
            System.out.println("MINERALOGY_PIECES_MODELS_PASS states=" + models);
            String phase = System.getProperty("skysbuildingpieces.integrationPhase", "client");
            if (phase.equals("client")) {
                writeMarker(minecraft);
                minecraft.shutdown();
            } else {
                WorldSettings settings = new WorldSettings(92837L, GameType.CREATIVE, true, false, WorldType.DEFAULT);
                minecraft.launchIntegratedServer("mineralogy-addon-world", "Mineralogy Pieces Test", settings);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            net.minecraftforge.fml.common.FMLCommonHandler.instance().exitJava(2, true);
        }
    }

    private static void writeMarker(Minecraft minecraft) throws java.io.IOException {
        Files.write(minecraft.gameDir.toPath().resolve("building-pieces-client-pass.txt"),
                "Mineralogy piece resources passed\n".getBytes(StandardCharsets.UTF_8));
    }
}
