"""Run a disposable native Forge profile with exact, already-built artifacts."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
from native_client_log import audit as audit_client


def digest(path, algorithm="sha256"):
    return hashlib.new(algorithm, path.read_bytes()).hexdigest().upper()


def allowed(rules):
    if not rules:
        return True
    result = False
    for rule in rules:
        operating_system = rule.get("os", {})
        if rule.get("features"):
            continue
        if operating_system.get("name", "windows") != "windows":
            continue
        if operating_system.get("arch", "x86_64") not in ("x86_64", "amd64"):
            continue
        if "version" in operating_system and not re.search(operating_system["version"], "10.0"):
            continue
        result = rule["action"] == "allow"
    return result


def manifest_classpath(destination, files):
    # Manifest URLs avoid Windows' command-line length limit and quote spaces safely.
    value = "Class-Path: " + " ".join(path.resolve().as_uri() for path in files)
    lines = ["Manifest-Version: 1.0"]
    while len(value.encode("utf-8")) > 70:
        lines.append(value[:70])
        value = " " + value[70:]
    lines.extend((value, "", ""))
    with zipfile.ZipFile(destination, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", "\r\n".join(lines))


def client_command(options, directory):
    runtime = options.runtime.resolve()
    base = json.loads((runtime / "versions/1.14.4/1.14.4.json").read_text(encoding="utf-8"))
    forge = json.loads((runtime / "versions/1.14.4-forge-28.2.26/1.14.4-forge-28.2.26.json")
                       .read_text(encoding="utf-8"))
    if base["id"] != "1.14.4" or forge["inheritsFrom"] != "1.14.4":
        raise ValueError("This launcher requires the verified Forge 28 client metadata")
    libraries = {}
    for layer in (base, forge):
        for library in layer["libraries"]:
            if allowed(library.get("rules")):
                libraries[":".join(library["name"].split(":")[:2])] = library
    classpath = []
    for library in libraries.values():
        artifact = library["downloads"]["artifact"]
        path = runtime / "libraries" / artifact["path"]
        if digest(path, "sha1") != artifact["sha1"].upper():
            raise ValueError("Client library checksum changed: " + library["name"])
        classpath.append(path)
    client = runtime / "versions/1.14.4/1.14.4.jar"
    if digest(client, "sha1") != base["downloads"]["client"]["sha1"].upper():
        raise ValueError("Official client checksum changed")
    classpath.append(client)
    classpath_jar = directory / "launch-classpath.jar"
    manifest_classpath(classpath_jar, classpath)
    replacements = {
        "auth_player_name": "BuildingPiecesChecks", "version_name": forge["id"],
        "game_directory": str(directory), "assets_root": str(options.assets.resolve()),
        "assets_index_name": base["assetIndex"]["id"], "auth_uuid": "1" * 32,
        "auth_access_token": "0", "user_type": "mojang", "version_type": "release",
    }
    arguments = []
    for layer in (base, forge):
        for argument in layer["arguments"]["game"]:
            if isinstance(argument, dict):
                if not allowed(argument.get("rules")):
                    continue
                argument = argument["value"]
            for value in argument if isinstance(argument, list) else [argument]:
                for key, replacement in replacements.items():
                    value = value.replace("${" + key + "}", replacement)
                if "${" in value:
                    raise ValueError("Unresolved native launch argument: " + value)
                arguments.append(value)
    natives = runtime / "natives/forge-28.2.26"
    if not natives.is_dir() or not (options.assets / "indexes/1.14.json").is_file():
        raise ValueError("Client natives or assets are incomplete")
    return [str(options.java), "-Xmx3g", "-Djava.library.path=" + str(natives),
            "-Dskysbuildingpieces.bootstrapPhase=" + options.phase,
            "-cp", str(classpath_jar), forge["mainClass"], *arguments]


def prepare(options, directory):
    if directory.exists():
        raise ValueError("Fresh checks require a new disposable directory")
    directory.mkdir(parents=True)
    mods = directory / "mods"
    mods.mkdir()
    receipts = []
    for source in (options.mod, options.probe, *options.extra_mod):
        if not source.is_file():
            raise ValueError("Missing exact runtime artifact: " + str(source))
        target = mods / source.name
        if target.exists():
            raise ValueError("Duplicate runtime artifact name: " + source.name)
        shutil.copyfile(source, target)
        receipts.append({"name": source.name, "sha256": digest(source)})
    (directory / "artifact-inputs.json").write_text(json.dumps(receipts, indent=2) + "\n", encoding="utf-8")
    (directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    for name in ("banned-players.json", "banned-ips.json", "ops.json", "whitelist.json"):
        (directory / name).write_text("[]\n", encoding="utf-8")
    (directory / "server.properties").write_text(
        "level-name=world\nlevel-seed=4815162342\nonline-mode=false\nserver-ip=127.0.0.1\n"
        "server-port=0\ngenerate-structures=false\nspawn-animals=false\nspawn-monsters=false\n"
        "max-tick-time=120000\n", encoding="utf-8")
    (directory / "options.txt").write_text("lang:en_us\nfullscreen:false\nrenderDistance:2\n", encoding="utf-8")
    shutil.copytree("src/bootstrapTest/defaultconfigs", directory, dirs_exist_ok=True)
    if options.fixture:
        with zipfile.ZipFile(options.fixture) as archive:
            for name in archive.namelist():
                target = (directory / "world" / name).resolve()
                if not target.is_relative_to(directory / "world"):
                    raise ValueError("Unsafe fixture archive path: " + name)
            archive.extractall(directory / "world")
        shutil.copyfile(options.fixture.with_suffix(".json"), directory / "forward-fixture.json")


def audit(directory, phase, mode):
    if mode == "client":
        audit_client(directory, phase)
        shutil.copyfile(directory / "logs/latest.log", directory / (phase + "-complete.log"))
        return
    log = (directory / "logs/latest.log").read_text(encoding="utf-8")
    errors = [line for line in log.splitlines() if re.search(r"/(?:ERROR|FATAL)\]", line)]
    required = ("Done (", "BUILDING_PIECES_NATIVE_BOOTSTRAP_PASS " + phase)
    if errors or "[STDERR/" in log or "[STDERR]" in log or any(marker not in log for marker in required):
        raise ValueError("Packaged runtime failed its log or completion checks: " + repr(errors[:4]))
    if list((directory / "crash-reports").glob("*")):
        raise ValueError("Packaged runtime produced a crash report")
    shutil.copyfile(directory / "logs/latest.log", directory / (phase + "-complete.log"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("java", "runtime", "directory", "mod", "probe"):
        parser.add_argument("--" + option, type=Path, required=True)
    parser.add_argument("--mode", choices=("server", "client"), required=True)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--assets", type=Path)
    parser.add_argument("--fixture", type=Path)
    parser.add_argument("--extra-mod", type=Path, action="append", default=[])
    parser.add_argument("--fresh", action="store_true")
    options = parser.parse_args()
    directory = options.directory.resolve()
    if not directory.is_relative_to(Path("build").resolve()) or directory == Path("build").resolve():
        raise ValueError("Packaged checks must stay inside this checkout's build directory")
    if options.fresh:
        prepare(options, directory)
    else:
        expected = json.loads((directory / "artifact-inputs.json").read_text(encoding="utf-8"))
        actual = [{"name": path.name, "sha256": digest(path)} for path in
                  (options.mod, options.probe, *options.extra_mod)]
        if actual != expected:
            raise ValueError("Reload inputs differ from the fresh run")
        for receipt in expected:
            if digest(directory / "mods" / receipt["name"]) != receipt["sha256"]:
                raise ValueError("A runtime artifact changed between loads")
    if options.mode == "client":
        if options.assets is None:
            raise ValueError("Client checks need the verified asset directory")
        command = client_command(options, directory)
    else:
        forge = options.runtime.resolve() / "forge-1.14.4-28.2.26.jar"
        command = [str(options.java), "-Xmx3g", "-Dskysbuildingpieces.bootstrapPhase=" + options.phase,
                   "-jar", str(forge), "--nogui"]
    # Do not inherit development mod output into a packaged launch.
    environment = {key: value for key, value in os.environ.items() if key not in ("MOD_CLASSES", "target")}
    with (directory / (options.phase + "-console.log")).open("w", encoding="utf-8") as output:
        subprocess.run(command, cwd=directory, env=environment, stdout=output,
                       stderr=subprocess.STDOUT, check=True, timeout=600)
    audit(directory, options.phase, options.mode)
    print("PACKAGED_CHECK_PASS", options.mode, options.phase, directory.name)


if __name__ == "__main__":
    main()
