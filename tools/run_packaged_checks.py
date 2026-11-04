"""Run a disposable native Forge profile with exact, already-built artifacts."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import shlex
import subprocess
import zipfile
from native_client_log import audit as audit_client, audit_server


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


def verify_native_libraries(directory, artifacts):
    """Match the extracted native files to the checksum-verified Windows jars."""
    checked = 0
    for artifact in artifacts:
        with zipfile.ZipFile(artifact) as archive:
            for member in archive.namelist():
                if not member.lower().endswith(".dll"):
                    continue
                target = (directory / member).resolve()
                if not target.is_relative_to(directory.resolve()) or not target.is_file():
                    raise ValueError("Client native library is missing: " + member)
                if hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(archive.read(member)).digest():
                    raise ValueError("Client native library checksum changed: " + member)
                checked += 1
    if not checked:
        raise ValueError("The official client metadata contains no Windows native libraries")


def argument_file(java, directory, phase, arguments):
    # Java 17 argument files avoid Windows' command-line length limit. Quoting
    # each argument also preserves paths containing spaces.
    source = Path(__file__).resolve().parent.parent / "src/bootstrapTest/native-log4j2.xml"
    logging = directory / "native-log4j2.xml"
    if logging.exists() and digest(logging) != digest(source):
        raise ValueError("The packaged logging configuration changed between loads")
    shutil.copyfile(source, logging)
    arguments = ["-Dlog4j.configurationFile=" + logging.as_uri(), *arguments]
    target = directory / (phase + "-launch-arguments.txt")
    target.write_text("\n".join(json.dumps(str(value), ensure_ascii=False)
                                for value in arguments) + "\n", encoding="utf-8")
    return [str(java), "@" + str(target)]


def client_command(options, directory):
    runtime = options.runtime.resolve()
    base = json.loads((runtime / "versions/1.20.1/1.20.1.json").read_text(encoding="utf-8"))
    profiles = [runtime / "versions" / name / (name + ".json")
                for name in ("1.20.1-forge-47.4.10", "forge-47.4.10")]
    profile = next((path for path in profiles if path.is_file()), None)
    if profile is None:
        raise ValueError("The installed client has no Forge 47.4.10 profile")
    forge = json.loads(profile.read_text(encoding="utf-8"))
    if base["id"] != "1.20.1" or forge["inheritsFrom"] != "1.20.1":
        raise ValueError("This launcher requires the verified Forge 47 client metadata")
    libraries = {}
    for layer in (base, forge):
        for library in layer["libraries"]:
            if allowed(library.get("rules")):
                coordinates = library["name"].split(":")
                libraries[":".join(coordinates[:2] + coordinates[3:])] = library
    classpath = []
    native_artifacts = []
    for library in libraries.values():
        artifact = (library.get("downloads") or {}).get("artifact")
        if artifact is None:
            continue
        path = runtime / "libraries" / artifact["path"]
        if digest(path, "sha1") != artifact["sha1"].upper():
            raise ValueError("Client library checksum changed: " + library["name"])
        classpath.append(path)
        if library["name"].split(":")[-1] == "natives-windows":
            native_artifacts.append(path)
    client = runtime / "versions/1.20.1/1.20.1.jar"
    if digest(client, "sha1") != base["downloads"]["client"]["sha1"].upper():
        raise ValueError("Official client checksum changed")
    classpath.append(client)
    assets = options.assets.resolve()
    index = assets / "indexes" / (base["assetIndex"]["id"] + ".json")
    if digest(index, "sha1") != base["assetIndex"]["sha1"].upper():
        raise ValueError("The official client asset index is missing or damaged")
    natives = (options.natives or runtime.parent / (runtime.name + "-natives")).resolve()
    verify_native_libraries(natives, native_artifacts)
    substitutions = {
        "library_directory": (runtime / "libraries").as_posix(),
        "classpath_separator": os.pathsep,
        "version_name": base["id"],
    }
    arguments = ["-Xmx3g", "-Djava.net.preferIPv4Stack=true",
                 "-Djava.library.path=" + str(natives),
                 "-Dskysbuildingpieces.bootstrapPhase=" + options.phase]
    for argument in forge["arguments"]["jvm"]:
        for key, value in substitutions.items():
            argument = argument.replace("${" + key + "}", value)
        if "${" in argument:
            raise ValueError("Unresolved native JVM argument: " + argument)
        arguments.append(argument)
    arguments.extend(("-cp", os.pathsep.join(str(path) for path in classpath), forge["mainClass"]))
    arguments.extend(forge["arguments"]["game"])
    arguments.extend(("--username", "BuildingPiecesChecks", "--version", forge["id"],
                      "--gameDir", str(directory), "--assetsDir", str(assets),
                      "--assetIndex", base["assetIndex"]["id"], "--uuid", "1" * 32,
                      "--accessToken", "0", "--userType", "legacy", "--versionType", "release"))
    return argument_file(options.java, directory, options.phase, arguments)


def server_command(options, directory):
    runtime = options.runtime.resolve()
    filename = "win_args.txt" if os.name == "nt" else "unix_args.txt"
    source = runtime / "libraries/net/minecraftforge/forge/1.20.1-47.4.10" / filename
    arguments = ["-Xmx3g", "-Djava.net.preferIPv4Stack=true",
                 "-Dskysbuildingpieces.bootstrapPhase=" + options.phase]
    for argument in shlex.split(source.read_text(encoding="utf-8")):
        argument = argument.replace("libraries/", (runtime / "libraries").as_posix() + "/")
        argument = argument.replace("-DlibraryDirectory=libraries",
                                    "-DlibraryDirectory=" + (runtime / "libraries").as_posix())
        arguments.append(argument)
    arguments.append("--nogui")
    return argument_file(options.java, directory, options.phase, arguments)


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
    (directory / "options.txt").write_text("lang:en_us\nfullscreen:false\nrenderDistance:2\npauseOnLostFocus:false\n", encoding="utf-8")
    shutil.copytree("src/bootstrapTest/defaultconfigs", directory, dirs_exist_ok=True)
    if options.mineralogy_slabs_disabled:
        configuration = directory / "config/mineralogy-common.toml"
        configuration.parent.mkdir(exist_ok=True)
        configuration.write_text("[options]\nGENERATE_ROCKSLAB=false\n", encoding="utf-8")
    profile = {"persistence_order": options.persistence_order,
               "mineralogy_slabs_disabled": options.mineralogy_slabs_disabled}
    (directory / "qualification-options.json").write_text(json.dumps(profile, indent=2) + "\n", encoding="utf-8")
    if options.fixture:
        with zipfile.ZipFile(options.fixture) as archive:
            for name in archive.namelist():
                target = (directory / "world" / name).resolve()
                if not target.is_relative_to(directory / "world"):
                    raise ValueError("Unsafe fixture archive path: " + name)
            archive.extractall(directory / "world")
        shutil.copyfile(options.fixture.with_suffix(".json"), directory / "forward-fixture.json")
        biomes = options.fixture.with_suffix(".biomes.json")
        if biomes.is_file():
            shutil.copyfile(biomes, directory / "forward-biomes.json")
        shutil.copyfile(directory / "world/level.dat", directory / "source-level.dat")


def audit(directory, phase, mode):
    if mode == "client":
        audit_client(directory, phase)
    else:
        log = (directory / "logs/latest.log").read_text(encoding="utf-8")
        audit_server(directory, phase)
        required = ("Done (", "BUILDING_PIECES_NATIVE_BOOTSTRAP_PASS " + phase)
        missing = [marker for marker in required if marker not in log]
        if missing:
            raise ValueError("Packaged runtime completion checks are missing: " + repr(missing))
    # A reload replaces the active files. Retain both logs from every phase.
    shutil.copyfile(directory / "logs/latest.log", directory / (phase + "-complete.log"))
    shutil.copyfile(directory / "logs/debug.log", directory / (phase + "-debug.log"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("java", "runtime", "directory", "mod", "probe"):
        parser.add_argument("--" + option, type=Path, required=True)
    parser.add_argument("--mode", choices=("server", "client"), required=True)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--assets", type=Path)
    parser.add_argument("--natives", type=Path)
    parser.add_argument("--fixture", type=Path)
    parser.add_argument("--extra-mod", type=Path, action="append", default=[])
    parser.add_argument("--fresh", action="store_true")
    parser.add_argument("--persistence-order", choices=("forward", "reverse"))
    parser.add_argument("--mineralogy-slabs-disabled", action="store_true")
    options = parser.parse_args()
    directory = options.directory.resolve()
    if not directory.is_relative_to(Path("build").resolve()) or directory == Path("build").resolve():
        raise ValueError("Packaged checks must stay inside this checkout's build directory")
    if options.fresh:
        prepare(options, directory)
    else:
        profile = {"persistence_order": options.persistence_order,
                   "mineralogy_slabs_disabled": options.mineralogy_slabs_disabled}
        if json.loads((directory / "qualification-options.json").read_text(encoding="utf-8")) != profile:
            raise ValueError("Qualification options changed between loads")
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
        command = server_command(options, directory)
    if options.mineralogy_slabs_disabled:
        command.insert(1, "-Dskysbuildingpieces.expectMineralogySlabsDisabled=true")
    if options.persistence_order:
        command.insert(1, "-Dskysbuildingpieces.persistenceOrder=" + options.persistence_order)
    # Do not inherit development mod output into a packaged launch.
    environment = {key: value for key, value in os.environ.items() if key not in ("MOD_CLASSES", "target")}
    with (directory / (options.phase + "-console.log")).open("w", encoding="utf-8") as output:
        subprocess.run(command, cwd=directory, env=environment, stdout=output,
                       stderr=subprocess.STDOUT, check=True, timeout=600)
    audit(directory, options.phase, options.mode)
    print("PACKAGED_CHECK_PASS", options.mode, options.phase, directory.name)


if __name__ == "__main__":
    main()
