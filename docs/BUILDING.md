# Building and running the mod

Run Gradle with Java 17. Production code and Minecraft use Java 8; the tested
toolchain is Temurin 8.0.502+7. Use the included Gradle wrapper.

```text
gradlew clean check build javadoc verifyReleaseArtifacts writeReleaseChecksums
gradlew genEclipseRuns eclipse verifyEclipseProductionClasspath
```

The main jar is in `build/libs`. Sources and Javadocs are separate jars, and
`build/release/SHA256SUMS` records their checksums. Only the main jar goes into
a game's mods folder.

## Eclipse

Import the project through Buildship, using the Gradle wrapper. Regenerate
the launches after changing dependencies. The generated configuration uses
processed resources and keeps test code out of ordinary game runs.

- `runClient` and `runServer` run Building Pieces on its own.
- `runGrassSlabsClient` and `runGrassSlabsServer` also load the released Grass
  Slabs jar recorded in `gradle/grass-slabs-release.properties`.
- `runMineralogyClient` and `runMineralogyServer` use the optional Mineralogy
  and OreSpawn jars recorded in `gradle/mineralogy-runtime.properties`.

Each profile has its own game folder. Grass Slabs is downloaded from its public
release, checked against its SHA-256 and mapped to development names. There is
no sibling source build to prepare. The published Grass Slabs jar remains
unchanged, and none of its classes are bundled into Building Pieces.

For the Mineralogy profile, supply the jars named in its lock file, or use
`-PmineralogyRuntimeJar=<jar>` and `-PorespawnRuntimeJar=<jar>` to select matching
copies. They must pass the recorded checksums.

## Runtime checks

`check` runs the unit suite and disposable server checks, both standalone and
with the released Grass Slabs jar. `integrationGrassSlabsClientProcess` checks
the combined client models. `integrationMineralogyTest` and
`integrationMineralogyClientProcess` cover the optional rock pieces when the
locked native jars are available.

Verification worlds live under `build/`. Keep player worlds in the separate
ordinary game folders or another instance.
