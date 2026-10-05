package zone.moddev.mc.skysbuildingpieces;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {
    @Test void documentationSupportFilesUsePortableLineEndings() throws Exception {
        String build=new String(Files.readAllBytes(Paths.get("build.gradle")),StandardCharsets.UTF_8);
        assertTrue(build.contains("'.css'"));
        assertTrue(build.contains("'.js'"));
        assertTrue(build.contains("Non-portable archive line endings"));
    }
    @Test void localContextIsIgnoredAndNotTracked() throws Exception {
        for(String path:new String[]{"AGENTS.md","agent-notes/evidence.json",".codex/local.json",".claude/settings.json","run/world/level.dat","build/libs/temporary.jar"}) {
            Process p=new ProcessBuilder("git","check-ignore","-q",path).start();assertEquals(0,p.waitFor(),path);
        }
        Process p=new ProcessBuilder("git","ls-files","-z").start();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
        while((count=p.getInputStream().read(buffer))!=-1)out.write(buffer,0,count);assertEquals(0,p.waitFor());
        String tracked=new String(out.toByteArray(),StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
        for(String forbidden:new String[]{"agents.md","agent-notes/",".codex/",".claude/","run/","build/"})assertFalse(tracked.contains(forbidden),forbidden);
    }
    @Test void branchWorkflowsUseExactArtifactsAndCannotPublish() throws Exception {
        String ci=new String(Files.readAllBytes(Paths.get(".github/workflows/ci.yml")),StandardCharsets.UTF_8);
        assertTrue(ci.contains("master-1.10.2"));assertTrue(ci.contains("if-no-files-found: error"));
        for(String classifier:new String[]{"","-sources","-javadoc"})assertTrue(ci.contains("SkysBuildingPieces-0.2.0.110021"+classifier+".jar"));
        String tags=new String(Files.readAllBytes(Paths.get(".github/workflows/release-on-tag.yml")),StandardCharsets.UTF_8);
        assertTrue(tags.contains("verifyReleaseArtifacts"));assertFalse(tags.contains("secrets."));assertFalse(tags.contains("gh release"));assertFalse(tags.contains("publishRelease"));
    }
}
