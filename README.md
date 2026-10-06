# Sky's Building Pieces

Building pieces for Minecraft 1.10.2, made from vanilla materials with ordinary
crafting recipes. No templates or tools are needed. The mod does not generate terrain.

Version 0.3.0.110021 is a beta for testing. Back up existing worlds before
trying it. Do not remove BuildingBricks from a world that still needs its
other materials, shapes or tools.

The new crafting recipes are intended for packs without BuildingBricks.
Keeping both mods installed can introduce competing recipes.

Ordinary dirt vertical slabs are included. Horizontal soil slabs, other vertical
soil or grass slabs, turf and path slabs are not included. Sky's Grass Slabs
remains independent.

Requires Forge 12.18.3.2511 and Java 8. Development uses ForgeGradle 7.0.34
and Gradle 9.6.1 under Java 17.

The mod adds 235 building block IDs and one hidden recovery holder: 236 in
total. Fixed metadata palettes keep material and orientation assignments
consistent between installations. Existing vanilla pieces are reused.

The optional Biomes O Plenty add-on provides pieces for its woods, stones and
gem blocks. It uses the same crafting patterns and replacement setting, with separate
migration progress. Install the matching add-on and Biomes O Plenty on both
the client and server. The core does not require either one.

Core and BOP together use 302 block IDs. Existing palette slots remain unchanged.

See [pieces and recipes](docs/GAMEPLAY.md), [configuration](docs/CONFIGURATION.md),
[existing worlds](docs/WORLD-UPGRADES.md) and [trying the beta](docs/TRYING-THE-BETA.md).

Licensed under LGPL-2.1-only. Adapted BuildingBricks definitions and geometry
retain the MIT notice in BUILDINGBRICKS-MIT.txt.
