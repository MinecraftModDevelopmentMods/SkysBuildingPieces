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

Without Mineralogy, Building Pieces uses 238 block IDs: 237 building blocks and one hidden recovery
holder. Materials share fixed metadata palettes so they don't each need a
separate block ID for every shape and orientation. Saved palette assignments
do not change between installations.

The matching Biomes O Plenty add-on uses another 66 IDs, for 304 combined.
Mineralogy support adds 35 IDs only when Mineralogy is installed, for 273 core
IDs in that setup. The vanilla catalogue still uses 238 IDs. The optional rock
catalogue has its own fixed 35-ID budget; it does not change existing palettes.
These totals exclude blocks already registered by Minecraft, Biomes O Plenty
and Mineralogy.
With both Mineralogy and the BOP add-on installed, the combined Sky pieces use
339 IDs (238+35+66).
