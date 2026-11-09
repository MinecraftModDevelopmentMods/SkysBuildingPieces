"""Generate the inventory model definitions that point to the existing item models."""
import argparse
import json
from pathlib import Path
import re

from prepare_1_14_materials import format_json


def generate(resources: Path, write: bool) -> None:
    assets = resources / "assets/skysbuildingpieces"
    stale = []
    models = sorted((assets / "models/item").glob("*.json"))
    for source in models:
        definition = {"type": "minecraft:model", "model": f"skysbuildingpieces:item/{source.stem}"}
        if re.fullmatch(r"grass_(?:corner|stairs|step)(?:_\d+)?", source.stem):
            definition["tints"] = [{"type": "minecraft:grass", "temperature": 0.5, "downfall": 1.0}]
        if source.stem.startswith("template_"):
            # The wood-coloured marks use the same sprite atlas as the paper.
            definition["tints"] = [{"type": "minecraft:constant", "value": 0x996638}]
        # Minecraft reads every item definition, including items whose mod
        # isn't installed. Load the Mineralogy definitions with its own pack.
        item_assets = (resources / "optional/mineralogy/assets/skysbuildingpieces"
                       if source.stem.startswith("mineralogy_") else assets)
        target = item_assets / "items" / source.name
        expected = format_json({"model": definition}) + "\n"
        if not target.is_file() or target.read_text(encoding="utf-8") != expected:
            stale.append(target)
            if write:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(expected, encoding="utf-8", newline="\n")
    if stale and not write:
        raise SystemExit(f"{len(stale)} item definitions need updating; run this tool with --write.")
    print(f"{len(models)} item definitions checked.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=Path("src/main/resources"))
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    generate(args.resources, args.write)
