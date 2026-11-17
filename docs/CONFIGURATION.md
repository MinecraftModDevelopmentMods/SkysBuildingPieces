# Configuration

Open **Mods → Sky's Building Pieces → Config** to edit settings in the game.
NeoForge also saves them in `config/skysbuildingpieces-common.toml`. On a
dedicated server, stop the server before editing the file and restart when
you are done. An existing CFG setting is copied on the first run and the
original file is kept.

`compat.forceReplaceBuildingBricksPieces=false`

The default leaves installed BuildingBricks pieces and items unchanged.
Set this to `true` only on a world backup when you want supported vanilla
material pieces replaced as they load. With the BOP add-on installed, this same
setting also replaces its supported materials. Replacement cannot automatically be undone.
Turning the option off stops further replacements, not previous conversions.

This mod has no world generation setting. It does not change Sky's Grass Slabs
configuration, generation or migration progress.

## Slab placement

**Gameplay → Directional slab placement** is on by default
(`gameplay.directionalSlabPlacement=true`).

Click near a face edge to place a slab vertically. Turn the setting off to
place supported regular slabs using Minecraft's horizontal slab
rules. Dedicated vertical pieces still place vertically. The setting changes
hand placement only. Existing blocks and Terrain Smoother generation keep
their shapes.

Close and reopen the world after changing slab placement. In multiplayer,
match the server's setting on each client.
