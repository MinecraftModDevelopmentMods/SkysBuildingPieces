# Building from source

Use Java 17 for Gradle and Temurin Java 8.0.502+7 for compilation and Minecraft.
Set `SBP_JAVA8_HOME` to that Java 8 installation, then run:

```text
gradlew clean check integrationTest build javadoc verifyReleaseArtifacts writeReleaseChecksums
gradlew genEclipseRuns verifyEclipseProductionClasspath
```

The main jar, sources and Javadocs are in `build/libs`. Tests and runtime probes
are separate from the published mod and ordinary Eclipse launches.

## Optional Mineralogy development runs

`runMineralogyClient` and `runMineralogyServer` use a separate `run-mineralogy`
folder. Supply the exact Mineralogy and OreSpawn jars listed in
`gradle/mineralogy-runtime.properties`; their checksums are checked before use.
You can select their locations with `-PmineralogyRuntimeJar=<jar>` and
`-PorespawnRuntimeJar=<jar>`. The default paths follow the sibling-checkout
layout used by the projects.

These jars are development inputs, not required dependencies. Neither is
bundled with Building Pieces. Normal builds and CI work without them.
`integrationMineralogyTest` runs the optional fresh-world and reload checks.

The former-add-on migration fixture also uses a matching Terrain Smoother
development jar. Build that project first, then run
`gradlew -PenableMineralogyFixtures=true prepareMineralogyFixture`.
The same option enables `prepareLegacyMineralogyWorld`. These fixtures are
opt-in and are not needed for ordinary builds or game launches.
