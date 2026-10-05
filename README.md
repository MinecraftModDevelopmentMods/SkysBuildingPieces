# Sky's Building Pieces

Building pieces for Minecraft 1.10.2, using vanilla materials and reusable
cutting templates. The mod does not generate terrain.

Version 0.2.0.110021 is a beta for testing. Back up existing worlds before
trying it. Do not remove BuildingBricks from a world that still needs its
other materials, shapes or tools.

Horizontal and vertical soil or grass slabs, turf and path slabs are not
included. Sky's Grass Slabs remains independent and unchanged.

Requires Forge 12.18.3.2511 and Java 8. Development uses ForgeGradle 7.0.34
and Gradle 9.6.1 under Java 17.

The mod adds 234 building block IDs and one hidden recovery holder: 235 in
total. Fixed metadata palettes keep material and orientation assignments
consistent between installations. Existing vanilla pieces are reused.

The optional Biomes O Plenty add-on provides pieces for its woods, stones and
gem blocks. It uses the same templates and replacement setting, with separate
migration progress. Install the matching add-on and Biomes O Plenty on both
the client and server. The core does not require either one.

See [pieces and templates](docs/GAMEPLAY.md), [configuration](docs/CONFIGURATION.md),
[existing worlds](docs/WORLD-UPGRADES.md) and [trying the beta](docs/TRYING-THE-BETA.md).

Licensed under LGPL-2.1-only. Adapted BuildingBricks definitions and geometry
retain the MIT notice in BUILDINGBRICKS-MIT.txt.
