"""Prepare native blockstate selectors, flat item models and language files."""

import argparse
import copy
import json
from pathlib import Path

from prepare_1_14_materials import format_json


def identifier(value, default_namespace):
    return value if ':' in value else default_namespace + ':' + value


def prepare(resources):
    assets = resources / 'assets'
    palettes = {}
    item_models = {}
    for module, namespace in [('vanilla', 'skysbuildingpieces'),
                              ('mineralogy', 'skysbuildingpiecesmineralogy')]:
        root = assets / namespace
        definitions = json.loads((root / 'catalogue/palettes.json').read_text(encoding='utf-8'))
        for palette in definitions:
            block_name = ('mineralogy_' if module == 'mineralogy' else '') + palette['id']
            palettes[('skysbuildingpieces', block_name)] = palette
            first_model = None
            for material in palette['materials']:
                material_path = material.split(':', 1)[1]
                old_item_name = material_path + '_' + palette['shape'].lower()
                flat_name = ('mineralogy_' if module == 'mineralogy' else '') + old_item_name
                old_model = root / 'models/item' / (old_item_name + '.json')
                if not old_model.is_file():
                    raise ValueError('Missing original item model: ' + str(old_model))
                item_models[assets / 'skysbuildingpieces/models/item' / (flat_name + '.json')] = \
                    json.loads(old_model.read_text(encoding='utf-8'))
                first_model = first_model or flat_name
            # The old palette item remains a hidden carrier for saved stacks.
            item_models[assets / 'skysbuildingpieces/models/item' / (block_name + '.json')] = {
                'parent': 'skysbuildingpieces:item/' + first_model}

    changes = dict(item_models)
    for path in assets.glob('*/blockstates/*.json'):
        data = json.loads(path.read_text(encoding='utf-8'))
        palette = palettes.get((path.parent.parent.name, path.stem))
        variants = {}
        for selector, original in data['variants'].items():
            if selector == 'inventory':
                continue
            conditions = {} if selector in ('', 'normal') else dict(
                part.split('=', 1) for part in selector.split(','))
            if palette and int(conditions.get('meta', 0)) >= len(palette['materials']) * {
                    'slab': 2, 'vertical_slab': 4, 'horizontal_step': 8, 'vertical_step': 4,
                    'step': 12, 'corner': 8, 'stairs': 8, 'wall': 1, 'pane': 1}[palette['shape'].lower()]:
                continue
            model = copy.deepcopy(original)
            for alternative in model if isinstance(model, list) else [model]:
                namespace, model_path = identifier(alternative['model'], 'minecraft').split(':', 1)
                if not model_path.startswith(('block/', 'item/')):
                    model_path = 'block/' + model_path
                alternative['model'] = namespace + ':' + model_path
            for waterlogged in ('false', 'true') if palette else (None,):
                native = dict(conditions)
                if palette and palette['shape'].lower() in ('wall', 'pane') and len(palette['materials']) == 1:
                    native.pop('meta', None)
                if waterlogged is not None:
                    native['waterlogged'] = waterlogged
                key = ','.join(name + '=' + native[name] for name in sorted(native))
                variants[key] = model
        changes[path] = {'variants': variants}

    languages = []
    for path in assets.glob('*/lang/*.lang'):
        translations = {}
        for line in path.read_text(encoding='utf-8').splitlines():
            if not line.strip() or line.lstrip().startswith('#'):
                continue
            key, value = line.split('=', 1)
            key = key.strip()
            if key.endswith('.name'):
                key = key[:-5]
            translations[key] = value
        destination = path.with_name(path.stem.lower() + '.json')
        changes[destination] = translations
        languages.append(path)

    for path, data in changes.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(format_json(data) + '\n', encoding='utf-8', newline='\n')
    for path in languages:
        path.unlink()
    print(f'Prepared {len(changes)} state/model/language files; converted {len(languages)} language files.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=Path('src/main/resources'))
    prepare(parser.parse_args().resources)
