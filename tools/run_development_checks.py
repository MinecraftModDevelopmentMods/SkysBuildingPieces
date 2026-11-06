"""Run a test-only probe in a disposable NeoGradle client or server."""

import argparse
import json
import os
from pathlib import Path
import subprocess

from run_packaged_checks import audit, digest, prepare


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--probe", type=Path, required=True)
    parser.add_argument("--mode", choices=("server", "client"), required=True)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--extra-mod", type=Path, action="append", default=[])
    parser.add_argument("--fresh", action="store_true")
    parser.add_argument("--no-early-display", action="store_true",
                        help="Disable the NeoForge splash in this disposable client only")
    options = parser.parse_args()
    if options.no_early_display and options.mode != "client":
        parser.error("--no-early-display requires a client launch")
    if not all(part.isalnum() for part in options.phase.split("-")):
        raise ValueError("Use a simple probe phase name")
    directory = options.directory.resolve()
    build = Path("build").resolve()
    if not directory.is_relative_to(build) or directory == build:
        raise ValueError("Development checks need a disposable directory under build")
    options.mod = options.fixture = options.persistence_order = None
    options.mineralogy_slabs_disabled = False
    if options.fresh:
        prepare(options, directory)
    expected = [{"name": path.name, "sha256": digest(path)}
                for path in (options.probe, *options.extra_mod)]
    if json.loads((directory / "artifact-inputs.json").read_text(encoding="utf-8")) != expected:
        raise ValueError("Probe or companion inputs changed between loads")
    for artifact in expected:
        if digest(directory / "mods" / artifact["name"]) != artifact["sha256"]:
            raise ValueError("A copied probe input changed: " + artifact["name"])

    wrapper = Path("gradlew.bat" if os.name == "nt" else "gradlew").resolve()
    command = [str(wrapper), "run" + options.mode.capitalize(), "--no-daemon", "--console=plain",
               "--max-workers=2", "-PskysBuildingPiecesRunDirectory=" + directory.relative_to(Path.cwd()).as_posix(),
               "-PskysBuildingPiecesProbePhase=" + options.phase]
    console = directory / (options.phase + "-console.log")
    with console.open("x", encoding="utf-8") as output:
        subprocess.run(command, check=True, stdout=output, stderr=subprocess.STDOUT, timeout=600)
    audit(directory, options.phase, options.mode, no_early_display=options.no_early_display)
    print("NEOFORGE_BUILDING_PIECES_DEVELOPMENT_PASS", options.mode, options.phase)


if __name__ == "__main__":
    main()
