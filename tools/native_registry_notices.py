"""Recognise only checksum-bound, unused vanilla registry entries from upgrade fixtures."""

import hashlib
import json
from pathlib import Path
import re


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
            or profile["saved_uses"] != 0 or profile["inspected_nbt_records"] <= 0):
        raise ValueError("The unused-entry proof does not match the upgrade fixture")
    return profile


def audit_text(text, profile=None):
    text = text.replace("\r\n", "\n")
    observed = {}
    pattern = r"Unidentified mapping from registry (minecraft:[a-z_]+)\n(.*?)(?:\n\n)"
    for registry, body in re.findall(pattern, text, re.S):
        entries = {}
        for row in body.splitlines():
            if not row.strip():
                continue
            entry = re.fullmatch(r"\s*(minecraft:[a-z_.]+): (\d+)", row)
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
    for line in text.splitlines():
        if not re.search(r"/(?:ERROR|FATAL)\]|\[STDERR[/\]]", line):
            continue
        message = re.fullmatch(r"\[[^\r\n]+\] \[main/ERROR\]"
                               r"(?: \[net\.minecraftforge\.registries\.GameData/REGISTRIES\])?: (.+)", line)
        if message and message[1] in headers and message[1] not in seen:
            seen.add(message[1])
        else:
            raise ValueError("Unexpected native failure: " + line)
    if seen != headers:
        raise ValueError("The native registry notice is incomplete")
    for problem in ("Encountered an unexpected exception", "Exception stopping the server",
                    "MissingModsException", "NoSuchMethodError", "NoClassDefFoundError",
                    "ExceptionInInitializerError", "cascading worldgen lag", "Unable to load model",
                    "Unable to load blockstate", "outside of the generating region",
                    "java.lang.AssertionError", "java.lang.IllegalStateException:"):
        if problem in text:
            raise ValueError("Unexpected native failure: " + problem)
    return sorted(headers)
