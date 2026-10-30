# Pieces and recipes

Mineralogy rock steps and corners are included automatically when Mineralogy
is installed. Three matching full rocks diagonally across the crafting grid
make twelve steps; two steps side by side make four corners. Vertical pairs
rebuild corners into steps and steps into Mineralogy's existing slabs.
Disabled native slab types are not used by recipes or smoothing. The rock
steps and corners remain craftable from full blocks.

Find the pieces in the Sky's Building Pieces creative tab. It includes the
vanilla pieces reused by the catalogue. Separate vertical slab items, old
templates and recovery holders stay hidden.

All custom walls have lower connecting panels and raised posts.
Straight runs do not need a post in every block; ends, corners, junctions and
blocks above keep their supporting posts. Matching glass panels look continuous,
with borders at posts, exposed ends and colour changes. Selection outlines follow
the visible glass, while wall collision still prevents jumping over it.
Ordinary and redstone torches can be placed on walls.

Use matching full blocks to craft building pieces. No template or tool is
needed. Existing Minecraft pieces keep their native items and recipes; the
BOP add-on follows the same patterns for its materials.

`X` is one matching full block, `H` is one matching slab, `Q` is one matching
step, and `.` is empty. Slabs and steps may be horizontal or vertical, but do
not mix the two item types in one recipe.
Patterns can be moved within the crafting grid; diagonal and stair patterns
also work mirrored. Each occupied slot consumes one item, not the whole stack.

| Piece | Top row | Middle row | Bottom row | Output |
| --- | --- | --- | --- | --- |
| Horizontal slab | `XXX` | `...` | `...` | 6 slabs |
| Dirt vertical slab | `X..` | `X..` | `X..` | 6 dirt vertical slabs |
| Missing slab variant | `X..` | `.X.` | `...` | 4 slabs |
| Step | `H..` | `.H.` | `...` | 4 steps |
| Soil step | `X..` | `.X.` | `...` | 8 steps |
| Corner | `QQ.` | `...` | `...` | 4 corners |
| Stairs | `X..` | `XX.` | `XXX` | 4 stairs |
| Wall | `XX.` | `XX.` | `XX.` | 6 walls |
| Pane | `XXX` | `XXX` | `...` | 16 panes |

Walls use two columns so their recipe does not overlap glass panes or nether
brick fences. Native cobblestone walls, slabs, stairs and glass panes keep
their usual recipes. Only supported shapes are available; there are no wooden
walls or horizontal soil slabs.

There is no separate vertical slab recipe when a regular slab exists. The same
item places either way. Old vertical slab items can be crafted back into regular
slabs one for one, retaining custom item data.

Two matching regular slabs side by side make their matching full block. This
also applies to supported BOP materials. A vertical pair keeps any existing
native decorative-block recipe. Smooth stone has no obtainable full block item
in this Minecraft version and therefore has no recombination recipe.

To rebuild larger pieces, put two matching corners one above the other to make
one step. Two matching steps in a column make one slab. This works in either
crafting grid and also covers the Building Pieces BOP add-on's materials.
Keep each pair the same material and item type; saved placement orientations
do not matter. Side-by-side steps still make four corners, and diagonal slabs
still make four steps.

Dirt and grass steps return the matching Grass Slabs item when that mod is
installed. Without it, ordinary dirt steps return the existing dirt vertical
slab. No new slabs are added for soil materials without a matching slab.

For raw stone, snow, decorative sandstone and mossy/cracked/chiseled stone brick
slabs, use two full blocks diagonally to make four slabs. Their full-block rows
already belong to native recipes, which remain unchanged.

For decorative sandstone and mossy/cracked/chiseled stone brick stairs, use six
matching regular slabs in the stair pattern to make four stairs. Their full-block
stair patterns still produce vanilla's ordinary sandstone or stone brick stairs.

Horizontal and vertical step items can also be rotated one for one. Either
kind can be cut into corners. Soil steps use full dirt, coarse dirt, podzol,
mycelium or grass blocks because their horizontal slabs are outside this mod.

Smooth stone has no obtainable full block item in Minecraft 1.12.2. Use
vanilla smooth stone slabs for its stairs and walls; the output
is half the normal full-block quantity. Its steps and corners follow the
normal slab and step recipes.

Put a supported old BuildingBricks piece in the grid by itself to convert one
item into its equivalent. The material and custom item data are preserved.
Automatic world recovery remains independent of crafting. Templates saved
by earlier betas are hidden and no longer cut pieces; craft one by itself to
recycle it into one paper while keeping its custom data.

Sky slab items can be placed horizontally or vertically. Click the centre of
a block face to place a slab against that face. Click near an edge to place it
along that edge instead: the top of a block can hold a vertical slab, and the
top or bottom edge of a side face can hold a horizontal slab. Sneaking does not
change slab placement. Old vertical slab items follow these same rules when
their regular slab exists.

Supported vanilla and BOP slab items use these placement rules too. While
BuildingBricks is installed, it continues handling its native slab placement.
Vertical slab items are hidden from creative inventory. Breaking, Silk Touch
and pick-block return the regular slab, with the material's usual drop rules.

Sneak while placing a step to turn it vertically. Matching pieces can combine
into a supported larger shape or the original full block. Unlike materials
do not combine. Some unions have no supported shape and cannot combine.

Grass shapes use biome colours and spread to ordinary dirt and matching dirt
pieces. Covered or dark grass becomes the matching dirt shape, retaining its
orientation. Steps and corners leave exposed ground grassy; pieces covering
the whole footprint keep dirt underneath. With Grass
Slabs 1.1.1.112021 or later installed, spreading also works across its slabs and turf.
Plants and bonemeal need a complete upper supporting face. Nearby snow gives
soil and grass pieces a visual snow cap without changing their collision.

Use a compatible shovel on an uncovered ordinary dirt or grass step or corner
to make a path. The piece keeps its shape and orientation; only its exposed
top drops by one pixel. Each use costs one durability, except in creative mode.
Flattening a grass piece also turns an ordinary grass block directly beneath
it into a path. Both changes use that same durability point. Other supporting
blocks stay as they are.
Paths drop matching dirt pieces, even with Silk Touch. A solid block above a
path turns it back into the matching dirt shape. Coarse dirt, podzol and
mycelium are not converted. Grass Slabs still provides the full-width path slabs.

Without Grass Slabs, three ordinary dirt blocks in a column make six dirt vertical slabs.
Place them in any of the four directions; complementary halves combine
into a vanilla dirt block.
With Grass Slabs installed, its regular dirt slab can also be placed vertically
and is returned when a dirt vertical slab is broken. Without Grass Slabs, the
dirt-only vertical item and recipe remain available; it stays hidden in creative.

Horizontal soil slabs, vertical grass or other soil slabs, turf, path slabs and
wooden walls are outside this mod. No new terrain is generated.
