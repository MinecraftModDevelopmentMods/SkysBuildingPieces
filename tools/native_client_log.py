"""Check Forge 28 client completion and its two control-proven log messages."""

import argparse
import json
from pathlib import Path
import re
import tomllib


EMPTY_PAYLOAD = re.compile(
    r".*\[(?:Client|Server) thread/ERROR\] "
    r"\[(?:net\.minecraftforge\.fml\.network\.simple\.)?IndexedMessageCodec/SIMPLENET\]: "
    r"Received empty payload on channel fml:handshake")
CONFIG_NOTICE = re.compile(
    r".*\[Thread-\d+/FATAL\] \[(?:net\.minecraftforge\.common\.)?ForgeConfig/CORE\]: "
    r"Forge config just got changed on the file system!")


def classify(log, unchanged_defaults):
    """Keep the native notices visible; reject other severe messages."""
    lines = log.splitlines()
    severe = [line for line in lines if re.search(r"/(?:ERROR|FATAL)\]", line)]
    handshake = [line for line in severe if EMPTY_PAYLOAD.fullmatch(line)]
    # ForgeConfig.onFileChange logs FATAL on ordinary config reloads. The
    # no-Sky control reproduces this first-save watcher race. Only startup
    # notices with unchanged native settings qualify for this classification.
    notices = [line for line in severe if CONFIG_NOTICE.fullmatch(line)]
    startup = next((index for index, line in enumerate(lines)
                    if "Preparing start region for dimension " in line), -1)
    if len(handshake) > 2 or "Connected to a modded server." not in log:
        raise ValueError("Native Forge login did not complete normally")
    if notices and (not unchanged_defaults or len(notices) > 2 or startup < 0
                    or any(index >= startup for index, line in enumerate(lines)
                           if CONFIG_NOTICE.fullmatch(line))):
        raise ValueError("Forge configuration changed outside the controlled startup")
    unexpected = [line for line in severe if line not in handshake and line not in notices]
    if unexpected or "[STDERR/" in log or "[STDERR]" in log:
        raise ValueError("Unexpected client errors: " + repr(unexpected[:4]))
    return {"empty_handshakes": len(handshake), "native_config_notices": len(notices),
            "notices": notices, "unchanged_native_defaults": unchanged_defaults}


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
    required = (("FORGE28_CLIENT_CONTROL_PASS joined=true sky_mods=false",) if control else
                ("BUILDING_PIECES_CLIENT_WORLD_ACCEPTED", "SKY_RECIPE_PREVIEW_MODELS_PASS",
                 "BUILDING_PIECES_CONTINUOUS_GLASS_PASS", "BUILDING_PIECES_COLOURS_PASS"))
    if any(marker not in log for marker in required):
        raise ValueError("Native client completion checks are missing")
    if control and receipt["empty_handshakes"] != 2:
        raise ValueError("The Forge-only control did not reproduce the exact handshake pair")
    if list((directory / "crash-reports").glob("*")):
        raise ValueError("Native client produced a crash report")
    receipt["phase"] = phase
    receipt["control_without_sky_mods"] = control
    (directory / (phase + "-log-audit.json")).write_text(
        json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    return receipt


def classify_server(log, debug, unchanged_defaults):
    """Recognise only the native server-config self-save observed by the Forge-only control."""
    lines = log.splitlines()
    debug_lines = debug.splitlines()
    severe = [line for line in lines if re.search(r"/(?:ERROR|FATAL)\]", line)]
    notices = [line for line in severe if CONFIG_NOTICE.fullmatch(line)]
    startup = next((index for index, line in enumerate(lines)
                    if 'Preparing level ' in line or 'Preparing start region for dimension ' in line), -1)
    if not unchanged_defaults or len(notices) > 1 or startup < 0:
        raise ValueError('Native server defaults or startup sequence changed')
    if any(line not in notices for line in severe) or '[STDERR/' in log or '[STDERR]' in log:
        raise ValueError('Unexpected server error')
    for notice in notices:
        if lines.index(notice) >= startup or notice not in debug_lines:
            raise ValueError('Server configuration notice occurred outside controlled startup')
        index = debug_lines.index(notice)
        thread = re.search(r'\[(Thread-\d+)/FATAL\]', notice).group(1)
        expected = re.compile(
            r'.*\[' + re.escape(thread) + r'/DEBUG\] '
            r'\[(?:net\.minecraftforge\.fml\.config\.)?ConfigFileTypeHandler/CONFIG\]: '
            r'Config file forge-server\.toml changed, sending notifies')
        if index == 0 or not expected.fullmatch(debug_lines[index - 1]):
            raise ValueError('Server notice lacks the exact native Forge config debug entry')
    return {'native_server_config_notices': len(notices), 'notices': notices,
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
    print("FORGE28_NATIVE_CLIENT_AUDIT_PASS", json.dumps(result, sort_keys=True))
