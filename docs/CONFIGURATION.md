# Configuration

Forge creates `config/skysbuildingpieces.cfg`. Stop the game or server before
editing it, then restart.

`compat.forceReplaceBuildingBricksPieces=false`

The default leaves installed BuildingBricks pieces and items unchanged.
Set this to `true` only on a world backup when you want supported vanilla
material pieces replaced as they load. With the BOP add-on installed, this same
setting also replaces its supported materials. Replacement cannot automatically be undone.
Turning the option off stops further replacements, not previous conversions.

This mod has no world generation setting. It does not change Sky's Grass Slabs
configuration, generation or migration progress.

## Block-ID budget for modpacks

Building Pieces uses 236 block IDs: 235 building blocks and one hidden recovery
holder. Materials share fixed metadata palettes so they don't each need a
separate block ID for every shape and orientation. Saved palette assignments
do not change between installations.

The matching Biomes O Plenty add-on uses another 74 IDs, for 310 combined.
These totals exclude blocks already registered by Minecraft and Biomes O Plenty.
