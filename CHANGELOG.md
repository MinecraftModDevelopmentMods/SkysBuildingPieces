# Changelog

## 0.3.7.110021

- Use a shovel to turn dirt and grass steps and corners into matching path
  pieces. Their shape and direction stay the same, with the top lowered by
  one pixel.
- Flattening a grass step or corner also turns ordinary grass directly beneath
  it into a path. Both changes use one shovel action and one durability point.
- Grass steps and corners keep their grass and snow fringes, and leave exposed
  grass visible underneath.
- Stack two matching corners in the crafting grid to make a step, or two steps
  to make a slab. The same recipes work with supported add-on materials.
- Mineralogy's 27 natural rocks now get steps and corners automatically when
  Mineralogy is installed. Its existing slabs are used when rebuilding pieces.
  No separate Mineralogy piece add-on is needed.
- Saves made with the earlier Mineralogy piece add-on keep their materials,
  orientations and item data when moving to the built-in support.

## 0.3.6.110021

- Shortened piece names, including Gold Wall, Gold Stairs and Oak Step.
- Tidied material names and word order across all 18 languages.
- Kept recipes, registry IDs, metadata and world migration unchanged.

## 0.3.5.110021

- Gave all custom walls lower panels and raised posts, including gold and packed ice.
- Kept posts at ends, corners, junctions and beneath blocks or torches above.
- Matched selection and collision shapes to the wall profile and removed internal faces.
- Kept existing materials, registry IDs, recipes and saved palette assignments unchanged.
- Updated the BOP add-on wall models to use the same profile.

## 0.3.4.110021

- Matching clear and stained-glass walls now join without a repeated border
  between blocks. Posts, exposed ends and colour changes keep their borders.
- Each wall block can still be placed, selected and broken separately.

## 0.3.3.110021

- Torches and redstone torches can now be placed on top of walls. Glass walls
  form a supporting post when needed.
- The glass-wall selection outline now follows the post and connections
  rather than drawing a large box around the empty space between them.

## 0.3.2.110021

- Glass walls now form continuous straight runs, with posts at ends, corners
  and junctions instead of on every block. Posts also support blocks above.
- Wall selection and collision follow the changing post shape.

## 0.3.1.110021

- Added a Sky's Building Pieces creative tab, including the vanilla pieces
  used by the mod. Vertical slabs and old templates remain hidden.
- Gave glass walls raised posts and lower, narrower connections.
- Removed the internal glass faces that made connected walls look dark and
  overlapping. Different glass colours keep their visible boundary.

## 0.3.0.110021

- Added a piece lookup interface for Terrain Smoother and shared grass rules
  when the updated Grass Slabs mod is installed. The core still works alone.
- Added ordinary dirt vertical slabs.
- Replaced cutting templates with ordinary shaped recipes for every material.
- Kept native recipes and added one-for-one slab rotation in the crafting grid.
- Old beta templates are hidden and can be recycled into paper.
- Added recovery for legacy dirt vertical slabs, including already visited chunks.
- Kept existing palette assignments and migration history unchanged.

## 0.2.0.110021

- Added support for material add-ons using the existing cutting templates.
- Kept separate migration progress and totals for each supported catalogue.
- Preserved existing block IDs, metadata and saved vanilla migration history.
- Added per-material fire behaviour for woods such as hellbark.

## 0.1.0.110021

First beta for Minecraft 1.10.2.

- Vanilla building pieces, with existing Minecraft pieces reused where possible.
- Seven reusable cutting templates.
- Slab placement follows the clicked face and edge, including vertical halves.
- Optional replacement of supported pieces from older worlds.
- Names in 18 language variants.
- No terrain generation.

Soil and grass slabs, turf, path slabs and wooden walls are not included.
Keep the older building mod installed if the world still needs its other content.
