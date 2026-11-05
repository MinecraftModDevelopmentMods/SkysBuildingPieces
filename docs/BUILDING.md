# Building and running the mod

This branch uses Minecraft 1.20.6, NeoForge 20.6.139 and Temurin Java 21.0.7+6.
Use the Gradle 9.2.1 wrapper included in the checkout.

```text
gradlew clean check build javadoc verifyReleaseArtifacts writeReleaseChecksums
gradlew eclipse verifyEclipseConfiguration -Declipse.application=org.eclipse.ui.ide.workbench
```

The main jar goes in the game's mods folder. The sources and Javadoc jars in
`build/libs` are for developers. Check `build/release/SHA256SUMS` to verify any
of those three jars.

## Eclipse and development runs

Import the checkout as a Gradle project with Buildship and use the wrapper.
Keep Buildship and the command line on the same Gradle home so the launcher
doesn't pick up duplicate copies of its libraries. Gradle generates the
launches and processes the resources; ordinary game launches exclude test code.

- `runClient` and `runServer` start Building Pieces on its own.
- `runGrassSlabsClient` and `runGrassSlabsServer` add the Grass Slabs build
  listed in `gradle/grass-slabs-release.properties`.
- `runMineralogyClient` and `runMineralogyServer` add the NeoForge Mineralogy
  and OreSpawn builds listed in `gradle/mineralogy-runtime.properties`.

Build the matching companion jars before starting an optional profile. You can
point the profiles at exact jars with `-PgrassSlabsMainJar=<jar>`,
`-PmineralogyMainJar=<jar>` and `-PoreSpawnMainJar=<jar>`. Each profile checks
the version and checksum before copying the jars into its own run directory.
The normal game does not need any of these mods.

## Runtime checks

`preparePackagedRuntimeProbes` builds separate test mods for the client and
server checks. They cover placement, rendering, the recipe book, saved pieces
and inventory migration. The probes do not go in a player installation.

`tools/run_packaged_checks.py` runs the probes with an installed NeoForge
runtime and exact jar inputs. Use Python 3.11 or later. The harness keeps
disposable worlds and the logs for both fresh starts and reloads under
`build`. Never point it at a world you play in.

The test fixture for installed-mod replacement checks the replacement policy.
It isn't a BuildingBricks port. Historical world fixtures test recovery of
actual saved pieces before Minecraft loads their chunks and inventories.
