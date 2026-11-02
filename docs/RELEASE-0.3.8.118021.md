# Building Pieces for Minecraft 1.18.2

This version brings the existing slabs, steps, corners, stairs, walls and panes
to Minecraft 1.18.2. It requires Forge 40.3.0 and Java 17.

Placement, joining, continuous glass walls, grass, snow, waterlogging and shovel
paths work as before. Mineralogy rocks receive steps and corners when Mineralogy
is installed. Grass Slabs remains optional; use its matching 1.18.2 version for
shared grass behaviour.

Existing palettes, orientations and custom item data are kept when upgrading.
Recovery also keeps its progress in Minecraft's new chunk format. Keep the whole
world and its registry sidecar together when making a backup, and test a copy
before continuing to play.

The upgrade bridge also retains add-on biome names through the conversion from
older numeric biome volumes to section palettes. Vanilla biome renames still
run normally. This changes saved identities without regenerating terrain.
