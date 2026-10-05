# Pieces and templates

Use vanilla full blocks and reusable templates to cut building pieces. Materials
that already have a matching Minecraft piece use that existing block and item.

Seven paper and stick patterns make templates for horizontal slabs, vertical
slabs, steps, corners, stairs, walls and panes. Each recipe makes one template.
The pattern and the template picture suggest its shape.

Use these patterns in a crafting table. `P` is paper, `S` is a stick, and `.`
is an empty slot. Each pattern makes one reusable template.

| Template | Top row | Middle row | Bottom row |
| --- | --- | --- | --- |
| Horizontal slab | `PPP` | `SSS` | `...` |
| Vertical slab | `PS.` | `PS.` | `PS.` |
| Step | `PP.` | `SS.` | `...` |
| Corner | `P..` | `S..` | `...` |
| Stairs | `P..` | `PP.` | `SSS` |
| Wall | `.P.` | `PPP` | `SSS` |
| Pane | `SPS` | `SPS` | `SPS` |

One full block and a template make two slabs, four steps or eight corners.
Six matching full blocks and a stairs template make four stairs. A wall
template and six full blocks make six walls; a pane template makes sixteen
panes. The template is returned unchanged, including custom item data.

Put each of the six input blocks in a separate crafting slot. Smooth stone
has no obtainable full block item in Minecraft 1.10.2, so use its ordinary
stone slabs instead; these recipes produce half the usual quantity.

A matching template can also convert a supported BuildingBricks item into
its Sky or vanilla equivalent. This is an explicit crafting choice and works
even when automatic replacement is disabled.

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

Grass shapes use biome colours. Covered or dark grass can become the matching
dirt shape. Plants need a complete upper supporting face. Nearby snow gives
soil and grass pieces a visual snow cap without changing their collision.

Horizontal and vertical soil or grass slabs, turf, path slabs and wooden
walls are deliberately outside this mod. No new terrain is generated.
