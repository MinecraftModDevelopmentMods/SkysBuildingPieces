package zone.moddev.mc.skysbuildingpieces.api;

import zone.moddev.mc.skysbuildingpieces.SkysBuildingPieces;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Version 1 catalogue API. Register an add-on during mod construction. */
public final class BuildingPiecesApi {
    public static final int VERSION = 1;

    private BuildingPiecesApi() {
    }

    /** Stable logical shapes; callers never need palette IDs or numeric metadata. */
    public enum TerrainShape {
        SLAB, STEP, CORNER
    }

    /** Finds an existing bottom piece. Steps point N/E/S/W; corners use N=NW, E=NE, S=SE, W=SW. */
    public static BlockState bottomPiece(String material, TerrainShape shape, Direction direction) {
        if (Pieces.BLOCKS.isEmpty()) {
            throw new IllegalStateException("Piece lookup requires block registration");
        }
        if (shape == null || direction == null || direction.getAxis() == Direction.Axis.Y) {
            throw new IllegalArgumentException("A terrain piece needs a horizontal direction");
        }

        int rotation = direction == Direction.NORTH ? 0
                : direction == Direction.EAST ? 1 : direction == Direction.SOUTH ? 2 : 3;

        return Pieces.state(material,
                shape == TerrainShape.SLAB ? Shape.SLAB
                        : shape == TerrainShape.STEP ? Shape.HORIZONTAL_STEP : Shape.CORNER,
                shape == TerrainShape.SLAB ? 1 : rotation + 4);
    }

    /** Register only this contributor's blocks and items from its native registry events. */
    public static void registerBlocks(String namespace,
            net.minecraftforge.registries.IForgeRegistry<net.minecraft.block.Block> registry) {
        Pieces.registerNamespaceBlocks(namespace, registry);
    }

    public static void registerItems(String namespace,
            net.minecraftforge.registries.IForgeRegistry<net.minecraft.item.Item> registry) {
        Pieces.registerNamespaceItems(namespace, registry);
    }

    /** Reads materials.json and palettes.json from the contributor's assets catalogue. */
    public static Catalogue.Module registerCatalogue(String module, String namespace, int maximumBlocks,
            Class<?> anchor) {
        String root = "/assets/" + namespace + "/catalogue/";

        return registerCatalogue(module, namespace, maximumBlocks, Catalogue.read(anchor, root + "materials.json"),
                Catalogue.read(anchor, root + "palettes.json"));
    }

    /** Register resolved definitions, for example when a native mod disables its slab forms. */
    public static Catalogue.Module registerCatalogue(String module, String namespace, int maximumBlocks,
            com.google.gson.JsonArray materials, com.google.gson.JsonArray palettes) {
        if (!Pieces.initialized) {
            throw new IllegalStateException("The core must initialize before its add-ons");
        }

        Catalogue.Module contribution = Catalogue.INSTANCE.registerModule(module, namespace, materials, palettes,
                maximumBlocks);

        return contribution;
    }
}
