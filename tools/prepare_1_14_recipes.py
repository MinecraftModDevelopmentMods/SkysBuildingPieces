"""Generate Forge 61 recipes from permanent material and shape catalogues.

The catalogue keeps source-world identities; recipes use the flattened item IDs.
Optional inputs and matching advancements share the same Forge conditions.
"""
import argparse
import copy
import json
from pathlib import Path
from zipfile import ZipFile

from prepare_1_14_materials import format_json

PATTERNS = {
    'slab': (6, ['XXX']), 'vertical_slab': (6, ['X', 'X', 'X']),
    'step': (4, ['X ', ' X']), 'soil_step': (8, ['X ', ' X']),
    'rock_step': (12, ['X  ', ' X ', '  X']), 'corner': (4, ['XX']),
    'stairs': (4, ['X  ', 'XX ', 'XXX']), 'wall': (6, ['XX', 'XX', 'XX']),
    'pane': (16, ['XXX', 'XXX']), 'rotate': (1, ['X'])
}


def item_id(spec):
    return spec if isinstance(spec, str) else spec['id']


def exists(item):
    return {'type': 'forge:item_exists', 'item': item}


def all_conditions(conditions):
    return conditions[0] if len(conditions) == 1 else {'type': 'forge:and', 'values': conditions}


def canonical_pattern(rows):
    occupied = [(column, row) for row, line in enumerate(rows)
                for column, symbol in enumerate(line) if symbol != ' ']
    if not occupied:
        raise ValueError('Empty crafting pattern')
    left = min(column for column, _ in occupied)
    top = min(row for _, row in occupied)
    width = max(column for column, _ in occupied) - left
    return tuple(sorted((column - left, row - top) for column, row in occupied)), width


def native_conflict(rows, ingredient, output, native_recipes, tags):
    def accepts(value, visited=frozenset()):
        if isinstance(value, list):
            return any(accepts(entry, visited) for entry in value)
        if isinstance(value, str):
            value = {'tag': value[1:]} if value.startswith('#') else {'item': value}
        if 'item' in value:
            return value['item'] == ingredient
        if 'tag' in value:
            tag = value['tag']
            if tag in visited:
                raise ValueError('Circular native ingredient tag: ' + tag)
            return any(accepts({'tag': entry[1:]} if entry.startswith('#') else {'item': entry},
                               visited | {tag}) for entry in tags.get(tag, []))
        return False
    pattern, width = canonical_pattern(rows)
    reflected = tuple(sorted((width - column, row) for column, row in pattern))
    for recipe in native_recipes:
        if recipe.get('type') == 'minecraft:crafting_shapeless':
            inputs = recipe['ingredients']
            if len(inputs) == len(pattern) and all(accepts(value) for value in inputs):
                result = recipe['result']
                if (result.get('id', result.get('item')), result.get('count', 1)) != output:
                    return True
            continue
        if recipe.get('type') != 'minecraft:crafting_shaped':
            continue
        other, _ = canonical_pattern(recipe['pattern'])
        if other not in (pattern, reflected):
            continue
        inputs = list(recipe['key'].values())
        if all(accepts(value) for value in inputs):
            result = recipe['result']
            if (result.get('id', result.get('item')), result.get('count', 1)) != output:
                return True
    return False


def prepare(resources, native_recipes=(), tags=None):
    tags = {} if tags is None else tags
    recipes = {}
    advancements = {}
    for module, assets in [('vanilla', 'skysbuildingpieces'),
                           ('mineralogy', 'skysbuildingpiecesmineralogy')]:
        catalogue = resources / 'assets' / assets / 'catalogue'
        materials = json.loads((catalogue / 'materials.json').read_text(encoding='utf-8'))
        palettes = json.loads((catalogue / 'palettes.json').read_text(encoding='utf-8'))
        available = {(material, palette['shape'].lower()) for palette in palettes
                     for material in palette['materials']}

        legacy_palettes = {}
        for palette in palettes:
            for slot, material in enumerate(palette['materials']):
                name = ('mineralogy_' if module == 'mineralogy' else '') + palette['id']
                shape = palette['shape'].lower()
                value = ('skysbuildingpieces:' + name, slot * palette['states'])
                legacy_palettes[(material, shape)] = value
                if shape == 'step':
                    legacy_palettes[(material, 'horizontal_step')] = value
                    legacy_palettes[(material, 'vertical_step')] = value

        def source_identity(material, kind, current):
            original = material['legacy_blocks'].get(kind)
            if original is not None:
                name = item_id(original)
                metadata = original.get('meta', 0) if isinstance(original, dict) else 0
                if module == 'mineralogy':
                    metadata = 0  # Native six-way rocks never used inventory subtypes.
            elif (material['id'], kind) in legacy_palettes:
                name, metadata = legacy_palettes[(material['id'], kind)]
            else:
                name, metadata = current, 0
            return name.replace(':', '/') + '_' + str(metadata)

        def piece(material, shape):
            blocks = material['blocks']
            if shape in blocks:
                return item_id(blocks[shape])
            stored = shape
            if (material['id'], stored) not in available and shape in ('horizontal_step', 'vertical_step'):
                stored = 'step'
            if (material['id'], stored) not in available:
                return None
            prefix = 'mineralogy_' if module == 'mineralogy' else ''
            return 'skysbuildingpieces:' + prefix + material['id'].split(':', 1)[1] + '_' + stored

        def add(path, rows, ingredient, output, count, extra=(), preserve=False):
            if ingredient is None or output is None or ingredient == output:
                return
            conditions = list(extra)
            if module == 'mineralogy':
                conditions.append({'type': 'forge:mod_loaded', 'modid': 'mineralogy'})
            for identity in sorted({ingredient, output}):
                if not identity.startswith('minecraft:'):
                    conditions.append(exists(identity))
            body = {'type': 'skysbuildingpieces:cutting' if preserve else 'minecraft:crafting_shaped',
                    'category': 'building',
                    'group': 'skysbuildingpieces/' + path.split('/')[-2],
                    'pattern': rows, 'key': {'X': ingredient},
                    'result': {'id': output, 'count': count}}
            if preserve:
                body['preserve_tag'] = True
            if conditions:
                body['forge:condition'] = all_conditions(conditions)
            if path in recipes:
                if recipes[path] != body:
                    raise ValueError('Conflicting generated recipe ID: ' + path)
                return
            recipes[path] = body
            advancement = {
                'criteria': {
                    'has_ingredient': {'trigger': 'minecraft:inventory_changed',
                                       'conditions': {'items': [{'items': [ingredient]}]}},
                    'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked',
                                       'conditions': {'recipe': 'skysbuildingpieces:' + path}}},
                'requirements': [['has_ingredient', 'has_the_recipe']],
                'rewards': {'recipes': ['skysbuildingpieces:' + path]}}
            if conditions:
                advancement['forge:condition'] = copy.deepcopy(all_conditions(conditions))
            advancements['recipes/' + path] = advancement

        for material in materials:
            if material['type'] == 'path':
                # Paths are made with a shovel, but existing pieces can still
                # be rebuilt into their larger shapes without losing material.
                base = material['id'].replace(':', '/')
                add(base + '/recombine_corner', ['X', 'X'], piece(material, 'corner'),
                    piece(material, 'horizontal_step'), 1)
                for shape in ('horizontal_step', 'vertical_step'):
                    add(base + '/recombine_' + shape, ['X', 'X'], piece(material, shape),
                        piece(material, 'slab'), 1)
                continue
            identity = material['id']
            base = identity.replace(':', '/')
            full_spec = material['blocks']['full']
            half_input = isinstance(full_spec, dict) and full_spec.get('properties', {}).get('type') == 'double'
            full = item_id(material['blocks']['slab'] if half_input else full_spec)
            slab = piece(material, 'slab')
            vertical = piece(material, 'vertical_slab')
            step = piece(material, 'horizontal_step')
            upright_step = piece(material, 'vertical_step')
            corner = piece(material, 'corner')

            def cut(shape, pattern, source, extra=()):
                source_kind = next((kind for kind, item in [
                    ('full', full), ('slab', slab), ('vertical_slab', vertical),
                    ('horizontal_step', step), ('vertical_step', upright_step)] if item == source), 'full')
                output = piece(material, shape)
                if source is None or output is None:
                    return
                count, rows = PATTERNS[pattern]
                if half_input and source == full:
                    source_kind = 'slab'
                    if pattern != 'rotate' and shape in ('stairs', 'wall', 'pane', 'slab', 'vertical_slab'):
                        count //= 2
                if native_conflict(rows, source, (output, count), native_recipes, tags):
                    if shape == 'slab' and pattern == 'slab':
                        pattern = 'step'
                        count, rows = PATTERNS[pattern]
                    elif shape == 'stairs' and pattern == 'stairs' and slab is not None:
                        source = slab
                        source_kind = 'slab'
                    else:
                        raise ValueError('No conflict-free cut: ' + identity + ' / ' + shape)
                    if native_conflict(rows, source, (output, count), native_recipes, tags):
                        raise ValueError('Fallback still conflicts: ' + identity + ' / ' + shape)
                path = base + '/' + shape + '/' + pattern + '/' + source_identity(material, source_kind, source)
                add(path, rows, source, output, count, extra, preserve=pattern == 'rotate')

            if 'slab' not in material['blocks']:
                rotate_only = identity in ('minecraft:snow', 'minecraft:stone', 'minecraft:chiseled_sandstone',
                    'minecraft:smooth_sandstone', 'minecraft:chiseled_red_sandstone',
                    'minecraft:smooth_red_sandstone', 'minecraft:stonebrick_carved',
                    'minecraft:stonebrick_cracked', 'minecraft:stonebrick_mossy')
                cut('slab', 'step' if rotate_only else 'slab', full)
            if slab is None and vertical is not None:
                cut('vertical_slab', 'vertical_slab', full)
            elif identity == 'minecraft:dirt' and vertical is not None:
                cut('vertical_slab', 'vertical_slab', full, [{'type': 'forge:not', 'value': exists(slab)}])
            if not material.get('fullBlockStepRecipe', False):
                if slab is not None:
                    cut('horizontal_step', 'step', slab)
                if vertical is not None:
                    cut('horizontal_step', 'step', vertical)
            if material.get('fullBlockStepRecipe', False):
                cut('horizontal_step', 'rock_step', full)
            elif material['type'] in ('grass', 'dirt'):
                cut('horizontal_step', 'soil_step', full)
            if step is not None:
                cut('corner', 'corner', step)
            if upright_step is not None and upright_step != step:
                cut('corner', 'corner', upright_step)
                cut('vertical_step', 'rotate', step)
                cut('horizontal_step', 'rotate', upright_step)
            for shape in ('stairs', 'wall', 'pane'):
                if shape not in material['blocks']:
                    cut(shape, shape, full)
            if slab is not None and vertical is not None:
                cut('slab', 'rotate', vertical)
            if slab is not None and not half_input:
                add(base + '/slab_recombination', ['XX'], slab, full, 1)
            if corner is not None and step is not None:
                add(base + '/recombine_corner', ['X', 'X'], corner, step, 1)
            for shape, source in [('horizontal_step', step), ('vertical_step', upright_step)]:
                output = slab if slab is not None else vertical
                add(base + '/recombine_' + shape, ['X', 'X'], source, output, 1)
                # Ordinary dirt still has its vertical slab when Grass Slabs is absent.
                if identity == 'minecraft:dirt' and slab is not None and vertical is not None:
                    add(base + '/recombine_' + shape + '_without_grass_slabs', ['X', 'X'], source,
                        vertical, 1, [{'type': 'forge:not', 'value': exists(slab)}])

    recipes['legacy_piece'] = {'type': 'skysbuildingpieces:legacy_piece'}
    return recipes, advancements


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=Path('src/main/resources'))
    parser.add_argument('--native-jar', type=Path, action='append', required=True)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    native = []
    tags = {}
    for path in args.native_jar:
        with ZipFile(path) as archive:
            for name in archive.namelist():
                if name.startswith('data/') and '/recipe/' in name and name.endswith('.json'):
                    native.append(json.loads(archive.read(name)))
                if name.startswith('data/') and '/tags/item/' in name and name.endswith('.json'):
                    namespace = name.split('/')[1]
                    identifier = namespace + ':' + name.split('/tags/item/', 1)[1][:-5]
                    data = json.loads(archive.read(name))
                    values = data['values']
                    if data.get('replace', False):
                        tags[identifier] = values
                    else:
                        tags.setdefault(identifier, []).extend(values)
    recipes, advancements = prepare(args.resources, native, tags)
    changed = 0
    manifest = args.resources.parent / 'recipe-book/native-1.14-generated.json'
    expected = sorted(folder + '/' + identifier + '.json'
                      for folder, values in [('recipe', recipes), ('advancement', advancements)]
                      for identifier in values)
    prior = json.loads(manifest.read_text(encoding='utf-8')) if manifest.is_file() else []
    # Only remove paths owned by this generator's previous manifest.
    for relative in sorted(set(prior) - set(expected)):
        path = args.resources / 'data/skysbuildingpieces' / relative
        if path.is_file():
            changed += 1
            if args.write:
                path.unlink()
    for folder, values in [('recipe', recipes), ('advancement', advancements)]:
        for identifier, data in values.items():
            path = args.resources / 'data/skysbuildingpieces' / folder / (identifier + '.json')
            text = format_json(data) + '\n'
            if not path.is_file() or path.read_text(encoding='utf-8') != text:
                changed += 1
                if args.write:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(text, encoding='utf-8', newline='\n')
    if args.write:
        manifest.write_text(format_json(expected) + '\n', encoding='utf-8', newline='\n')
    print(f'{len(recipes)} recipes and {len(advancements)} advancements; {changed} files need writing.')
    if changed and not args.write:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
