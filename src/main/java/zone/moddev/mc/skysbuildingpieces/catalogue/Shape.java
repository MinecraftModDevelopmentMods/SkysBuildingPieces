package zone.moddev.mc.skysbuildingpieces.catalogue;

/** Saved shape conventions. Rotation matches BuildingBricks 2.0.13. */
public enum Shape {
    SLAB(2), VERTICAL_SLAB(4), HORIZONTAL_STEP(8), VERTICAL_STEP(4), STEP(12),
    CORNER(8), STAIRS(8), WALL(1), PANE(1);
    public final int states;
    Shape(int states) { this.states = states; }
    public static Shape named(String value) { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
}
