# Configuration

Forge creates `config/skysbuildingpieces-common.toml`. Stop the game or server before
editing it, then restart. An existing CFG setting is copied on the first run;
the original file is kept.

`compat.forceReplaceBuildingBricksPieces=false`

The default leaves installed BuildingBricks pieces and items unchanged.
Set this to `true` only on a world backup when you want supported vanilla
material pieces replaced as they load. With the BOP add-on installed, this same
setting also replaces its supported materials. Replacement cannot automatically be undone.
Turning the option off stops further replacements, not previous conversions.

This mod has no world generation setting. It does not change Sky's Grass Slabs
configuration, generation or migration progress.

## Slab placement

`gameplay.directionalSlabPlacement=true`

Slabs can be placed vertically by clicking near a face edge. Set this to
`false` to place supported regular slabs using Minecraft's horizontal slab
rules. Dedicated vertical pieces still place vertically. The setting changes
hand placement only. Existing blocks and Terrain Smoother generation keep
their shapes.

Stop the game or server before editing the config and restart to apply it.
For multiplayer, set the same value on the server and on clients.
