# Trying the beta

For Mineralogy, use the core jar with Mineralogy and its OreSpawn dependency.
Do not install the separate Mineralogy piece add-on from the earlier tests.
The generated `runMineralogyClient` Eclipse profile loads the native mods in
a separate development folder. Check rock textures, all step and corner
orientations, cutting recipes and rebuilding to the native slab.

Install the main jar in a Minecraft 1.12.2 Forge 14.23.5.2859 instance using
Java 8. The sources and Javadoc jars are not game mods. Start with a new
creative world; use a separate copy when trying an existing world.

Before relying on the beta in a modpack, check:

- Check names such as Gold Wall, Gold Stairs and Oak Step. Switch the game
  language and check translated piece names too. Renamed items keep their custom names.
- Check gold, packed ice and the other wall materials in straight runs, corners
  and junctions. The panels should be lower than the posts. Add and remove a
  block or torch above the middle of a run, then check selection and collision.
- Open the Sky's Building Pieces creative tab. Reused vanilla pieces such as
  oak slabs should appear once, without separate vertical slabs or old templates.
- Build clear and stained glass walls in straight runs, corners and junctions.
  Matching panels should look continuous; exposed ends, posts and colour changes
  should keep their borders. Add and remove a block above a straight run to check
  that its supporting post appears and disappears.
- Aim at the lower glass panels and taller posts. Selection should follow their
  shape. Place ordinary and redstone torches on the walls, then remove the wall
  beneath them to check that the torches drop.
- Place slabs, steps and corners in every direction, including upside down
  and vertical placements. Sneaking turns steps vertically.
- With a slab such as oak, glass or wool, click the centre and edges of each
  block face. The same slab item should place horizontally or vertically,
  and matching halves should combine into the full block.
- Join matching pieces and try unlike materials. Only supported matching
  combinations should succeed, consuming the right number of items.
- Put full blocks above and beside pieces. Exposed internal faces should
  remain visible; glass and ice should retain their transparency.
- Craft several materials using the patterns in the gameplay guide. Check
  quantities, steps and corners, missing slab variants and unchanged native recipes.
- Break vertical slabs with ordinary and Silk Touch tools, then try pick-block.
  Each should return the regular slab; vertical items should be hidden in creative.
- If upgrading an earlier beta, recycle a saved template into paper. New
  templates should not appear in creative inventory or crafting results.
- Check grass colours in several biomes, nearby snow, covered grass becoming
  dirt, plant support, breaking drops and Silk Touch.
- Use a shovel on a grass step or corner above ordinary grass. Both should
  become paths, with one durability point used. Check after saving and reloading.
- Check wall and pane connections and stair corners, including their collision.
- With BuildingBricks present, first leave replacement disabled. On a second
  backup enable it, restart, and check converted blocks, containers and item
  entities. Save and reload, then inspect the migration report.

Check ordinary dirt vertical slabs in all four directions and combine matching
halves into dirt. When testing removal of BuildingBricks, use a fresh backup
and audit all remaining content first. BOP materials need the matching add-on;
unsupported shapes and tools still need their original mod.
