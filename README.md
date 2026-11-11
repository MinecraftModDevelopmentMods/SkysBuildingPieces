# Sky's Building Pieces

Sky's Building Pieces gives familiar Minecraft materials more shapes to build
with: slabs, steps, corners, stairs, walls and ice panes. Add narrow trims,
fill awkward corners or build a glass wall without using full blocks everywhere.

The mod uses vanilla materials and reuses existing Minecraft pieces where they
already fit. When Mineralogy is installed, its natural rocks also get matching
steps and corners automatically. No Mineralogy add-on is needed. Building
Pieces doesn't generate terrain.

## Building with the pieces

- Use the same slab item for horizontal and vertical placement. Click the centre
  of a face to place against it, or near an edge to place along that edge.
  Breaking a vertical slab returns the regular slab. To use ordinary slab
  placement, open **Mods → Sky's Building Pieces → Config → Gameplay** and
  turn off **Directional slab placement**.
- Sneak while placing a step to turn it vertically. Steps are quarter-block
  pieces; corners are eighth-block pieces.
- Matching pieces can join into supported larger shapes or a full block.
  Different materials don't combine.
- Find the mod's pieces in the **Sky's Building Pieces** creative tab. Vanilla
  pieces stay in their usual tabs.

Walls have lower panels with posts at ends, corners and junctions. A torch or
block above keeps a supporting post. Matching glass walls form a continuous
run, with borders at posts, exposed ends and colour changes.

Dirt and grass steps and corners are included too. Grass follows biome colours,
spreads and decays much like vanilla grass. Plants need a complete upper face.
Use a shovel on an uncovered dirt or grass step or corner to turn it into a
matching path piece. Its footprint stays the same, with a one-pixel lower top.
Flattening a grass piece also turns an ordinary grass block directly beneath
it into a path, using the same shovel action.

Pieces support native waterlogging. Wet grass and path pieces turn into their
matching dirt shapes, keeping the water and orientation. Drain them before
using a shovel.

Snow pieces drop themselves when dug by hand or with a shovel. Wooden slabs,
steps and corners burn for the corresponding share of their original block's
furnace fuel, so cutting wood does not create extra fuel.

## Crafting

Recipes appear in the recipe book as you collect their ingredients. Crafting a
piece also reveals its next cuts and recombination recipes while the table is
still open.

Craft pieces from matching full blocks, slabs and steps. For example, three
full blocks in a row make six slabs, and two matching slabs placed diagonally
make four steps. Existing vanilla recipes stay available.
You can also rebuild larger pieces: stack two matching corners in the crafting
grid for one step, or two matching steps for one slab.

The [recipe and placement guide](docs/GAMEPLAY.md) has the full patterns,
quantities and exceptions, including the separate dirt vertical slab recipe
when Grass Slabs isn't installed.

## Installation and other Sky mods

For **Minecraft 1.21.1**, use **NeoForge 21.1.247** and **Java 21**.
No other mod is required. Put the main jar in `mods` on both the client and
server.

You can also add:

- [Sky's Grass Slabs](https://github.com/MinecraftModDevelopmentMods/SkysGrassSlabs)
  version 1.2.0.121012 or later for horizontal dirt and grass slabs, paths and turf.
- [The Biomes O' Plenty add-on](https://github.com/MinecraftModDevelopmentMods/SkysBuildingPieces-BiomesOPlenty-Addon)
  for pieces made from BOP woods, stones and gem blocks.
- [Sky's Terrain Smoother](https://github.com/MinecraftModDevelopmentMods/SkysTerrainSmoother)
  to use suitable pieces on newly generated slopes and cave floors.

Building Pieces doesn't add wooden walls, horizontal soil slabs, turf or path
slabs.

## Existing worlds

Back up your world before changing mods. Recovery covers supported older
building pieces, not every block or item another mod might have added.

Read [existing worlds](docs/WORLD-UPGRADES.md) for the upgrade steps and limits,
or [configuration and modpack notes](docs/CONFIGURATION.md) for settings and
block-ID usage. Release history is in the [changelog](CHANGELOG.md).

## Licence

[LGPL-2.1-only](LICENSE), with third-party MIT portions credited in [NOTICE](NOTICE).
