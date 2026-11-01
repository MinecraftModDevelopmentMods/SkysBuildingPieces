# Building Pieces 0.3.8.116051

This build brings Building Pieces to Minecraft 1.16.5 and Forge 36.2.34.

- Keeps existing pieces and their orientations when upgrading from 1.12.2.
- Uses the native recipe book and waterlogging. Wet grass and path pieces
  become matching dirt pieces without losing their shape or water.
- Keeps edge placement for ordinary slabs, including the vanilla pieces the
  mod reuses. Vertical slabs still drop their ordinary slab item.
- Retains lowered wall panels, end posts, torch support and continuous matching
  glass walls.
- Supports the coordinated Grass Slabs 1.2.0.116051 build. Mineralogy remains
  optional and needs no separate piece add-on.
- Keeps later Sky registrations alongside older recovery information, including
  worlds that already have a migration sidecar.
- Preserves recovered inventories in saved chunks that had not finished
  population, and unlocks add-on recipes while the crafting table is still open.
- Keeps rough-stone slabs distinct from smooth stone during old-world upgrades.
  Stored rough-stone stair items also retain their material.

Back up the whole world before upgrading. Recovery covers supported building
pieces, not unrelated content from another mod. Use matching 1.16.5 add-ons.
