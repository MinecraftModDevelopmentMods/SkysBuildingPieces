package zone.moddev.mc.skysbuildingpieces.api;

import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Version-one extension for fixed material catalogues. Call during add-on pre-initialization. */
public final class BuildingPiecesApi {
    public static final int VERSION = 1;
    private BuildingPiecesApi() { }

    /** Reads materials.json and palettes.json from the contributor's assets catalogue. */
    public static Catalogue.Module registerCatalogue(String module, String namespace, int maximumBlocks, Class<?> anchor) {
        if (!Pieces.initialized) throw new IllegalStateException("The core must pre-initialize before its add-ons");
        String root = "/assets/" + namespace + "/catalogue/";
        Catalogue.Module contribution = Catalogue.INSTANCE.registerModule(module, namespace,
                Catalogue.read(anchor, root + "materials.json"), Catalogue.read(anchor, root + "palettes.json"), maximumBlocks);
        Pieces.registerModule(contribution);
        SkysBuildingPieces.proxy.registerModels(namespace);
        return contribution;
    }
}
