package zone.moddev.mc.skysbuildingpieces.legacy;

import zone.moddev.mc.skysbuildingpieces.catalogue.*;

/** Pure scope/geometry resolver. Never uses BuildingBricks' dynamic item damage. */
public final class LegacyMapping {
    private LegacyMapping() { }
    public static String material(String value) { return value.contains(":") ? value : "minecraft:"+value; }
    public static boolean owned(String registry) {
        if(!registry.startsWith("buildingbricks:")) return false;
        String path=registry.substring(15);
        if(path.equals("grass_slab") || path.equals("turf_slab") || path.equals("dirt_slab") || path.equals("grass_vertical_slab")) return false;
        return path.endsWith("_slab") || path.endsWith("_step") || path.endsWith("_corner") || path.endsWith("_stairs") || path.endsWith("_wall") || path.endsWith("_pane");
    }
    public static Shape shape(String registry,int meta) {
        if(registry.endsWith("_vertical_slab")) return Shape.VERTICAL_SLAB;
        if(registry.endsWith("_slab")) return Shape.SLAB;
        if(registry.endsWith("_step")) return (meta&8)!=0 ? Shape.VERTICAL_STEP : Shape.HORIZONTAL_STEP;
        if(registry.endsWith("_corner")) return Shape.CORNER;
        if(registry.endsWith("_stairs")) return Shape.STAIRS;
        if(registry.endsWith("_wall")) return Shape.WALL;
        if(registry.endsWith("_pane")) return Shape.PANE;
        throw new IllegalArgumentException("Unsupported legacy shape " + registry);
    }
    public static int orientation(Shape shape,int meta) {
        switch(shape) {
            case SLAB: return meta&1;
            case VERTICAL_SLAB: case VERTICAL_STEP: return meta&3;
            case HORIZONTAL_STEP: case CORNER: case STAIRS: return meta&7;
            case WALL: case PANE: return 0;
            default: throw new IllegalArgumentException("Unsupported legacy orientation");
        }
    }
    public static boolean enabled(boolean installed,boolean force) { return !installed || force; }
    public static boolean allowedMaterial(String material,Shape shape) {
        Catalogue.MaterialDef def=Catalogue.INSTANCE.materials.get(material(material));
        return def!=null && Catalogue.supportsShape(def,shape);
    }
}
