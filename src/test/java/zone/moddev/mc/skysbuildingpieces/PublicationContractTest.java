package zone.moddev.mc.skysbuildingpieces;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicationContractTest {
    @Test void publicationNeedsThisModsMetadataAndExplicitApproval() throws Exception {
        String properties = text("gradle.properties");
        assertTrue(properties.contains("release_enabled=false"));
        assertTrue(properties.contains("curseforge_project_id=\n"));
        assertTrue(properties.contains("release_repository=MinecraftModDevelopmentMods/SkysBuildingPieces"));
        String workflow = text(".github/workflows/deploy-release.yml");
        assertTrue(workflow.contains("confirm_live_publication"));
        assertTrue(workflow.contains("default: false"));
        assertTrue(workflow.contains("value release_enabled"));
        assertTrue(workflow.contains("Build, test, and audit"));
        assertTrue(workflow.contains("sha256sum --check SHA256SUMS"));
        assertTrue(workflow.contains("publishMavenJavaPublicationToReleaseRepository"));
        assertFalse(workflow.contains("1677588"));
        assertTrue(workflow.indexOf("  publish_maven:") < workflow.indexOf("  publish_curseforge:"));
        assertTrue(workflow.indexOf("  publish_curseforge:") < workflow.indexOf("  publish_github:"));
    }
    @Test void mavenUsesTheVerifiedBundleAndCiRunsOnPushAndPullRequest() throws Exception {
        String release = text("gradle/release.gradle");
        assertTrue(release.contains("if (!releaseEnabled"));
        assertTrue(release.contains("Prepared artifact checksum mismatch"));
        assertTrue(release.contains("lines.size() != 3"));
        assertTrue(release.contains("!checked.add"));
        assertTrue(release.contains("verifyPreparedReleaseArtifacts"));
        String ci = text(".github/workflows/ci.yml");
        assertTrue(ci.contains("  push:"));
        assertTrue(ci.contains("  pull_request:"));
        assertTrue(ci.contains("if-no-files-found: error"));
        assertTrue(ci.contains("integrationTest"));
        assertTrue(ci.contains("--offline"));
        assertFalse(ci.contains("\n           --offline"));
    }
    private String text(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
