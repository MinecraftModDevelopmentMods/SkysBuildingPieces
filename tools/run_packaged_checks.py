"""Run a disposable native NeoForge profile with exact, already-built artifacts."""

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
from native_registry_notices import audit_text, profile_for
from native_runtime_notices import windows_counter_notice


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
                if not target.is_file():
                    # Some launchers flatten the extracted native library paths.
                    target = (directory / Path(member).name).resolve()
                if not target.is_relative_to(directory.resolve()) or not target.is_file():
                    raise ValueError("Client native library is missing: " + member)
                if hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(archive.read(member)).digest():
                    raise ValueError("Client native library checksum changed: " + member)
                checked += 1
    if not checked:
        raise ValueError("The official client metadata contains no Windows native libraries")


def argument_file(java, directory, phase, arguments):
    # Java argument files avoid Windows' command-line length limit. Quoting
    # each argument also preserves paths containing spaces.
    target = directory / (phase + "-launch-arguments.txt")
    target.write_text("\n".join(json.dumps(str(value), ensure_ascii=False)
                                for value in arguments) + "\n", encoding="utf-8")
    return [str(java), "@" + str(target)]


def probe_property(options):
    kind = getattr(options, "probe_kind", "bootstrap")
    return "-Dskysbuildingpieces." + kind + "Phase=" + options.phase


def client_command(options, directory):
    runtime = options.runtime.resolve()
    base = json.loads((runtime / "versions/26.3/26.3.json").read_text(encoding="utf-8"))
    profiles = [runtime / "versions" / name / (name + ".json")
                for name in ("neoforge-26.3.0.99",)]
    profile = next((path for path in profiles if path.is_file()), None)
    if profile is None:
        raise ValueError("The installed client has no NeoForge 26.3.0.99 profile")
    forge = json.loads(profile.read_text(encoding="utf-8"))
    if base["id"] != "26.3" or forge["inheritsFrom"] != "26.3":
        raise ValueError("This launcher requires the verified NeoForge 26.3 client metadata")
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
        classifier = library["name"].split(":")[-1]
        if classifier in {"natives-windows-arm64", "natives-windows-x86"}:
            continue
        classpath.append(path)
        if classifier == "natives-windows":
            native_artifacts.append(path)
    # NeoForge locates the patched game and universal jar through the library directory.
    installed_libraries = {
        "net/neoforged/minecraft-client-patched/26.3.0.99/minecraft-client-patched-26.3.0.99.jar":
            "59724A91C97404F45FE517BED6EFBF744731D182E03583131EB283AA9D6488DC",
        "net/neoforged/neoforge/26.3.0.99/neoforge-26.3.0.99-universal.jar":
            "1C41DC00C92E87CD874788EE51720E3217E238423B853AC13B8242CF1973A13E",
    }
    for name, expected in installed_libraries.items():
        path = runtime / "libraries" / name
        if digest(path) != expected:
            raise ValueError("NeoForge installer output checksum changed: " + name)
    client = runtime / "versions/26.3/26.3.jar"
    if digest(client, "sha1") != base["downloads"]["client"]["sha1"].upper():
        raise ValueError("Official client checksum changed")
    classpath.append(client)
    assets = options.assets.resolve()
    index = assets / "indexes" / (base["assetIndex"]["id"] + ".json")
    if digest(index, "sha1") != base["assetIndex"]["sha1"].upper():
        raise ValueError("The official client asset index is missing or damaged")
    natives = (options.natives or directory / "natives").resolve()
    if options.natives is None:
        natives.mkdir(exist_ok=True)
        for artifact in native_artifacts:
            with zipfile.ZipFile(artifact) as jar:
                for member in jar.namelist():
                    if not member.lower().endswith(".dll"):
                        continue
                    target = natives / Path(member).name
                    payload = jar.read(member)
                    if target.exists() and target.read_bytes() != payload:
                        raise ValueError("Native library changed between loads: " + target.name)
                    if not target.exists():
                        target.write_bytes(payload)
    verify_native_libraries(natives, native_artifacts)
    substitutions = {
        "library_directory": (runtime / "libraries").as_posix(),
        "classpath_separator": os.pathsep,
        "version_name": base["id"],
    }
    arguments = ["-Xmx3g", "-Djava.net.preferIPv4Stack=true",
                 "-Djava.library.path=" + str(natives),
                 probe_property(options)]
    # The loader profile inherits these Java 25 options from Mojang's profile.
    inherited_options = ["-XX:StackShadowPages=32", "--enable-native-access=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"]
    if not all(option in base["arguments"]["jvm"] for option in inherited_options):
        raise ValueError("The Minecraft Java 25 startup flags changed")
    arguments.extend(inherited_options)
    for argument in forge["arguments"]["jvm"]:
        for key, value in substitutions.items():
            argument = argument.replace("${" + key + "}", value)
        if "${" in argument:
            raise ValueError("Unresolved native JVM argument: " + argument)
        arguments.append(argument)
    arguments.extend(("-cp", os.pathsep.join(str(path) for path in classpath), forge["mainClass"]))
    arguments.extend(forge["arguments"]["game"])
    arguments.extend(("--username", "PiecesChecks", "--version", forge["id"],
                      "--gameDir", str(directory), "--assetsDir", str(assets),
                      "--assetIndex", base["assetIndex"]["id"], "--uuid", "1" * 32,
                      "--accessToken", "0", "--userType", "legacy", "--versionType", "release"))
    return argument_file(options.java, directory, options.phase, arguments)


def server_command(options, directory):
    runtime = options.runtime.resolve()
    filename = "win_args.txt" if os.name == "nt" else "unix_args.txt"
    libraries = runtime / "libraries"
    source = libraries / "net/neoforged/neoforge/26.3.0.99" / filename
    launch = shlex.split(source.read_text(encoding="utf-8"))
    if "net.neoforged.fml.startup.Server" not in launch or "26.3.0.99" not in launch:
        raise ValueError("Expected the official NeoForge 26.3.0.99 server arguments")
    local_libraries = directory / "libraries"
    if not local_libraries.exists():
        if os.name == "nt":
            subprocess.run(["cmd", "/d", "/c", "mklink", "/j", str(local_libraries), str(libraries)],
                           check=True, capture_output=True)
        else:
            local_libraries.symlink_to(libraries, target_is_directory=True)
    if local_libraries.resolve() != libraries.resolve():
        raise ValueError("The disposable server is not using the selected NeoForge installation")
    # The plain layout keeps diagnostics without loading unsupported native Netty classes
    # just to attach code-source names to Windows platform-probe stack frames.
    log_config = Path(__file__).resolve().parents[1] / "src/bootstrapTest/log4j2-server.xml"
    arguments = ["-Xmx3g", probe_property(options),
                 "-Dlog4j.configurationFile=" + log_config.as_uri(), *launch, "nogui"]
    return argument_file(options.java, directory, options.phase, arguments)


def prepare(options, directory):
    if directory.exists():
        raise ValueError("Fresh checks require a new disposable directory")
    directory.mkdir(parents=True)
    mods = directory / "mods"
    mods.mkdir()
    receipts = []
    inputs = ([options.mod] if options.mod else []) + [options.probe, *options.extra_mod]
    for source in inputs:
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
        "server-port=0\nuse-native-transport=false\ngenerate-structures=false\n"
        "spawn-animals=false\nspawn-monsters=false\n"
        "max-tick-time=120000\n", encoding="utf-8")
    (directory / "options.txt").write_text("lang:en_us\nfullscreen:false\nrenderDistance:2\npauseOnLostFocus:false\n", encoding="utf-8")
    shutil.copytree("src/bootstrapTest/defaultconfigs", directory, dirs_exist_ok=True)
    no_early_display = getattr(options, "no_early_display", False)
    if no_early_display:
        fml = directory / "config/fml.toml"
        original = fml.read_text(encoding="utf-8")
        setting = "earlyWindowControl = true"
        if original.count(setting) != 1:
            raise ValueError("Cannot identify the NeoForge early display setting")
        fml.write_text(original.replace(setting, "earlyWindowControl = false"), encoding="utf-8")
    if options.mineralogy_slabs_disabled:
        configuration = directory / "config/mineralogy-common.toml"
        configuration.parent.mkdir(exist_ok=True)
        configuration.write_text("[options]\nGENERATE_ROCKSLAB=false\n", encoding="utf-8")
    if getattr(options, "directional_slabs_off", False):
        configuration = directory / "config/skysbuildingpieces-common.toml"
        configuration.parent.mkdir(exist_ok=True)
        configuration.write_text("[gameplay]\ndirectionalSlabPlacement=false\n", encoding="utf-8")
    profile = {"probe_kind": getattr(options, "probe_kind", "bootstrap"),
               "persistence_order": options.persistence_order,
               "no_early_display": no_early_display,
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
        renamed_blocks = options.fixture.with_suffix(".forge50-grass-rename.json")
        if renamed_blocks.is_file():
            shutil.copyfile(renamed_blocks, directory / "forward-block-renames.json")
        shutil.copyfile(directory / "world/level.dat", directory / "source-level.dat")


def audit_console(directory, phase, packaged_offline=False):
    """Include loader diagnostics that can happen before Minecraft opens its logs."""
    path = directory / (phase + "-console.log")
    console = path.read_text(encoding="utf-8")
    status_error = re.search(r"(?m)^(?:ERROR|FATAL) Status(?:Console)?Listener\b.*", console)
    if status_error:
        raise ValueError("Unexpected loader logging failure: " + status_error[0])
    console = windows_counter_notice(console)
    if packaged_offline:
        from native_client_log import offline_client_notices
        console, _ = offline_client_notices(console)
    audit_text(console, profile_for(directory))


def audit(directory, phase, mode, probe_kind="bootstrap", packaged_offline=False, no_early_display=False):
    audit_console(directory, phase, packaged_offline)
    if mode == "client":
        audit_client(directory, phase, packaged_offline=packaged_offline, no_early_display=no_early_display)
    else:
        log = (directory / "logs/latest.log").read_text(encoding="utf-8")
        audit_server(directory, phase)
        marker = ("BUILDING_PIECES_PROBE_PASS " if probe_kind == "integration"
                  else "BUILDING_PIECES_NATIVE_BOOTSTRAP_PASS ")
        required = ("Done (", marker + phase)
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
    parser.add_argument("--probe-kind", choices=("bootstrap", "integration"), default="bootstrap")
    parser.add_argument("--phase", required=True)
    parser.add_argument("--directional-slabs-off", action="store_true")
    parser.add_argument("--assets", type=Path)
    parser.add_argument("--natives", type=Path)
    parser.add_argument("--fixture", type=Path)
    parser.add_argument("--extra-mod", type=Path, action="append", default=[])
    parser.add_argument("--fresh", action="store_true")
    parser.add_argument("--no-early-display", action="store_true",
                        help="Disable the NeoForge splash in this disposable client only")
    parser.add_argument("--persistence-order", choices=("forward", "reverse"))
    parser.add_argument("--mineralogy-slabs-disabled", action="store_true")
    options = parser.parse_args()
    if options.no_early_display and options.mode != "client":
        parser.error("--no-early-display requires a client launch")
    directory = options.directory.resolve()
    if not directory.is_relative_to(Path("build").resolve()) or directory == Path("build").resolve():
        raise ValueError("Packaged checks must stay inside this checkout's build directory")
    if options.fresh:
        prepare(options, directory)
    else:
        profile = {"probe_kind": options.probe_kind,
                   "persistence_order": options.persistence_order,
                   "no_early_display": options.no_early_display,
                   "mineralogy_slabs_disabled": options.mineralogy_slabs_disabled}
        saved_profile = json.loads((directory / "qualification-options.json").read_text(encoding="utf-8"))
        saved_profile.setdefault("probe_kind", "bootstrap")
        saved_profile.setdefault("no_early_display", False)
        if saved_profile != profile:
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
    with (directory / (options.phase + "-console.log")).open("x", encoding="utf-8") as output:
        subprocess.run(command, cwd=directory, env=environment, stdout=output,
                       stderr=subprocess.STDOUT, check=True, timeout=600)
    audit(directory, options.phase, options.mode, options.probe_kind,
          packaged_offline=options.mode == "client", no_early_display=options.no_early_display)
    print("PACKAGED_CHECK_PASS", options.mode, options.phase, directory.name)


if __name__ == "__main__":
    main()
