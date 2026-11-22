# Biome upgrade fixture

`saved-bop-1.12.2.nbt` is an unchanged chunk from the locked Building Pieces
BOP 1.12.2 fixture. Its provenance and checksums are in the adjacent JSON file.
The original biome is BOP's gravel beach.

The tests pass this chunk through Minecraft's actual chunk upgrader, using a
test-only biome alias. A second case mixes its biome columns with vanilla
biomes to check sampling and palette storage. These tests cover biome
serialization; they do not qualify this chunk's blocks or items, or add a
production BOP replacement mapping.
