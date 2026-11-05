# Content integration

Catalogue API version 1 remains supported. Register add-on catalogues during
mod construction, before registry events; permanent palette slots must never be reassigned.

The JSON-array overload of `registerCatalogue` accepts resolved material and
palette definitions. Use it when a native mod can disable some shapes, without
changing the permanent piece palettes. A material can set
`fullBlockStepRecipe: true` to cut three diagonal full blocks into twelve
steps instead of using the usual two-slab recipe.

Building Pieces 0.3 also provides `BuildingPiecesApi.bottomPiece(material,
TerrainShape, direction)` during initialization, after the block registry event. It returns an existing block
state or `null` when that material has no such piece. It registers nothing.
Shapes are `SLAB`, `STEP` and `CORNER`. Directions are north, east, south and
west; corners use those directions for NW, NE, SE and SW respectively.

Sky's Terrain Smoother uses this lookup without depending on palette internals.
Building Pieces never generates terrain itself. It optionally joins Grass
Slabs' grass-form integration, using 1.2.0.120062 or later on Minecraft 1.20.6.
It works without either Grass Slabs or Terrain Smoother installed.

Mineralogy is supported directly. Its presence adds 35 fixed rock-piece
palettes during construction; no add-on or compile-time Mineralogy
dependency is required. The original rocks, textures and slabs remain owned
by Mineralogy. Disabled native slabs are not used in recipes or smoothing.

On NeoForge 20.6, call `registerBlocks(namespace, registry)` and
`registerItems(namespace, registry)` from the contributor's own mod-bus `RegisterEvent`
listener. Pass `BuiltInRegistries.BLOCK` during `Registries.BLOCK` and
`BuiltInRegistries.ITEM` during `Registries.ITEM`. Catalogue registration must finish before any block registry event;
the first block registration freezes the catalogue. The helpers register only
that contributor's namespace.

The API returns native `BlockState` values. The permanent palette IDs and slots
remain unchanged, while ordinary inventory items have one identity per material
and shape. Use the public lookup rather than constructing inventory metadata.

For a retired material, `replacement` can name another registered material.
Keep its original ID and palette slots: saved pieces then use the replacement's
matching geometry. Unknown targets and replacement cycles are rejected before
the catalogue is published. This is for explicit material changes, not a
fallback for missing dependencies.

Full blocks with several state variants can call
`BuildingPiecesApi.registerStateItems(block, lookup)` during item registration.
The lookup returns the normal item for a block state, so recipes and drops can
use distinct items instead of inventory metadata. Duplicate or late lookups
are rejected.
