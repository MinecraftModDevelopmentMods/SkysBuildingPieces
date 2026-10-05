package zone.moddev.mc.skysbuildingpieces.catalogue;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Permanent palettes. Registration order never determines saved metadata. */
public final class Catalogue {
    public static final String ROOT = "/assets/skysbuildingpieces/catalogue/";
    public static final Catalogue INSTANCE = new Catalogue();
    private final Map<String, MaterialDef> definitions = new LinkedHashMap<String, MaterialDef>();
    private final List<Palette> registeredPalettes = new ArrayList<Palette>();
    private final Map<String, Module> registeredModules = new LinkedHashMap<String, Module>();
    public final Map<String, MaterialDef> materials = Collections.unmodifiableMap(definitions);
    public final List<Palette> palettes = Collections.unmodifiableList(registeredPalettes);
    public final Map<String, Module> modules = Collections.unmodifiableMap(registeredModules);
    private boolean frozen;

    public Catalogue() {
        Module core = registerModule("vanilla", "skysbuildingpieces", read(Catalogue.class, ROOT + "materials.json"),
                read(Catalogue.class, ROOT + "palettes.json"), 255);
        if (core.palettes.size() != 234 || core.palettes.size() + 1 > 256)
            throw new IllegalStateException("Core block budget exceeded");
    }

    /** Validate the complete contribution before publishing any of it. */
    public synchronized Module registerModule(String module, String namespace, JsonArray materialData,
            JsonArray paletteData, int maximumBlocks) {
        if (frozen) throw new IllegalStateException("Catalogue registration has finished");
        if (!module.matches("[a-z][a-z0-9_]*") || !namespace.matches("[a-z][a-z0-9_]*") ||
                registeredModules.containsKey(module) || maximumBlocks < 1)
            throw new IllegalArgumentException("Invalid or duplicate catalogue module: " + module);
        for (Module existing : modules.values()) if (existing.namespace.equals(namespace))
            throw new IllegalArgumentException("Duplicate catalogue namespace: " + namespace);
        Map<String, MaterialDef> additions = new LinkedHashMap<String, MaterialDef>();
        for (JsonElement element : materialData) {
            MaterialDef definition = new MaterialDef(module, element.getAsJsonObject());
            if (definitions.containsKey(definition.id) || additions.put(definition.id, definition) != null)
                throw new IllegalArgumentException("Duplicate material: " + definition.id);
        }
        if (additions.isEmpty()) throw new IllegalArgumentException("Empty material catalogue");
        List<Palette> addedPalettes = new ArrayList<Palette>();
        Set<String> ids = new HashSet<String>(), shapes = new HashSet<String>();
        for (JsonElement element : paletteData) {
            Palette palette = new Palette(module, namespace, element.getAsJsonObject());
            if (!ids.add(palette.id) || palette.materials.isEmpty() || palette.materials.size() * palette.shape.states > 16)
                throw new IllegalArgumentException("Invalid palette: " + palette.registryId());
            for (String id : palette.materials) {
                MaterialDef definition = additions.get(id);
                if (definition == null || !definition.group.equals(palette.group) ||
                        !shapes.add(id + "/" + palette.shape) ||
                        definition.soil() && (palette.shape == Shape.SLAB || palette.shape == Shape.VERTICAL_SLAB))
                    throw new IllegalArgumentException("Invalid palette material: " + id);
            }
            addedPalettes.add(palette);
        }
        if (addedPalettes.size() > maximumBlocks || addedPalettes.isEmpty())
            throw new IllegalArgumentException("Module block budget exceeded: " + module);
        Module contribution = new Module(module, namespace, additions, addedPalettes, maximumBlocks);
        definitions.putAll(additions);
        registeredPalettes.addAll(addedPalettes);
        registeredModules.put(module, contribution);
        return contribution;
    }

    public synchronized void freeze() { frozen = true; }
    public String moduleFor(String material) {
        MaterialDef definition = materials.get(material);
        return definition == null ? null : definition.module;
    }
    public static JsonArray read(Class<?> anchor, String resource) {
        InputStream stream = anchor.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing catalogue: " + resource);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonArray();
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    public static final class Module {
        public final String id, namespace;
        public final int maximumBlocks;
        public final Map<String, MaterialDef> materials;
        public final List<Palette> palettes;
        private Module(String id, String namespace, Map<String, MaterialDef> materials, List<Palette> palettes, int maximum) {
            this.id = id; this.namespace = namespace; maximumBlocks = maximum;
            this.materials = Collections.unmodifiableMap(new LinkedHashMap<String, MaterialDef>(materials));
            this.palettes = Collections.unmodifiableList(new ArrayList<Palette>(palettes));
        }
    }
    public static final class MaterialDef {
        public final String module, id, name, group, type;
        private final JsonObject json, blocks;
        MaterialDef(String module, JsonObject source) {
            this.module = module;
            json = new JsonParser().parse(source.toString()).getAsJsonObject();
            id = json.get("id").getAsString(); name = json.get("name").getAsString();
            group = json.get("group").getAsString(); type = json.get("type").getAsString();
            blocks = json.getAsJsonObject("blocks");
            if (!id.matches("[a-z0-9_]+:[a-z0-9_/]+") || !blocks.has("full") ||
                    !Float.isFinite(hardness()) || hardness() < 0 || !Float.isFinite(resistance()))
                throw new IllegalArgumentException("Invalid material: " + id);
        }
        public JsonElement block(String shape) {
            JsonElement value = blocks.get(shape);
            return value == null ? null : new JsonParser().parse(value.toString());
        }
        public float hardness() { return json.has("hardness") ? json.get("hardness").getAsFloat() : type.equals("grass") ? .6f : 1; }
        public float resistance() { return json.has("resistance") ? json.get("resistance").getAsFloat() : type.equals("rock") || type.equals("metal") ? 10 : type.equals("wood") ? 5 : -1; }
        public int flammability() { return json.has("flammability") ? json.get("flammability").getAsInt() : -1; }
        public int fireSpread() { return json.has("fireSpread") ? json.get("fireSpread").getAsInt() : -1; }
        public boolean nativeProperties() { return json.has("nativeProperties") && json.get("nativeProperties").getAsBoolean(); }
        public float explosionResistance() { return json.has("explosionResistance") ? json.get("explosionResistance").getAsFloat() : -1; }
        public boolean soil() { return type.equals("dirt") || type.equals("grass"); }
        public String normalDrop() { return json.has("normalHarvest") ? json.get("normalHarvest").getAsString() : id; }
    }
    public static final class Palette {
        public final String module, namespace, id, group;
        public final Shape shape;
        public final List<String> materials;
        Palette(String module, String namespace, JsonObject source) {
            this.module = module; this.namespace = namespace;
            id = source.get("id").getAsString(); group = source.get("group").getAsString();
            if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid palette ID: " + id);
            shape = Shape.named(source.get("shape").getAsString());
            List<String> slots = new ArrayList<String>();
            for (JsonElement material : source.getAsJsonArray("materials")) slots.add(material.getAsString());
            materials = Collections.unmodifiableList(slots);
        }
        public String registryId() { return namespace + ":" + id; }
        public String material(int meta) {
            int slot = (meta & 15) / shape.states;
            if (slot >= materials.size()) throw new IllegalArgumentException("Unassigned palette slot: " + registryId() + ":" + meta);
            return materials.get(slot);
        }
        public int meta(String material, int orientation) {
            int slot = materials.indexOf(material);
            if (slot < 0 || orientation < 0 || orientation >= shape.states) throw new IllegalArgumentException("Not in palette");
            return slot * shape.states + orientation;
        }
    }
}
