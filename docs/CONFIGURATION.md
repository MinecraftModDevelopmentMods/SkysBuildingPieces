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

## Saved palettes

Building Pieces keeps the same fixed material palettes when moving from
Minecraft 1.12.2. Without Mineralogy it registers 237 piece palettes and one
hidden recovery block. Mineralogy adds its own 35 palettes when installed.

Minecraft 1.14 no longer has the old 4,096-block registry limit. Materials still
share their permanent palette slots so saved pieces keep their identities.
Water, snow and neighbouring connections are stored as native block states.
Add-ons have separate catalogues; use the add-on built for this Minecraft
version rather than copying an older jar across.
