"""Recognise only checksum-bound, unused vanilla registry entries from upgrade fixtures."""

import hashlib
import json
from pathlib import Path
import re
from zipfile import ZipFile


EMPTY_CAPABILITIES = "Failed to parse saved data for 'SavedDataType[capabilities]': No key data in MapLike[{}]"
CAPABILITIES_PATHS = {"data/capabilities.dat", "DIM-1/data/capabilities.dat", "DIM1/data/capabilities.dat"}


def validate_historical_registry_proof(root, directory, profile, fixture):
    receipt = profile['forge61_registry_contract']
    raw = (root / receipt['path']).read_bytes()
    proof = json.loads(raw)
    if (hashlib.sha256(raw).hexdigest().upper() != receipt['sha256']
            or proof['source_world_sha256'] != profile['sha256']
            or proof['inspected_nbt_records'] <= 0):
        raise ValueError('The Forge 61 registry proof does not match this fixture')
    empty = proof['empty_capabilities']
    if empty:
        original, rewritten = empty['original'], empty['rewritten']
        if not original or original.keys() != rewritten.keys() or not original.keys() <= CAPABILITIES_PATHS:
            raise ValueError('The Forge 61 empty capability proof names unexpected files')
        with ZipFile(fixture) as archive:
            for name, expected in original.items():
                actual = hashlib.sha256(archive.read(name)).hexdigest().upper()
                current = hashlib.sha256((directory / 'world' / name).read_bytes()).hexdigest().upper()
                # Forge 64 saves capabilities in namespaced dimension data.
                # The original file is left alone and no parse error is expected.
                if actual != expected or current != expected:
                    raise ValueError('The historical capability file changed: ' + name)
    return {**profile, 'mappings': {}, 'empty_capabilities_count': 0}


def validate_registry_proofs(root, profile):
    """Keep registry notices tied to this fixture and its complete native audit."""
    block_proof = profile.get("native_block_rename_contract")
    if block_proof is not None:
        path = Path(root) / block_proof["path"]
        data = json.loads(path.read_text(encoding="utf-8"))
        positions = data["positions"]
        if (hashlib.sha256(path.read_bytes()).hexdigest().upper() != block_proof["sha256"]
                or data["source_world_sha256"] != profile["sha256"]
                or data["source"] != block_proof["source"]
                or data["target"] != block_proof["target"]
                or data["source"] not in profile["mappings"]["minecraft:block"]
                or data["checked_positions"] != block_proof["checked_positions"]
                or len({tuple(position) for position in positions}) != data["checked_positions"]):
            raise ValueError("The saved block rename does not match the fixture")
    for key in ("static_biome_contract", "retired_poi_contract",
                "forge47_unused_registry_contract", "forge50_unused_registry_contract"):
        receipt = profile.get(key)
        if receipt is None:
            continue
        path = Path(root) / receipt["path"]
        proof = json.loads(path.read_text(encoding="utf-8"))
        if (hashlib.sha256(path.read_bytes()).hexdigest().upper() != receipt["sha256"]
                or proof["source_world_sha256"] != profile["sha256"]):
            raise ValueError("The native registry proof does not match this fixture")
        if key == "static_biome_contract":
            biomes = profile.get("native_biome_contract")
            if (biomes is None or proof["native_biome_contract_sha256"] != biomes["sha256"]
                    or proof["checked_cells"] != biomes["checked_cells"]
                    or proof["native_renamed_cells"] != biomes["renamed_cells"]
                    or proof["snapshot_entries"] != profile["mappings"]["minecraft:worldgen/biome"]):
                raise ValueError("The static biome snapshot lacks its complete conversion proof")
        elif key in {"forge47_unused_registry_contract", "forge50_unused_registry_contract"}:
            entries = proof["snapshot_entries"]
            names = {name for registry in entries.values() for name in registry}
            if (not names or proof["inspected_nbt_values"] <= 0
                    or "minecraft:worldgen/biome" in entries
                    or proof["saved_uses"] != dict.fromkeys(names, 0)
                    or any(profile["mappings"].get(registry, {}).get(name) != numeric_id
                           for registry, values in entries.items() for name, numeric_id in values.items())):
                raise ValueError("The additional Forge registry entries are not proven unused")
        elif (proof["inspected_nbt_values"] <= 0
                or proof["saved_uses"] != {"minecraft:nitwit": 0, "minecraft:unemployed": 0}
                or proof["snapshot_entries"] != {"minecraft:point_of_interest_type":
                                                 profile["mappings"]["minecraft:point_of_interest_type"]}):
            raise ValueError("The retired POI entries are not proven unused")


def profile_for(directory):
    manifest = Path(directory) / "forward-fixture.json"
    if not manifest.is_file():
        return None
    source = json.loads(manifest.read_text(encoding="utf-8"))
    root = Path(__file__).resolve().parent.parent
    profiles = json.loads((root / "tools/native-log-profiles.json").read_text(encoding="utf-8"))["profiles"]
    profile = profiles.get(source.get("archive_sha256"))
    if profile is None:
        return None
    fixture = root / profile["fixture"]
    if (hashlib.sha256(fixture.read_bytes()).hexdigest().upper() != profile["sha256"]
            or source.get("source_jar_sha256") != profile["source_jar_sha256"]
            or profile.get("non_biome_saved_uses", profile["saved_uses"]) != 0
            or profile["inspected_nbt_records"] <= 0):
        raise ValueError("The unused-entry proof does not match the upgrade fixture")
    proof = profile.get("native_biome_contract")
    if profile["saved_uses"] and proof is None:
        raise ValueError("Used biome entries require a complete native conversion contract")
    if proof is not None:
        contract = root / proof["path"]
        data = json.loads(contract.read_text(encoding="utf-8"))
        if (hashlib.sha256(contract.read_bytes()).hexdigest().upper() != proof["sha256"]
                or data["source_world_sha256"] != profile["sha256"]
                or data["source_minecraft"] != proof["source"]
                or data["checked_cells"] != proof["checked_cells"]
                or data["native_renamed_cells"] != proof["renamed_cells"]
                or profile["saved_uses"] != proof["renamed_cells"]):
            raise ValueError("The native biome contract does not match this source fixture")
    validate_registry_proofs(root, profile)
    if 'forge61_registry_contract' in profile:
        return validate_historical_registry_proof(root, Path(directory), profile, fixture)
    receipt = profile.get("forge52_registry_contract")
    if receipt is not None:
        path = root / receipt["path"]
        raw = path.read_bytes()
        proof = json.loads(raw)
        if (hashlib.sha256(raw).hexdigest().upper() != receipt["sha256"]
                or proof["source_world_sha256"] != profile["sha256"]
                or proof["inspected_nbt_records"] <= 0
                or not proof["snapshot_entries"]):
            raise ValueError("The Forge 52 registry proof does not match this fixture")
        # Forge 52 drops a different set of static snapshot entries. Its
        # exact set has its own proof; older proofs still cover native data fixes.
        profile = {**profile, "mappings": proof["snapshot_entries"]}
    return profile


def audit_biome_proof(text, profile):
    block_proof = profile.get("native_block_rename_contract") if profile else None
    if block_proof is not None:
        marker = ("BUILDING_PIECES_NATIVE_BLOCK_RENAME_PASS source=" + block_proof["source"]
                  + " target=" + block_proof["target"] + " positions="
                  + str(block_proof["checked_positions"]))
        if not any(line.endswith(marker) for line in text.splitlines()):
            raise ValueError("The complete native block rename was not verified")
    proof = profile.get("native_biome_contract") if profile else None
    if proof is None:
        return
    marker = ("BUILDING_PIECES_NATIVE_BIOME_PASS source=" + proof["source"]
              + " cells=" + str(proof["checked_cells"]) + " renamed=" + str(proof["renamed_cells"]))
    if not any(line.endswith(marker) for line in text.splitlines()):
        raise ValueError("The complete native biome conversion was not verified")


def audit_text(text, profile=None):
    text = text.replace("\r\n", "\n")
    observed = {}
    pattern = r"Unidentified mapping from registry (minecraft:[a-z_/]+)\n(.*?)(?:\n\n)"
    for registry, body in re.findall(pattern, text, re.S):
        entries = {}
        for row in body.splitlines():
            if not row.strip():
                continue
            entry = re.fullmatch(r"\s*(minecraft:[a-z0-9_./-]+): (\d+)", row)
            if not entry or entry[1] in entries:
                raise ValueError("Malformed or repeated registry entry: " + row)
            entries[entry[1]] = int(entry[2])
        if registry in observed:
            raise ValueError("Repeated native registry notice: " + registry)
        observed[registry] = entries
    if observed and (profile is None or observed != profile["mappings"]):
        raise ValueError("Unexpected missing registry entries: " + repr(observed))
    headers = {"Unidentified mapping from registry " + registry for registry in observed}
    if observed:
        headers.add("There are unidentified mappings in this world - we are going to attempt to process anyway")
    seen = set()
    capabilities = 0
    for line in text.splitlines():
        if not re.search(r"/(?:ERROR|FATAL)\]|\[STDERR[/\]]", line):
            continue
        message = re.fullmatch(r"\[[^\r\n]+\] \[main/ERROR\]"
                               r"(?: \[net\.minecraftforge\.registries\.GameData/REGISTRIES\])?: (.+)", line)
        if message and message[1] in headers and message[1] not in seen:
            seen.add(message[1])
        elif (profile and profile.get('empty_capabilities_count', 0)
                and re.fullmatch(r'\[[^\r\n]+\] \[Server thread/ERROR\]'
                                 r'(?: \[net\.minecraft\.world\.level\.storage\.DimensionDataStorage/\])?: '
                                 + re.escape(EMPTY_CAPABILITIES), line)):
            capabilities += 1
        else:
            raise ValueError("Unexpected native failure: " + line)
    if capabilities and capabilities != profile['empty_capabilities_count']:
        raise ValueError('Unexpected number of Forge empty capability records')
    if seen != headers:
        raise ValueError("The native registry notice is incomplete")
    for problem in ("Encountered an unexpected exception", "Exception stopping the server",
                    "MissingModsException", "NoSuchMethodError", "NoClassDefFoundError",
                    "ExceptionInInitializerError", "cascading worldgen lag", "Unable to load model",
                    "Unable to load blockstate", "Unable to bake item model", "outside of the generating region",
                    "java.lang.AssertionError", "java.lang.IllegalStateException:",
                    "java.util.ConcurrentModificationException"):
        if problem in text:
            raise ValueError("Unexpected native failure: " + problem)
    return sorted(headers) + [EMPTY_CAPABILITIES] * capabilities
