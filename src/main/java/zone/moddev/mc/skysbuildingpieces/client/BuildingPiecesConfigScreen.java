package zone.moddev.mc.skysbuildingpieces.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import zone.moddev.mc.skysbuildingpieces.config.BuildingPiecesConfig;

/** Common settings are saved for the next game start. */
final class BuildingPiecesConfigScreen extends Screen {
    private static final String KEYS = "skysbuildingpieces.configuration.";

    private final Screen parent;
    private boolean directionalSlabs;
    private boolean replaceLegacyPieces;

    BuildingPiecesConfigScreen(Screen parent) {
        super(Component.translatable(KEYS + "title", "Sky's Building Pieces"));
        this.parent = parent;
        directionalSlabs = BuildingPiecesConfig.configuredSlabPlacement();
        replaceLegacyPieces = BuildingPiecesConfig.forceReplacement();
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;

        addRenderableWidget(CycleButton.onOffBuilder(directionalSlabs)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEYS + "directionalSlabPlacement.tooltip")))
                .create(left, 65, 300, 20, Component.translatable(KEYS + "directionalSlabPlacement"),
                        (button, enabled) -> directionalSlabs = enabled));

        addRenderableWidget(CycleButton.onOffBuilder(replaceLegacyPieces)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEYS + "forceReplaceBuildingBricksPieces.tooltip")))
                .create(left, 95, 300, 20, Component.translatable(KEYS + "forceReplaceBuildingBricksPieces"),
                        (button, enabled) -> replaceLegacyPieces = enabled));

        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.done"), button -> {
            BuildingPiecesConfig.saveForNextStart(directionalSlabs, replaceLegacyPieces);
            onClose();
        }).bounds(width / 2 - 100, height - 35, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.centeredText(font, title, width / 2, 25, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
