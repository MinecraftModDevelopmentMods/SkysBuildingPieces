"""Keep optional slab ownership and distinct catalogue recipes intact."""
import json
from pathlib import Path
import unittest
from prepare_1_14_recipes import native_conflict, prepare

ROOT = Path(__file__).resolve().parents[1]


class RecipeOwnershipTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.recipes, cls.unlocks = prepare(ROOT / "src/main/resources")

    def test_soil_slabs_are_rebuilt_by_grass_slabs(self):
        for soil in ("dirt", "grass"):
            self.assertNotIn("minecraft/" + soil + "/slab_recombination", self.recipes)

    def test_book_groups_are_named_for_the_result_not_the_source_namespace(self):
        for identity, recipe in self.recipes.items():
            if "result" in recipe:
                self.assertEqual("skys/result/" + recipe["result"]["id"].replace(":", "/"),
                                 recipe["group"], identity)

    def test_dirt_vertical_slabs_still_rebuild_without_grass_slabs(self):
        identity = "minecraft/dirt/vertical_slab_recombination_without_grass_slabs"
        recipe = self.recipes[identity]
        self.assertEqual(["XX"], recipe["pattern"])
        self.assertEqual("skysbuildingpieces:dirt_vertical_slab", recipe["key"]["X"])
        self.assertEqual({"id": "minecraft:dirt", "count": 1}, recipe["result"])
        guard = {"type": "forge:not", "value": {
            "type": "forge:item_exists", "item": "skysgrassslabs:dirt_slab"}}
        condition = recipe["forge:condition"]
        self.assertIn(guard, condition['values'] if condition['type'] == 'forge:and' else [condition])
        self.assertEqual(condition, self.unlocks["recipes/" + identity]["forge:condition"])

    def test_a_single_step_item_does_not_get_two_identical_rebuilding_recipes(self):
        for material in ("glass", "ice", "packed_ice", "snow", "grass", "grass_path"):
            self.assertIn("minecraft/" + material + "/recombine_horizontal_step", self.recipes)
            self.assertNotIn("minecraft/" + material + "/recombine_vertical_step", self.recipes)

    def test_distinct_step_items_and_component_preserving_rotations_remain(self):
        horizontal = self.recipes["minecraft/planks_oak/recombine_horizontal_step"]
        vertical = self.recipes["minecraft/planks_oak/recombine_vertical_step"]
        self.assertNotEqual(horizontal["key"]["X"], vertical["key"]["X"])
        rotations = [recipe for recipe in self.recipes.values() if recipe.get("preserve_tag")]
        self.assertTrue(rotations)
        self.assertTrue(all(recipe["type"] == "skysbuildingpieces:cutting" for recipe in rotations))

    def test_native_tag_objects_are_resolved_before_recipe_comparison(self):
        recipe = {"type": "minecraft:crafting_shaped", "pattern": ["X"],
                  "key": {"X": {"tag": "example:rocks"}}, "result": {"id": "example:other"}}
        tags = {"example:rocks": [{"id": "#example:nested", "required": False}],
                "example:nested": [{"id": "mineralogy:gabbro", "required": False}]}
        self.assertTrue(native_conflict(["X"], "mineralogy:gabbro", ("example:piece", 1), [recipe], tags))
        self.assertFalse(native_conflict(["X"], "mineralogy:shale", ("example:piece", 1), [recipe], tags))


if __name__ == "__main__":
    unittest.main()
