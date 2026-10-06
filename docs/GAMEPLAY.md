# Pieces and recipes

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
| Vertical slab | `X..` | `X..` | `X..` | 6 vertical slabs |
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

A single horizontal slab in the grid becomes one matching vertical slab,
and a single vertical slab becomes one horizontal slab. This also makes the
ordinary stone slab available without changing vanilla's three-stone recipe,
which still produces smooth stone slabs. Snow slabs, decorative sandstone slabs
and mossy/cracked/chiseled stone brick slabs also use this route: their full-block
rows already belong to vanilla recipes.

For decorative sandstone and mossy/cracked/chiseled stone brick stairs, use six
matching vertical slabs in the stair pattern to make four stairs. Their full-block
stair patterns still produce vanilla's ordinary sandstone or stone brick stairs.

Horizontal and vertical step items can also be rotated one for one. Either
kind can be cut into corners. Soil steps use full dirt, coarse dirt, podzol,
mycelium or grass blocks because their horizontal slabs are outside this mod.

Smooth stone has no obtainable full block item in Minecraft 1.10.2. Use
vanilla smooth stone slabs for its vertical slabs, stairs and walls; the output
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
change slab placement. Vertical slab items always stay vertical.

Existing vanilla slab items keep their usual placement rules, or the rules
provided by BuildingBricks while it is installed.

Sneak while placing a step to turn it vertically. Matching pieces can combine
into a supported larger shape or the original full block. Unlike materials
do not combine. Some unions have no supported shape and cannot combine.

Grass shapes use biome colours and spread to ordinary dirt and matching dirt
pieces. Covered or dark grass becomes the matching dirt shape, retaining its
orientation. Grass pieces keep their grass-block support as dirt. With Grass
Slabs 1.1 installed, spreading also works across its slabs and turf.
Plants and bonemeal need a complete upper supporting face. Nearby snow gives
soil and grass pieces a visual snow cap without changing their collision.

Three ordinary dirt blocks in a column make six dirt vertical slabs.
Place them in any of the four directions; complementary halves combine
into a vanilla dirt block.

Horizontal soil slabs, vertical grass or other soil slabs, turf, path slabs and
wooden walls are outside this mod. No new terrain is generated.
