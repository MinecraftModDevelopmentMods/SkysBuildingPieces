# Changelog

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
