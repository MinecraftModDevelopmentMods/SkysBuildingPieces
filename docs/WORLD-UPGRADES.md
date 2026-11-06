# Existing worlds

The separate Mineralogy piece add-on from the earlier local tests is no longer
needed. Use Building Pieces with Mineralogy instead. Its test-save piece IDs
are recovered automatically, including item stacks in chunks opened after the
first upgrade save. Counts, metadata, names and other item data are retained.
Do not load the old add-on jar alongside the new core.

Test this beta on a disposable copy, never the only copy of a world.

BuildingBricks 1.10.2-2.0.13 stores many pieces using generic block IDs and
material tile data. Sky's Building Pieces resolves both the saved material
and orientation before replacing a piece. It never guesses from an item's
temporary material index.

There is no supported BuildingBricks release for Minecraft 26.2. Recovery
from an older save is automatic and does not require the replacement setting.
The permanent Sky palette IDs and orientation values are unchanged. When upgrading from before 26.1, existing migration totals move to Minecraft’s namespaced data folder. The old file remains as a backup. The 1.14
loader turns old numeric states into named states, preserving the material and
orientation. Old pieces start dry. Wall connections, joined stairs, snow and
path supports are refreshed through Minecraft's normal chunk post-processing.
When Minecraft now has an equivalent piece, recovery uses that native block,
keeping its material and orientation. The old Sky palette remains registered
so its saved identity can still be read.

Old palette inventory items are converted to their matching material items,
keeping counts, names and custom data. A registry sidecar in the world's `data`
folder preserves the original numeric snapshot for chunks opened later. It also
keeps registrations added during later 1.12 saves, so newly added Sky pieces are
not lost behind an older recovery snapshot. Conflicting block numbers stop the
upgrade instead of being guessed. Keep that file with the world and its backups. Back up
the whole world before upgrading, and check every other mod's missing content.

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

Item totals count conversions, not the survival of another mod's container or
entity. If that mod cannot load its old cabinets or entities, their contents can
still be lost. Check the saved world as well as the migration report.

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

Ordinary dirt vertical slabs have separate recovery progress. Adding this
version to an already visited world converts those remaining slabs without
counting the earlier chunk visits again.

This release is not a universal replacement for BuildingBricks. Removing it
requires a complete audit of the particular world, matching material add-ons,
and a tested backup. Uncovered materials, shapes and tools may still need it.
Never confirm Forge's missing-content warning for an entry that still has saved
blocks or items without a proven recovery path.

Sky's Grass Slabs remains responsible for its own content. This mod does not
change its IDs, configuration, generation, saved state or migration markers.
