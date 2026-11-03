# Building and running the mod

This branch targets Minecraft 1.19.4 and Forge 45.4.0. Run the included Gradle
wrapper with Java 17. Compilation and Minecraft use Java 17; the pinned production
toolchain is Temurin 17.0.1+12. ForgeGradle uses Java 25 for its Mavenizer tool.
The native log checks and resource helpers use Python 3.11 or later. Keep it on
the command path, or pass `-PpythonExecutable=<python executable>` to Gradle.

```text
gradlew clean check build javadoc verifyReleaseArtifacts writeReleaseChecksums
gradlew genEclipseRuns eclipse verifyEclipseProductionClasspath
```

The main jar goes in the game's mods folder. Sources and Javadocs are separate
jars in `build/libs`, and `build/release/SHA256SUMS` records all three checksums.

## Eclipse and development runs

Import the project through Buildship with the Gradle wrapper, then generate the
launches. Ordinary client and server launches use processed production resources
and exclude test classes.

- `runClient` and `runServer` load Building Pieces on its own.
- `runGrassSlabsClient` and `runGrassSlabsServer` add the coordinated Grass Slabs
  candidate recorded in `gradle/grass-slabs-release.properties`.
- `runMineralogyClient` and `runMineralogyServer` add the optional Mineralogy and
  OreSpawn jars recorded in `gradle/mineralogy-runtime.properties`.

The Grass Slabs pin currently names a source candidate, not a public release.
Build that exact commit in the sibling `SkysGrassSlabs119/SkysGrassSlabs` checkout,
or pass `-PgrassSlabsMainJar=<jar>` and `-PgrassSlabsDeobfJar=<jar>`. Both jars must
match their pinned hashes. CI builds the pinned commit before testing the optional
integration; it can run after that commit is available in the upstream repository.
Building Pieces does not bundle any Grass Slabs classes or require it to play.

Optional Mineralogy/OreSpawn jars also need their exact recorded hashes. Each
ordinary profile has its own game folder and leaves existing saves alone.

## Runtime checks

`check` runs focused tests, native gameplay checks, recipe-book discovery and
fresh/reload servers, both standalone and with Grass Slabs. The isolated native
client checks bake the models, inspect colours and glass joins, check recipe
previews, and save/reload a separate world. Verification worlds stay under `build`.

`nativeInstalledPolicyTest` uses a build-only legacy policy fixture. There is no
official BuildingBricks release for this Minecraft target, and that fixture is
not a claim of compatibility with an unofficial port. Genuine saved-world
fixtures separately exercise conversion before vanilla data fixing.

Native log checks reject errors, fatal messages, standard-error output and crash
reports. They include rolled logs from earlier loads, as well as the latest log,
so a clean reload cannot hide an earlier failure. Upgrade fixtures are locked
to their source checksums. The audit permits only their exact recorded vanilla
registry notices, with matching numeric IDs and proof that retired entries have
no saved uses. Named 1.14–1.18 fixtures also check every retained biome cell
against Minecraft's own conversion before accepting old biome-registry notices.
The 1.18 fixture includes the full native world height. All other errors fail.
The checked Forge settings must remain unchanged throughout the test.

The resource helpers in `tools` regenerate models, translations and native
recipe/advancement JSON from the fixed catalogues. Unlocks retain their existing
identities; the server checks discovery from both inventory and crafting inputs.

For the optional Mineralogy profiles, first run:

```text
gradlew prepareOfficialMineralogyDevelopment -PgenerateOptionalDevelopment=true
```

This makes separate mapped development jars from the two checksum-locked
production jars. Their native access rules are preserved. Ordinary builds
and launches remain independent of those optional mods.
