# Content integration

Catalogue API version 1 remains supported. Register add-on catalogues during
pre-initialization as before; permanent palette slots must never be reassigned.

The JSON-array overload of `registerCatalogue` accepts resolved material and
palette definitions. Use it when a native mod can disable some shapes, without
changing the permanent piece palettes. A material can set
`fullBlockStepRecipe: true` to cut three diagonal full blocks into twelve
steps instead of using the usual two-slab recipe.

Building Pieces 0.3 also provides `BuildingPiecesApi.bottomPiece(material,
TerrainShape, direction)` after pre-initialization. It returns an existing block
state or `null` when that material has no such piece. It registers nothing.
Shapes are `SLAB`, `STEP` and `CORNER`. Directions are north, east, south and
west; corners use those directions for NW, NE, SE and SW respectively.

Sky's Terrain Smoother uses this lookup without depending on palette internals.
Building Pieces never generates terrain itself. It optionally joins Grass
Slabs 1.1's grass-form integration, but works without either mod installed.

Mineralogy is supported directly. Its presence adds 35 fixed rock-piece
palettes during pre-initialization; no add-on or compile-time Mineralogy
dependency is required. The original rocks, textures and slabs remain owned
by Mineralogy. Disabled native slabs are not used in recipes or smoothing.
