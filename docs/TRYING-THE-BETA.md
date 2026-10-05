# Trying the beta

Install the main jar in a Minecraft 1.10.2 Forge 12.18.3.2511 instance using
Java 8. The sources and Javadoc jars are not game mods. Start with a new
creative world; use a separate copy when trying an existing world.

Before relying on the beta in a modpack, check:

- Place slabs, steps and corners in every direction, including upside down
  and vertical placements. Sneaking turns steps vertically.
- With a Sky slab such as glass or wool, click the centre and edges of each
  block face. The same slab item should place horizontally or vertically,
  and matching halves should combine into the full block.
- Join matching pieces and try unlike materials. Only supported matching
  combinations should succeed, consuming the right number of items.
- Put full blocks above and beside pieces. Exposed internal faces should
  remain visible; glass and ice should retain their transparency.
- Craft each template, then cut several materials. Check the output amounts
  and that a renamed template is returned with its name and other data intact.
- Check grass colours in several biomes, nearby snow, covered grass becoming
  dirt, plant support, breaking drops and Silk Touch.
- Check wall and pane connections and stair corners, including their collision.
- With BuildingBricks present, first leave replacement disabled. On a second
  backup enable it, restart, and check converted blocks, containers and item
  entities. Save and reload, then inspect the migration report.

Do not remove BuildingBricks from a mixed world such as Sylvester yet. This
beta covers vanilla building materials only; BOP materials, excluded soil
slabs and other unsupported content still need their original mod.
