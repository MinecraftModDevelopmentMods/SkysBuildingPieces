"""Resolve the fixed catalogue against the target's own states and models.

This is a development tool. It reads local, verified Minecraft and Mineralogy
inputs; it does not download assets or copy their textures into the mod.
"""

import argparse
import copy
import json
from pathlib import Path
import re
from zipfile import ZipFile


def read_json(archive, name):
    return json.loads(archive.read(name))


def format_json(value, depth=0):
    """Keep coordinates and individual faces readable without expanding every number."""
    if isinstance(value, list):
        if not any(isinstance(item, (dict, list)) for item in value):
            return json.dumps(value, ensure_ascii=False)
        indent = '  ' * (depth + 1)
        return '[\n' + ',\n'.join(indent + format_json(item, depth + 1) for item in value) \
            + '\n' + '  ' * depth + ']'
    if isinstance(value, dict):
        if not value or 'texture' in value and all(key in (
                'uv', 'texture', 'cullface', 'tintindex', 'rotation') for key in value):
            return json.dumps(value, ensure_ascii=False)
        indent = '  ' * (depth + 1)
        return '{\n' + ',\n'.join(indent + json.dumps(key) + ': ' + format_json(item, depth + 1)
                                 for key, item in value.items()) + '\n' + '  ' * depth + '}'
    return json.dumps(value, ensure_ascii=False)


def flattening_table(sources):
    text = sources.read('net/minecraft/util/datafix/fixes/BlockStateFlatteningMap.java').decode()
    names = {}
    states = {}
    for match in re.finditer(r'addEntry\((\d+), "([^"]+)"(.*?)\);', text):
        numeric = int(match[1])
        fixed = re.sub(r'([{,])([A-Za-z_][A-Za-z_0-9]*):', r'\1"\2":', match[2])
        states[numeric] = json.loads(fixed.replace("'", '"'))
        for old_name in re.findall(r"Name:'([^']+)'", match[3]):
            previous = names.setdefault(old_name, numeric >> 4)
            if previous != numeric >> 4:
                raise ValueError('Ambiguous old vanilla block: ' + old_name)
    if len(states) < 1000:
        raise ValueError('The expected native flattening table was not found')
    return names, states


class NativeAssets:
    def __init__(self, minecraft, mineralogy):
        self.archives = {'minecraft': minecraft, 'mineralogy': mineralogy}
        self.models = {}

    def archive_path(self, identifier, directory):
        namespace, path = identifier.split(':', 1)
        return self.archives[namespace], f'assets/{namespace}/{directory}/{path}.json'

    def exists(self, identifier):
        archive, path = self.archive_path(identifier, 'blockstates')
        return path in archive.namelist()

    def model(self, identifier):
        if ':' not in identifier:
            identifier = 'minecraft:' + identifier
        if identifier in self.models:
            return self.models[identifier]
        archive, path = self.archive_path(identifier, 'models')
        data = read_json(archive, path)
        parent = self.model(data['parent']) if 'parent' in data else {}
        combined = {**parent, **data}
        combined['textures'] = {**parent.get('textures', {}), **data.get('textures', {})}
        self.models[identifier] = combined
        return combined

    def face_textures(self, state):
        archive, path = self.archive_path(state['id'], 'blockstates')
        variants = read_json(archive, path)['variants']
        properties = state.get('properties', {})
        matches = []
        for conditions, model in variants.items():
            choices = dict(part.split('=', 1) for part in conditions.split(',') if part)
            if all(properties.get(key, value) == value for key, value in choices.items()):
                matches.append(model)
        if not matches:
            raise ValueError('No native model for ' + repr(state))
        model = matches[0][0] if isinstance(matches[0], list) else matches[0]
        data = self.model(model['model'])

        def resolve(texture):
            seen = set()
            while texture.startswith('#'):
                if texture in seen:
                    raise ValueError('Circular texture reference: ' + texture)
                seen.add(texture)
                texture = data['textures'][texture[1:]]
            return texture if ':' in texture else 'minecraft:' + texture

        faces = {}
        for element in data.get('elements', []):
            for direction, face in element['faces'].items():
                faces.setdefault(direction, resolve(face['texture']))
        faces['particle'] = resolve(data['textures'].get('particle', '#all'))
        return faces


def as_spec(fixed):
    result = {'id': fixed['Name']}
    if fixed.get('Properties'):
        result['properties'] = fixed['Properties']
    return result


def prepare(args):
    with ZipFile(args.mapping_sources) as sources, ZipFile(args.minecraft_client) as minecraft, \
            ZipFile(args.mineralogy) as mineralogy:
        old_ids, flattened = flattening_table(sources)
        assets = NativeAssets(minecraft, mineralogy)
        changes = {}
        texture_names = {
            'minecraft:blocks/grass_side_overlay': 'minecraft:block/grass_block_side_overlay',
            'minecraft:blocks/grass_side_snowed': 'minecraft:block/grass_block_snow',
        }

        for namespace in ('skysbuildingpieces', 'skysbuildingpiecesmineralogy'):
            path = args.resources / 'assets' / namespace / 'catalogue/materials.json'
            materials = json.loads(path.read_text(encoding='utf-8'))
            for material in materials:
                legacy = material.setdefault('legacy_blocks', copy.deepcopy(material['blocks']))
                legacy_textures = material.setdefault('legacy_textures', copy.deepcopy(material['textures']))
                blocks = {}
                for shape, original in legacy.items():
                    spec = {'id': original} if isinstance(original, str) else original
                    identifier = spec['id']
                    if identifier.startswith('buildingbricks:') and shape == 'slab':
                        blocks[shape] = {'id': 'skysgrassslabs:grass_slab', 'optional': True}
                    elif identifier.startswith('minecraft:'):
                        numeric = (old_ids[identifier] << 4) | spec.get('meta', 0)
                        if numeric not in flattened:
                            raise ValueError('No native state for ' + repr(original))
                        blocks[shape] = as_spec(flattened[numeric])
                    elif identifier.startswith('mineralogy:'):
                        blocks[shape] = {'id': identifier}
                        if shape == 'slab':
                            blocks[shape]['properties'] = {'facing': 'up'}
                            blocks[shape]['optional'] = True
                        if not assets.exists(identifier):
                            raise ValueError('The native rock is missing: ' + identifier)
                    else:
                        raise ValueError('An explicit native mapping is required: ' + identifier)

                # FlatteningMap describes 1.13 names. Vanilla's later schema 1802
                # renames that old stone slab before 1.14 adds its new stone slab.
                for state in blocks.values():
                    if state['id'] == 'minecraft:stone_slab':
                        state['id'] = 'minecraft:smooth_stone_slab'
                if material['id'] == 'minecraft:stone_slab':
                    blocks['full'] = {'id': 'minecraft:smooth_stone'}
                full = blocks['full']['id']
                # New vanilla shapes can replace normal crafting outputs while
                # the old palette blocks remain registered for saved worlds.
                if full.startswith('minecraft:') and material['type'] not in ('dirt', 'grass', 'path'):
                    base = full.split(':', 1)[1]
                    base = base.removesuffix('_planks')
                    singular = {'bricks': 'brick', 'stone_bricks': 'stone_brick',
                                'mossy_stone_bricks': 'mossy_stone_brick',
                                'end_stone_bricks': 'end_stone_brick',
                                'nether_bricks': 'nether_brick',
                                'red_nether_bricks': 'red_nether_brick'}.get(base, base)
                    for shape in ('slab', 'stairs', 'wall', 'fence', 'fence_gate'):
                        candidate = 'minecraft:' + singular + '_' + shape
                        if assets.exists(candidate):
                            blocks[shape] = {'id': candidate}
                if material['id'] in ('minecraft:dirt', 'minecraft:grass', 'minecraft:grass_path'):
                    path_name = {'minecraft:dirt': 'dirt_slab', 'minecraft:grass': 'grass_slab',
                                 'minecraft:grass_path': 'path_slab'}[material['id']]
                    blocks['slab'] = {'id': 'skysgrassslabs:' + path_name, 'optional': True}
                material['blocks'] = blocks

                texture_state = blocks['slab'] if material['id'] == 'minecraft:stone_slab' else blocks['full']
                native_faces = assets.face_textures(texture_state)
                old_faces = {'all': legacy_textures} if isinstance(legacy_textures, str) else legacy_textures
                textures = {}
                for role, old_texture in old_faces.items():
                    face = {'top': 'up', 'bottom': 'down', 'side': 'north', 'all': 'particle'}.get(role)
                    candidate = old_texture.replace('minecraft:blocks/', 'minecraft:block/')
                    resource = 'assets/minecraft/textures/' + candidate.split(':', 1)[1] + '.png'
                    if old_texture.startswith('minecraft:blocks/') and resource in minecraft.namelist():
                        texture = candidate
                    else:
                        texture = texture_names.get(old_texture) if face is None else native_faces[face]
                    if texture is None:
                        raise ValueError('Unknown texture role: ' + role)
                    if old_texture in texture_names and texture_names[old_texture] != texture:
                        raise ValueError('Conflicting texture mapping for ' + old_texture)
                    texture_names[old_texture] = texture
                    textures[role] = texture
                material['textures'] = textures['all'] if 'all' in textures else textures
            changes[path] = format_json(materials) + '\n'

        # A texture referenced outside the catalogue must also resolve to a real
        # file in the target. Reject unknown references before changing any files.
        for path in (args.resources / 'assets').rglob('models/**/*.json'):
            data = json.loads(path.read_text(encoding='utf-8'))
            for key, texture in data.get('textures', {}).items():
                if texture.startswith('minecraft:blocks/'):
                    if texture not in texture_names:
                        candidate = texture.replace(':blocks/', ':block/')
                        resource = 'assets/minecraft/textures/' + candidate.split(':', 1)[1] + '.png'
                        if resource not in minecraft.namelist():
                            raise ValueError('Unresolved model texture ' + texture + ' in ' + str(path))
                        texture_names[texture] = candidate
                    data['textures'][key] = texture_names[texture]
            for element in data.get('elements', []):
                for direction, face in element['faces'].items():
                    axis, edge, boundary = {'down': (1, 'from', 0), 'up': (1, 'to', 16),
                                           'north': (2, 'from', 0), 'south': (2, 'to', 16),
                                           'west': (0, 'from', 0), 'east': (0, 'to', 16)}[direction]
                    if element[edge][axis] != boundary:
                        face.pop('cullface', None)
            changes[path] = format_json(data) + '\n'

        if args.write:
            for path, content in changes.items():
                if not path.is_file() or path.read_text(encoding='utf-8') != content:
                    path.write_text(content, encoding='utf-8', newline='\n')
        print(f'Validated {len(changes)} catalogue/model files; write={args.write}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mapping-sources', type=Path, required=True)
    parser.add_argument('--minecraft-client', type=Path, required=True)
    parser.add_argument('--mineralogy', type=Path, required=True)
    parser.add_argument('--resources', type=Path, default=Path('src/main/resources'))
    parser.add_argument('--write', action='store_true')
    prepare(parser.parse_args())
