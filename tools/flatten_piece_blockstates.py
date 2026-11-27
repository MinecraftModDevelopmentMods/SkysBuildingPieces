"""Generate the individual blockstate files from the original palette models."""
import argparse
import json
from pathlib import Path


SIDES = ("north", "east", "south", "west")
CORNERS = ("north_west", "north_east", "south_east", "south_west")
STAIRS = ("east", "west", "south", "north")


def halves(directions, first="top", second="bottom"):
    return tuple(f"{half}_{direction}" for half in (first, second) for direction in directions)


ORIENTATIONS = {
    "slab": ("top", "bottom"),
    "vertical_slab": ("south", "west", "north", "east"),
    "horizontal_step": halves(SIDES),
    "vertical_step": CORNERS,
    "step": halves(SIDES) + CORNERS,
    "corner": halves(CORNERS),
    "stairs": halves(STAIRS, "bottom", "top"),
    "wall": (None,),
    "pane": (None,),
}


def generate(assets, namespace, catalogue, prefix):
    definitions = json.loads((assets / catalogue / "catalogue/palettes.json").read_text(encoding="utf-8"))
    blockstates = assets / namespace / "blockstates"
    written = set()
    for palette in definitions:
        shape = palette["shape"].lower()
        orientations = ORIENTATIONS[shape]
        source = blockstates / (prefix + palette["id"] + ".json")
        variants = json.loads(source.read_text(encoding="utf-8"))["variants"]
        by_material = [{} for _ in palette["materials"]]
        for selector, model in variants.items():
            properties = dict(entry.split("=", 1) for entry in selector.split(",") if entry)
            metadata = int(properties.pop("meta", "0"))
            slot, orientation = divmod(metadata, len(orientations))
            name = orientations[orientation]
            if name is not None:
                properties["orientation"] = name
            key = ",".join(f"{prop}={value}" for prop, value in sorted(properties.items()))
            if key in by_material[slot]:
                raise ValueError(f"Duplicate blockstate selector in {source}: {key}")
            by_material[slot][key] = model
        for material, states in zip(palette["materials"], by_material, strict=True):
            path = material.split(":", 1)[1]
            destination = blockstates / f"{prefix}{path}_{shape}.json"
            if not states or destination in written or destination == source:
                raise ValueError(f"Invalid flattened blockstate: {destination}")
            written.add(destination)
            destination.write_text(json.dumps({"variants": states}, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Generated {len(written)} individual blockstates in {namespace} from {catalogue}.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("assets", type=Path)
    parser.add_argument("namespace")
    parser.add_argument("--catalogue")
    parser.add_argument("--prefix", default="")
    options = parser.parse_args()
    generate(options.assets, options.namespace, options.catalogue or options.namespace, options.prefix)
