package zone.moddev.mc.skysbuildingpieces.content;

import java.util.List;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;

/** Named geometry for placed pieces. The order of each list matches the old shape geometry. */
enum PieceOrientation implements StringRepresentable {
    TOP, BOTTOM,
    NORTH, EAST, SOUTH, WEST,
    NORTH_WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST,
    TOP_NORTH, TOP_EAST, TOP_SOUTH, TOP_WEST,
    BOTTOM_NORTH, BOTTOM_EAST, BOTTOM_SOUTH, BOTTOM_WEST,
    TOP_NORTH_WEST, TOP_NORTH_EAST, TOP_SOUTH_EAST, TOP_SOUTH_WEST,
    BOTTOM_NORTH_WEST, BOTTOM_NORTH_EAST, BOTTOM_SOUTH_EAST, BOTTOM_SOUTH_WEST;

    private final String name = name().toLowerCase(Locale.ROOT);

    static List<PieceOrientation> forShape(Shape shape) {
        return switch (shape) {
            case SLAB -> List.of(TOP, BOTTOM);
            case VERTICAL_SLAB -> List.of(SOUTH, WEST, NORTH, EAST);
            case HORIZONTAL_STEP -> List.of(TOP_NORTH, TOP_EAST, TOP_SOUTH, TOP_WEST,
                    BOTTOM_NORTH, BOTTOM_EAST, BOTTOM_SOUTH, BOTTOM_WEST);
            case VERTICAL_STEP -> List.of(NORTH_WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST);
            case STEP -> List.of(TOP_NORTH, TOP_EAST, TOP_SOUTH, TOP_WEST,
                    BOTTOM_NORTH, BOTTOM_EAST, BOTTOM_SOUTH, BOTTOM_WEST,
                    NORTH_WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST);
            case CORNER -> List.of(TOP_NORTH_WEST, TOP_NORTH_EAST, TOP_SOUTH_EAST, TOP_SOUTH_WEST,
                    BOTTOM_NORTH_WEST, BOTTOM_NORTH_EAST, BOTTOM_SOUTH_EAST, BOTTOM_SOUTH_WEST);
            case STAIRS -> List.of(BOTTOM_EAST, BOTTOM_WEST, BOTTOM_SOUTH, BOTTOM_NORTH,
                    TOP_EAST, TOP_WEST, TOP_SOUTH, TOP_NORTH);
            case WALL, PANE -> List.of();
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
