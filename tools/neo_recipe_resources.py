"""Translate generated recipes and unlocks to NeoForge's data-pack conditions."""

import copy
import hashlib
import json


CONDITIONS = "neoforge:conditions"
WOODEN_RODS = "c:rods/wooden"


def condition(value):
    if value["type"] in {"forge:item_exists", "neoforge:item_exists"}:
        return {"type": "neoforge:registered", "registry": "minecraft:item", "value": value["item"]}
    result = dict(value)
    result["type"] = result["type"].replace("forge:", "neoforge:")
    if "value" in result:
        result["value"] = condition(result["value"])
    if "values" in result:
        result["values"] = [condition(part) for part in result["values"]]
    return result


def keys(value):
    if isinstance(value, list):
        return [keys(row) for row in value]
    if isinstance(value, dict):
        return {key: keys(row) for key, row in value.items()}
    if value == "forge:rods/wooden":
        return WOODEN_RODS
    if value == "#forge:rods/wooden":
        return "#" + WOODEN_RODS
    return value


def native_data(value):
    result = keys(value)
    guard = result.pop("forge:condition", None)
    if guard:
        result[CONDITIONS] = [condition(guard)]
    return result


def recipe(value):
    if value.get("type") != "forge:conditional":
        return native_data(value)
    guards = [condition(choice["forge:condition"]) for choice in value["recipes"]]
    return {
        "type": "skysbuildingpiecesbop:material_recipe",
        CONDITIONS: [{"type": "neoforge:or", "values": guards}],
        "recipes": [{CONDITIONS: [guard], **native_data(choice["recipe"])}
                    for guard, choice in zip(guards, value["recipes"])],
    }


def advancement(value, extra):
    choices = value.get("advancements", value.get("forge:conditional"))
    if choices is None:
        return native_data(value)
    guards = [condition(choice["forge:condition"]) for choice in choices]
    originals = [{k: v for k, v in choice.items() if k != "forge:condition"} for choice in choices]
    result = copy.deepcopy(originals[0])
    for other in originals[1:]:
        if result["requirements"] != other["requirements"] or result["rewards"] != other["rewards"]:
            raise ValueError("Material alternatives do not share recipe and unlock identities")
        if result["criteria"].keys() != other["criteria"].keys():
            raise ValueError("Material alternatives do not share unlock criteria")
    for criterion in result["criteria"]:
        alternatives = [row["criteria"][criterion] for row in originals]
        if all(row == alternatives[0] for row in alternatives):
            continue
        # This unlock has one material per branch. A tag can reference the
        # optional native item without making advancement loading require it.
        ingredients = set()
        for entry in alternatives:
            predicates = entry["conditions"]["items"]
            if len(predicates) != 1 or set(predicates[0]) != {"items"}:
                raise ValueError("Cannot merge alternative inventory conditions")
            items = predicates[0]["items"]
            if not isinstance(items, list) or len(items) != 1:
                raise ValueError("Expected one material for each inventory condition")
            ingredients.update(items)
        names = sorted(ingredients)
        tag = "recipe_materials/" + hashlib.sha256("\n".join(names).encode()).hexdigest()[:16]
        path = "data/skysbuildingpiecesbop/tags/item/" + tag + ".json"
        extra[path] = {"replace": False, "values": [{"id": item, "required": False} for item in names]}
        result["criteria"][criterion]["conditions"]["items"] = [{"items": "#skysbuildingpiecesbop:" + tag}]
    result[CONDITIONS] = [{"type": "neoforge:or", "values": guards}]
    return result


def convert(resources):
    extra = {}
    outputs = {path: (advancement(value, extra) if "/advancement/" in path else recipe(value))
               for path, value in resources.items()}
    return outputs | extra
