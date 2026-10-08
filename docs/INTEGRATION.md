# Content integration

Catalogue API version 1 remains supported. Register add-on catalogues during
pre-initialization as before; permanent palette slots must never be reassigned.

Building Pieces 0.3 also provides `BuildingPiecesApi.bottomPiece(material,
TerrainShape, direction)` after pre-initialization. It returns an existing block
state or `null` when that material has no such piece. It registers nothing.
Shapes are `SLAB`, `STEP` and `CORNER`. Directions are north, east, south and
west; corners use those directions for NW, NE, SE and SW respectively.

Sky's Terrain Smoother uses this lookup without depending on palette internals.
Building Pieces never generates terrain itself. It optionally joins Grass
Slabs 1.1's grass-form integration, but works without either mod installed.
