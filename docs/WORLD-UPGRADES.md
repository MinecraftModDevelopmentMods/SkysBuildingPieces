# Existing worlds

Test this beta on a disposable copy, never the only copy of a world.

BuildingBricks 1.10.2-2.0.13 stores many pieces using generic block IDs and
material tile data. Sky's Building Pieces resolves both the saved material
and orientation before replacing a piece. It never guesses from an item's
temporary material index.

With BuildingBricks installed, replacement is off by default. When explicitly
enabled, supported vanilla pieces can be converted as chunks, containers,
players and item entities load. Unsupported materials and excluded soil slabs
remain with the older mod. Reports are written under the world's `serverconfig`
directory, and progress has its own world state.

Supported recovery is automatic when the older mod is absent. If a covered
piece or item cannot be recovered safely, loading stops with an explanation.
Restore the older mod and its material add-ons, then return to a backup.

The report is `serverconfig/skysbuildingpieces-migration-report.txt` inside
the world folder. It lists completed conversions and unresolved material and
shape pairs. Turning replacement off preserves previous conversions, counters
and reports. It does not restore old pieces.

The BOP add-on has separate progress and totals. It can process a chunk that
was already visited by the vanilla core without repeating earlier conversions.
Removing the add-on does not erase its saved history. Unresolved counts include
past encounters and are not an inventory of content still awaiting replacement.

Editable item handlers are updated as their containers load. Serialized
item stacks are also recovered before the game reads them, preserving counts,
enchantments and other custom data. Unopened loot containers are not opened
to perform migration.
Saved contents of sealed item containers are recovered without opening them.
Their container identity, seal and unrelated custom data are retained.

This release does not provide a complete replacement for BuildingBricks.
Uncovered biome materials, excluded soil slabs and tools may still require it. Do not
remove it from Sylvester or another mixed modpack on the basis of this beta.

Sky's Grass Slabs remains responsible for its own content. This mod does not
change its IDs, configuration, generation, saved state or migration markers.
