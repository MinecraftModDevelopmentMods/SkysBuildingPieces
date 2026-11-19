"""Check NeoForge completion, native settings and unexpected errors."""

import argparse
import gzip
import json
from pathlib import Path
import re
import tomllib
from native_registry_notices import audit_text, profile_for, audit_biome_proof
from native_runtime_notices import offline_client_notices, windows_counter_notice



def audit_complete_logs(directory, packaged_offline=False):
    """Include rolled first-load logs, so a clean reload cannot hide a failure."""
    paths = sorted(path for path in (Path(directory) / "logs").rglob("*")
                   if path.is_file() and (path.name.endswith(".log") or path.name.endswith(".log.gz")))
    if not paths:
        raise ValueError("Native log evidence is missing")
    for path in paths:
        if path.name.endswith(".gz"):
            with gzip.open(path, "rt", encoding="utf-8") as source:
                text = source.read()
        else:
            text = path.read_text(encoding="utf-8")
        text = windows_counter_notice(text)
        if packaged_offline:
            text, _ = offline_client_notices(text)
        try:
            audit_text(text, profile_for(directory))
        except ValueError as failure:
            raise ValueError(str(path) + ": " + str(failure)) from failure
    return len(paths)


def classify(log, unchanged_defaults, packaged_offline=False):
    """Require a complete client login without severe notices."""
    log = windows_counter_notice(log)
    notices = []
    if packaged_offline:
        log, notices = offline_client_notices(log)
    lines = log.splitlines()
    severe = [line for line in lines if re.search(r"/(?:ERROR|FATAL)\]", line)]
    startup = next((index for index, line in enumerate(lines)
                    if "Starting integrated minecraft server version 26.1.2" in line), -1)
    login = next((index for index, line in enumerate(lines) if " logged in with entity id " in line), -1)
    joined = next((index for index, line in enumerate(lines) if line.endswith(" joined the game")), -1)
    if not 0 <= startup < login <= joined:
        raise ValueError("NeoForge login did not complete normally")
    if not unchanged_defaults:
        raise ValueError("NeoForge settings changed")
    if severe or "[STDERR/" in log or "[STDERR]" in log:
        raise ValueError("Unexpected client errors: " + repr(severe[:4]))
    return {"empty_handshakes": 0, "native_config_notices": 0,
            "notices": notices, "unchanged_native_defaults": True}


def unchanged_native_settings(directory, names, no_early_display=False):
    defaults = Path(__file__).resolve().parent.parent / "src/bootstrapTest/defaultconfigs/config"
    for name in names:
        path = directory / "config" / name
        if not path.is_file():
            return False
        expected = tomllib.loads((defaults / name).read_text(encoding="utf-8"))
        actual = tomllib.loads(path.read_text(encoding="utf-8"))
        if name == "fml.toml":
            # An omitted empty override table is equivalent to the default.
            if no_early_display:
                expected["earlyWindowControl"] = False
            expected.setdefault("dependencyOverrides", {})
            actual.setdefault("dependencyOverrides", {})
        if actual != expected:
            return False
    return True


def audit(directory, phase, control=False, packaged_offline=False, no_early_display=False):
    directory = Path(directory)
    log = (directory / "logs/latest.log").read_text(encoding="utf-8")
    unchanged = unchanged_native_settings(directory, (
        "fml.toml", "neoforge-client.toml", "neoforge-common.toml", "neoforge-server.toml"), no_early_display)
    if not unchanged:
        raise ValueError("NeoForge settings differ from the controlled defaults")
    if no_early_display:
        debug = (directory / "logs/debug.log").read_text(encoding="utf-8")
        if "ImmediateWindowProvider not loading because splash screen is disabled" not in debug:
            raise ValueError("NeoForge used the early loading window")
    receipt = classify(log, unchanged, packaged_offline)
    required = (("NEOFORGE_CLIENT_CONTROL_PASS joined=true sky_mods=false",) if control else
                ("BUILDING_PIECES_CLIENT_WORLD_ACCEPTED", "SKY_RECIPE_PREVIEW_MODELS_PASS",
                 "BUILDING_PIECES_CONTINUOUS_GLASS_PASS", "BUILDING_PIECES_COLOURS_PASS",
                 "BUILDING_PIECES_CLIENT_DISCONNECT_PASS", "Stopping server", "Saving worlds"))
    if any(marker not in log for marker in required):
        raise ValueError("Native client completion checks are missing")
    if not control:
        for dimension in ("overworld", "the_nether", "the_end"):
            if not re.search(r"(?m)Saving chunks for level '[^\r\n]+'/minecraft:" + dimension + r"$", log):
                raise ValueError("The integrated client did not save " + dimension)
    if control and receipt["empty_handshakes"] != 0:
        raise ValueError("The NeoForge-only control must complete without handshake errors")
    if list((directory / "crash-reports").glob("*")):
        raise ValueError("Native client produced a crash report")
    receipt["complete_log_files"] = audit_complete_logs(directory, packaged_offline)
    receipt["phase"] = phase
    receipt["early_display"] = not no_early_display
    receipt["control_without_sky_mods"] = control
    (directory / (phase + "-log-audit.json")).write_text(
        json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    return receipt


def classify_server(log, debug, unchanged_defaults, profile=None):
    """Require unchanged defaults and a complete native server start."""
    lines = log.splitlines()
    startup = any('Preparing level ' in line or 'Preparing start region for dimension ' in line
                  for line in lines)
    if not unchanged_defaults or not startup or 'Done (' not in log:
        raise ValueError('Native server defaults or startup sequence changed')
    checked = windows_counter_notice(log)
    notices = audit_text(checked, profile)
    audit_biome_proof(checked, profile)
    if not debug.strip():
        raise ValueError('Native server debug evidence is missing')
    return {'native_server_config_notices': 0, 'notices': notices,
            'unchanged_native_defaults': True, 'debug_identity_verified': True}

def audit_server(directory, phase):
    directory = Path(directory)
    unchanged = unchanged_native_settings(directory, ("fml.toml", "neoforge-common.toml", "neoforge-server.toml"))
    receipt = classify_server((directory / 'logs/latest.log').read_text(encoding='utf-8'),
                              (directory / 'logs/debug.log').read_text(encoding='utf-8'), unchanged, profile_for(directory))
    if list((directory / 'crash-reports').glob('*')):
        raise ValueError('Native server produced a crash report')
    receipt['complete_log_files'] = audit_complete_logs(directory)
    receipt['phase'] = phase
    (directory / (phase + '-server-log-audit.json')).write_text(
        json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    return receipt


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--control", action="store_true")
    parser.add_argument("--server", action="store_true")
    options = parser.parse_args()
    result = (audit_server(options.directory, options.phase) if options.server else
              audit(options.directory, options.phase, options.control))
    print("NEOFORGE_NATIVE_LOG_AUDIT_PASS", json.dumps(result, sort_keys=True))
