package zone.moddev.mc.skysbuildingpieces;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicationContractTest {
    @Test
    void publicationNeedsThisModsMetadataAndExplicitApproval() throws Exception {
        String properties = text("gradle.properties");
        java.util.Properties metadata = new java.util.Properties();

        metadata.load(new java.io.StringReader(properties));

        String projectId = metadata.getProperty("curseforge_project_id", "");

        assertTrue(metadata.getProperty("release_enabled").matches("true|false"));

        if (!projectId.matches("[1-9][0-9]*")) {
            assertEquals("false", metadata.getProperty("release_enabled"));
        }

        assertNotEquals("1677588", projectId);
        assertTrue(properties.contains("release_repository=MinecraftModDevelopmentMods/SkysBuildingPieces"));

        String workflow = text(".github/workflows/deploy-release.yml");

        assertTrue(workflow.contains("confirm_live_publication"));
        assertTrue(workflow.contains("default: false"));
        assertTrue(workflow.contains("value release_enabled"));
        assertTrue(workflow.contains("Build, test, and audit"));
        assertTrue(workflow.contains("sha256sum --check SHA256SUMS"));
        assertTrue(workflow.contains("publishMavenJavaPublicationToReleaseRepository"));
        assertTrue(workflow.contains("curseforge-retry-attempts: 1"));
        assertFalse(workflow.contains("1677588"));
        assertTrue(workflow.indexOf("  publish_maven:") < workflow.indexOf("  publish_curseforge:"));
        assertTrue(workflow.indexOf("  publish_curseforge:") < workflow.indexOf("  publish_github:"));
    }

    @Test
    void releaseRoutingAndToolchainsFollowBranchMetadata() throws Exception {
        String workflow = text(".github/workflows/deploy-release.yml");
        String ci = text(".github/workflows/ci.yml");
        String tags = text(".github/workflows/release-on-tag.yml");

        assertTrue(workflow.contains("mc_major=\"${without_patch::-2}\""));
        assertTrue(workflow.contains("2) loader_suffix=-neo"));
        assertTrue(workflow.contains("value java_toolchain_version"));
        assertTrue(ci.contains("steps.target.outputs.java_setup_version"));
        assertTrue(ci.contains("steps.target.outputs.gradle_java_version"));
        assertTrue(tags.contains("s/^minecraft_version=//p"));
        assertTrue(tags.contains("s/^loader_code=//p"));
        assertFalse(tags.contains("\\.112021$"));
    }

    @Test
    void mavenUsesTheVerifiedBundleAndCiRunsOnPushAndPullRequest() throws Exception {
        String release = text("gradle/release.gradle");

        assertTrue(release.contains("if (!releaseEnabled"));
        assertTrue(release.contains("Prepared artifact checksum mismatch"));
        assertTrue(release.contains("lines.size() != 3"));
        assertTrue(release.contains("!checked.add"));
        assertTrue(release.contains("verifyPreparedReleaseArtifacts"));
        assertTrue(release.contains("collect { it.toString() }"));
        assertTrue(release.contains("verifyReleaseBundleContract"));

        String ciWorkflow = text(".github/workflows/ci.yml");

        assertTrue(ciWorkflow.contains("  push:"));
        assertTrue(ciWorkflow.contains("  pull_request:"));
        assertTrue(ciWorkflow.contains("if-no-files-found: error"));
        assertTrue(ciWorkflow.contains("integrationTest"));
        assertTrue(ciWorkflow.contains("--offline"));
        assertFalse(ciWorkflow.contains("\n           --offline"));
    }

    private String text(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
