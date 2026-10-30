# Changelog

## 0.3.7.112021

- Use a shovel to turn ordinary dirt and grass steps and corners into paths.
  They keep their orientation, with the top lowered by one pixel.
- Flattening a grass step or corner also turns the grass block beneath it into
  a path, using the shovel once for both changes.
- Grass steps and corners keep their grass and snow edges, with grass left
  visible beneath partial pieces.
- Rebuild two corners into one step and two steps into one slab by stacking
  them in the crafting grid. Matching BOP pieces work the same way.
- Mineralogy rock steps and corners are now included when Mineralogy is
  installed. They rebuild into its native slabs; no separate add-on is needed.
  Saves made with the earlier test add-on keep their pieces and item data.
- When Grass Slabs is installed, use 1.1.1.112021 or later for shared grass
  behaviour. Building Pieces still works on its own.

## 0.3.6.112021

- Shortened piece names, including Gold Wall, Gold Stairs and Oak Step.
- Tidied material names and word order across all 18 languages.
- Kept recipes, registry IDs, metadata and world migration unchanged.

## 0.3.5.112021

- Gave all custom walls lower panels and raised posts, including gold and packed ice.
- Kept posts at ends, corners, junctions and beneath blocks or torches above.
- Matched selection and collision shapes to the wall profile and removed internal faces.
- Kept existing materials, registry IDs, recipes and saved palette assignments unchanged.
- Updated the BOP add-on wall models to use the same profile.

## 0.3.4.112021

- Added a Sky's Building Pieces creative tab, including the vanilla pieces the mod reuses.
- Made clear and stained glass walls continuous where the material and colour match.
  Borders remain at posts, exposed ends and colour changes.
- Added raised posts at wall ends, corners, junctions and beneath blocks above.
  Straight glass runs have lower panels without a post in every block.
- Matched glass-wall selection outlines to the panels and posts, and removed
  hidden internal glass faces.
- Allowed ordinary and redstone torches on walls.
- Kept existing pieces, palette assignments, recipes and world recovery unchanged.

## 0.3.0.112021

- Kept separate recovery progress for removed BOP decorations supplied by the add-on.
- Added Minecraft 1.12.2 support with the existing IDs, palettes and save state.
- Kept native crafting recipes intact when other material mods are installed.
- Recovered supported legacy stacks before containers read them, including
  furniture inventories with their own saved-data layout.

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
