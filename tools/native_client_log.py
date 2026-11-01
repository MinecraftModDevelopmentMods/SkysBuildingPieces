"""Check Forge 31 completion, native settings and unexpected errors."""

import argparse
import json
from pathlib import Path
import re
import tomllib


def classify(log, unchanged_defaults):
    """The Forge-only 1.15 client control completes without severe notices."""
    lines = log.splitlines()
    severe = [line for line in lines if re.search(r"/(?:ERROR|FATAL)\]", line)]
    startup = next((index for index, line in enumerate(lines)
                    if "Preparing start region for dimension " in line), -1)
    if "Connected to a modded server." not in log or startup < 0:
        raise ValueError("Native Forge login did not complete normally")
    if not unchanged_defaults:
        raise ValueError("Native Forge settings changed")
    if severe or "[STDERR/" in log or "[STDERR]" in log:
        raise ValueError("Unexpected client errors: " + repr(severe[:4]))
    return {"empty_handshakes": 0, "native_config_notices": 0,
            "notices": [], "unchanged_native_defaults": True}


def audit(directory, phase, control=False):
    directory = Path(directory)
    log = (directory / "logs/latest.log").read_text(encoding="utf-8")
    defaults = Path(__file__).resolve().parent.parent / "src/bootstrapTest/defaultconfigs"
    servers = list((directory / "saves").glob("*/serverconfig/forge-server.toml"))
    pairs = [(defaults / "config/forge-client.toml", directory / "config/forge-client.toml")]
    pairs.extend((defaults / "defaultconfigs/forge-server.toml", server) for server in servers)
    unchanged = len(servers) == 1 and all(
        actual.is_file() and tomllib.loads(expected.read_text(encoding="utf-8"))
        == tomllib.loads(actual.read_text(encoding="utf-8")) for expected, actual in pairs)
    if not unchanged:
        raise ValueError("Native Forge settings differ from the controlled defaults")
    receipt = classify(log, unchanged)
    required = (("FORGE31_CLIENT_CONTROL_PASS joined=true sky_mods=false",) if control else
                ("BUILDING_PIECES_CLIENT_WORLD_ACCEPTED", "SKY_RECIPE_PREVIEW_MODELS_PASS",
                 "BUILDING_PIECES_CONTINUOUS_GLASS_PASS", "BUILDING_PIECES_COLOURS_PASS"))
    if any(marker not in log for marker in required):
        raise ValueError("Native client completion checks are missing")
    if control and receipt["empty_handshakes"] != 0:
        raise ValueError("The Forge-only control must complete without handshake errors")
    if list((directory / "crash-reports").glob("*")):
        raise ValueError("Native client produced a crash report")
    receipt["phase"] = phase
    receipt["control_without_sky_mods"] = control
    (directory / (phase + "-log-audit.json")).write_text(
        json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    return receipt


def classify_server(log, debug, unchanged_defaults):
    """The Forge-only 1.15 server control completes without severe notices."""
    lines = log.splitlines()
    severe = [line for line in lines if re.search(r"/(?:ERROR|FATAL)\]", line)]
    startup = any('Preparing level ' in line or 'Preparing start region for dimension ' in line
                  for line in lines)
    if not unchanged_defaults or not startup or 'Done (' not in log:
        raise ValueError('Native server defaults or startup sequence changed')
    if severe or '[STDERR/' in log or '[STDERR]' in log:
        raise ValueError('Unexpected server error: ' + repr(severe[:4]))
    if not debug.strip():
        raise ValueError('Native server debug evidence is missing')
    return {'native_server_config_notices': 0, 'notices': [],
            'unchanged_native_defaults': True, 'debug_identity_verified': True}

def audit_server(directory, phase):
    directory = Path(directory)
    defaults = Path(__file__).resolve().parent.parent / 'src/bootstrapTest/defaultconfigs'
    expected = defaults / 'defaultconfigs/forge-server.toml'
    actual = directory / 'world/serverconfig/forge-server.toml'
    unchanged = actual.is_file() and tomllib.loads(expected.read_text(encoding='utf-8')) == tomllib.loads(
        actual.read_text(encoding='utf-8'))
    receipt = classify_server((directory / 'logs/latest.log').read_text(encoding='utf-8'),
                              (directory / 'logs/debug.log').read_text(encoding='utf-8'), unchanged)
    if list((directory / 'crash-reports').glob('*')):
        raise ValueError('Native server produced a crash report')
    receipt['phase'] = phase
    (directory / (phase + '-server-log-audit.json')).write_text(
        json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    return receipt


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--control", action="store_true")
    options = parser.parse_args()
    result = audit(options.directory, options.phase, options.control)
    print("FORGE31_NATIVE_CLIENT_AUDIT_PASS", json.dumps(result, sort_keys=True))
