# Sky's Building Pieces

Piece names use the material itself: Gold Wall and Oak Step, rather than the
full block's name followed by the shape. All 18 languages have matching material labels.

Building pieces for Minecraft 1.12.2, made from vanilla materials with ordinary
crafting recipes. No templates or tools are needed. The mod does not generate terrain.

Version 0.3.6.112021 is a beta for testing. Back up existing worlds before
trying it. Do not remove BuildingBricks from a world that still needs its
other materials, shapes or tools.

The new crafting recipes are intended for packs without BuildingBricks.
Keeping both mods installed can introduce competing recipes.

Ordinary dirt vertical slabs are included. Horizontal soil slabs, other vertical
soil or grass slabs, turf and path slabs are not included. Sky's Grass Slabs
remains independent.

Requires Forge 14.23.5.2859 and Java 8. Development uses ForgeGradle 7.0.34
and Gradle 9.6.1 under Java 17.

The mod adds 235 building block IDs and one hidden recovery holder: 236 in
total. Fixed metadata palettes keep material and orientation assignments
consistent between installations. Existing vanilla pieces are reused.
Supported regular slab items place horizontally or vertically, including oak.
Vertical slabs drop regular slabs and do not have separate creative entries.
Only ordinary dirt needs a separate recipe when no horizontal slab is available.

The Sky's Building Pieces creative tab groups the pieces together, including
reused vanilla slabs and stairs. Matching clear or stained glass walls join
without a border between each panel. Posts remain at ends, corners and junctions,
and beneath a torch or other block above.

The optional Biomes O Plenty add-on provides pieces for its woods, stones and
gem blocks. It uses the same crafting patterns and replacement setting, with separate
migration progress. Install the matching add-on and Biomes O Plenty on both
the client and server. The core does not require either one.

Core and the 1.12 BOP add-on together use 310 block IDs, including retired BOP
recovery. Existing palette slots remain unchanged.

See [pieces and recipes](docs/GAMEPLAY.md), [configuration](docs/CONFIGURATION.md),
[existing worlds](docs/WORLD-UPGRADES.md) and [trying the beta](docs/TRYING-THE-BETA.md).

[Release preparation](docs/RELEASING.md) covers the CI checks and publication safeguards.

Licensed under LGPL-2.1-only. Adapted BuildingBricks definitions and geometry
retain the MIT notice in BUILDINGBRICKS-MIT.txt.

All custom walls have lower connecting panels and raised posts at ends, corners
and junctions. A block or torch above keeps its supporting post. Selection follows
the visible wall; the wall still prevents jumping over it.
If using the BOP add-on, update its jar too so its wall models match this version.
