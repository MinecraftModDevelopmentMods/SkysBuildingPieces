# Changelog

## 0.3.10.2603001

- Let grass pieces spread and decay naturally, including beneath solid cover.
- Improve snow caps and cover the exposed grass below snowy pieces.
- Keep snow pieces when dug by hand and scale wooden-piece fuel to its size.
- Make wooden pieces work as fuel in furnaces, smokers and blast furnaces.
- Separate different crafting results in the recipe book and retain their unlocks.


## 0.3.9.2603001

- Removed duplicate grass and dirt slab recipes when Sky's Grass Slabs is installed.
- Each material and shape now has its own block ID. Existing pieces keep their shapes and orientation.
- The creative tab shows the mod's own pieces.
- Directional slab placement is on by default and can be turned off in the Mods menu.

## 0.3.8.2603001

- Updated Building Pieces for Minecraft 26.3 on Forge.
- Existing pieces, recipes and migration totals carry forward.
- The Building Pieces tab now uses Minecraft's wool slabs and stairs. Older
  Sky wool slabs and stairs still load and can be exchanged in the crafting grid.

## 0.3.8.2602001

- Updated Building Pieces for Minecraft 26.2 on Forge.
- Existing pieces, recipes and migration totals carry forward.
- Larger modded saves can upgrade without losing their saved registry data.

## 0.3.8.2601021

- Bring Building Pieces to Minecraft 26.1.2 on Forge.
- Carry forward the saved pieces, recipes and migration totals from earlier worlds.

## 0.3.8.121111

- Update Building Pieces to Minecraft 1.21.11 on Forge.
- Keep the existing pieces, recipes and supported world upgrades.
- Grass Slabs and Mineralogy remain optional.

## 0.3.8.121011

- Bring Building Pieces to Minecraft 1.21.1 on Forge.
- Keep existing pieces, recipes and supported world upgrades.
- Grass Slabs and Mineralogy are still optional.

## 0.3.8.120061

- Bring the building pieces and recipes to Minecraft 1.20.6.
- Keep custom names and other item data when rotating or recovering pieces.
- Keep Grass Slabs and Mineralogy optional.

## 0.3.8.120011

- Bring the building pieces, creative tab and continuous glass walls to Minecraft 1.20.1.
- Preserve existing pieces, their recipes and supported world upgrades.
- Keep Grass Slabs and Mineralogy integration optional.

## 0.3.8.119041

- Bring the building pieces, creative tab and continuous glass walls to Minecraft 1.19.4.
- Keep the existing palettes, recipes and supported world recovery.
- Retain optional Grass Slabs and Mineralogy integration without requiring either mod.


## 0.3.8.118021 - Minecraft 1.18.2 (unreleased)

- Bring the existing building shapes and Mineralogy pieces to Minecraft 1.18.2.
- Keep placement, joins, continuous glass walls, grass, snow, waterlogging and paths.
- Keep saved palettes, orientations, inventories and custom item data when upgrading.
- Preserve recovery progress in the new chunk format, including add-on coverage.
- Keep add-on biome names through Minecraft's conversion to section palettes.
- Fix loading of older worlds with gaps in their saved biome numbers.
- Use the matching Grass Slabs version for shared grass behaviour when installed.

## 0.3.8.117011 - Minecraft 1.17.1 (unreleased)

- Bring the existing building shapes and Mineralogy pieces to Minecraft 1.17.1.
- Keep placement, joins, continuous glass walls, grass, snow, waterlogging and paths.
- Show recipes in the recipe book when their matching ingredients are collected.
- Keep saved palettes, orientations and custom item data when upgrading.
- Keep add-on material names short and translated, including BOP wood pieces.
- Keep the full material's mining rules for pieces made from retired rocks.
- Fix a startup crash when several mods preserve old world data.

## 0.3.8.116051 - Minecraft 1.16.5 (unreleased)

- Bring the existing building shapes and Mineralogy pieces to Minecraft 1.16.5.
- Keep placement, joins, continuous glass walls, grass, snow, waterlogging and paths.
- Preserve saved palette identities, older pieces and custom item data.
- Use the matching Grass Slabs version for shared grass behaviour when installed.

## 0.3.8.115021 - Minecraft 1.15.2 (unreleased)

- Bring the existing building shapes and Mineralogy pieces to Minecraft 1.15.2.
- Keep placement, joining, glass walls, grass, snow, waterlogging and paths.
- Keep saved palette identities and recovery of older pieces and item data.
- Use the matching Grass Slabs release for shared grass behaviour when installed.

## 0.3.8.114041

Minecraft 1.14.4 support, native waterlogging and recipe-book integration.
Existing piece palettes, orientations and custom item data are kept when
upgrading from 1.12.2. Saved item frames also load without stalling the world.
See the [release notes](docs/RELEASE-0.3.8.114041.md).

## 0.3.8.112021

- Added the missing recipe-book unlocks for crafting and rebuilding pieces.
- Made the next recipes appear while the crafting table is still open.
- Keep recipe previews on the normal item model, including saved orientation variants.

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
