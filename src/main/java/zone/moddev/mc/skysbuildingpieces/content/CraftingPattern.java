package zone.moddev.mc.skysbuildingpieces.content;

/** Stable patterns and quantities, independent of Minecraft initialization. */
public enum CraftingPattern {
    SLAB(6, "XXX"),
    VERTICAL_SLAB(6, "X", "X", "X"),
    STEP(4, "X ", " X"),
    SOIL_STEP(8, "X ", " X"),
    CORNER(4, "XX"),
    STAIRS(4, "X  ", "XX ", "XXX"),
    WALL(6, "XX", "XX", "XX"),
    PANE(16, "XXX", "XXX"),
    ROTATE(1, "X");

    private final int output;
    private final String[] rows;
    CraftingPattern(int output, String... rows) { this.output = output; this.rows = rows; }
    public int width() { return rows[0].length(); }
    public int height() { return rows.length; }
    public boolean occupied(int x, int y) { return rows[y].charAt(x) == 'X'; }
    public int ingredients() {
        int count = 0;
        for (int y = 0; y < height(); y++) for (int x = 0; x < width(); x++) if (occupied(x, y)) count++;
        return count;
    }
    public int yield(boolean halfBlockInput) { return halfBlockInput ? output / 2 : output; }

    public static boolean wildcardNativeCuts(String material) {
        return material.equals("minecraft:chiseled_sandstone") || material.equals("minecraft:smooth_sandstone") ||
                material.equals("minecraft:chiseled_red_sandstone") || material.equals("minecraft:smooth_red_sandstone") ||
                material.equals("minecraft:stonebrick_carved") || material.equals("minecraft:stonebrick_cracked") ||
                material.equals("minecraft:stonebrick_mossy");
    }
    public static boolean rotateOnlySlab(String material) {
        return material.equals("minecraft:stone") || material.equals("minecraft:snow") || wildcardNativeCuts(material);
    }
}
