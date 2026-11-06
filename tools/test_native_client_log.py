"""Controls for the NeoForge 26.2 client log checks."""

import unittest
import json
from pathlib import Path
import shutil
import tempfile
import tomllib
from unittest.mock import patch
from native_client_log import audit, classify
import run_packaged_checks


LOGIN = ("[Server thread/INFO]: PiecesChecks logged in with entity id 21 at (0, 64, 0)\n"
         "[Server thread/INFO]: PiecesChecks joined the game")
START = "[Server thread/INFO] [IntegratedServer/]: Starting integrated minecraft server version 26.2"
NOTICE = "[Thread-1/FATAL] [net.minecraftforge.common.ForgeConfig/CORE]: Forge config just got changed on the file system!"
PAYLOAD = "[Client thread/ERROR] [net.minecraftforge.fml.network.simple.IndexedMessageCodec/SIMPLENET]: Received empty payload on channel fml:handshake"


class NativeClientLogTest(unittest.TestCase):
    def test_previous_target_notices_are_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE, START, LOGIN, PAYLOAD)), True)

    def test_clean_native_startup_is_accepted(self):
        receipt = classify("\n".join((START, LOGIN)), True)
        self.assertEqual(receipt["native_config_notices"], 0)
        self.assertEqual(receipt["empty_handshakes"], 0)
        self.assertEqual(receipt["notices"], [])

    def test_actual_config_change_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE, START, LOGIN)), False)

    def test_notice_after_world_start_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((START, NOTICE, LOGIN)), True)

    def test_other_fatal_message_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE.replace("just got changed", "failed"), START, LOGIN)), True)

    def test_other_logger_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE.replace("ForgeConfig/CORE", "OtherConfig/CORE"), START, LOGIN)), True)

    def test_repeated_config_notifications_are_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE, NOTICE, NOTICE, START, LOGIN)), True)

    def test_other_handshake_error_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((START, LOGIN, PAYLOAD.replace("fml:handshake", "another:channel"))), True)

    def test_handshake_failure_without_login_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((START, PAYLOAD)), True)

    def test_stderr_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((START, LOGIN, "[STDERR/]: java.lang.IllegalStateException")), True)

    def test_only_the_packaged_offline_authentication_notice_is_accepted(self):
        auth = "\n".join((
            '[00:00:00] [Download-2/ERROR]: Failed to fetch user properties',
            'com.mojang.authlib.exceptions.InvalidCredentialsException: Status: 401',
            'at com.mojang.authlib.exceptions.MinecraftClientHttpException.toAuthenticationException(MinecraftClientHttpException.java:70)',
            'at com.mojang.authlib.yggdrasil.YggdrasilUserApiService.fetchProperties(YggdrasilUserApiService.java:173)',
            'at TRANSFORMER/minecraft@26.2/net.minecraft.client.Minecraft.lambda$new$1(Minecraft.java:409)',
            'at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(CompletableFuture.java:1789)',
            'at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1090)',
            'at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:614)',
            'at java.base/java.lang.Thread.run(Thread.java:1474)',
            'Caused by: MinecraftClientHttpException[type=HTTP_ERROR, status=401, response=ErrorResponse[path=/player/attributes, error=null, errorMessage=null, details=null]]',
            'at com.mojang.authlib.minecraft.client.MinecraftClient.buildHttpException(MinecraftClient.java:170)',
            'at com.mojang.authlib.minecraft.client.MinecraftClient.readServiceResponse(MinecraftClient.java:147)',
            'at com.mojang.authlib.minecraft.client.MinecraftClient.getWithEtag(MinecraftClient.java:81)',
            'at com.mojang.authlib.minecraft.client.MinecraftClient.get(MinecraftClient.java:65)',
            'at com.mojang.authlib.yggdrasil.YggdrasilUserApiService.fetchProperties(YggdrasilUserApiService.java:123)',
            '... 5 more',
        ))
        log = "\n".join((auth, START, LOGIN))
        self.assertEqual(classify(log, True, packaged_offline=True)["notices"],
                         ["Failed to fetch user properties"])
        with self.assertRaises(ValueError):
            classify(log, True)
        with self.assertRaises(ValueError):
            classify(log.replace("Status: 401", "Status: 503"), True, packaged_offline=True)
        with self.assertRaises(ValueError):
            classify(log.replace("CompletableFuture", "AnotherWorker"), True, packaged_offline=True)


class NativeClientFilesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        defaults = Path(__file__).resolve().parent.parent / "src/bootstrapTest/defaultconfigs"
        shutil.copytree(defaults / "config", self.directory / "config")
        self.server = self.directory / "config"
        (self.directory / "logs").mkdir()
        (self.directory / "logs/latest.log").write_text("\n".join((
            START, LOGIN,
            "NEOFORGE_CLIENT_CONTROL_PASS joined=true sky_mods=false")), encoding="utf-8")

    def test_native_defaults_with_different_order_and_comments(self):
        client = self.directory / "config/neoforge-client.toml"
        settings = tomllib.loads(client.read_text(encoding="utf-8"))
        client.write_text("# Native defaults, reordered\n" + "\n".join(
            name + " = " + json.dumps(value) for name, value in reversed(list(settings.items()))),
            encoding="utf-8")
        receipt = audit(self.directory, "control", True)
        self.assertTrue(receipt["unchanged_native_defaults"])
        self.assertEqual(receipt["native_config_notices"], 0)

    def test_changed_default_stops_the_check(self):
        client = self.directory / "config/neoforge-client.toml"
        original = client.read_text(encoding="utf-8")
        self.assertIn("showLoadWarnings = true", original)
        client.write_text(original.replace("showLoadWarnings = true",
                                           "showLoadWarnings = false"), encoding="utf-8")
        with self.assertRaises(ValueError):
            audit(self.directory, "control", True)

    def test_early_window_override_requires_a_matching_profile_and_log(self):
        config = self.directory / "config/fml.toml"
        original = config.read_text(encoding="utf-8")
        config.write_text(original.replace("earlyWindowControl = true", "earlyWindowControl = false"),
                          encoding="utf-8")
        debug = self.directory / "logs/debug.log"
        debug.write_text("[main/INFO]: Loader started", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "early loading window"):
            audit(self.directory, "control", True, no_early_display=True)
        debug.write_text("[main/INFO]: ImmediateWindowProvider not loading because splash screen is disabled",
                         encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "settings differ"):
            audit(self.directory, "control", True)
        receipt = audit(self.directory, "control", True, no_early_display=True)
        self.assertFalse(receipt["early_display"])

    def test_missing_server_config_stops_the_check(self):
        (self.server / "neoforge-server.toml").unlink()
        with self.assertRaises(ValueError):
            audit(self.directory, "control", True)

    def test_login_exception_in_info_log_stops_the_check(self):
        log = self.directory / "logs/latest.log"
        with log.open("a", encoding="utf-8") as output:
            output.write("\n[Server thread/INFO]: lost connection: Internal Exception: "
                         "java.util.ConcurrentModificationException")
        with self.assertRaisesRegex(ValueError, "ConcurrentModificationException"):
            audit(self.directory, "control", True)

    def test_crash_report_stops_the_check(self):
        (self.directory / "crash-reports").mkdir()
        (self.directory / "crash-reports/test.txt").write_text("Simulated failure")
        with self.assertRaises(ValueError):
            audit(self.directory, "control", True)


class PackagedClientLogArchiveTest(unittest.TestCase):
    def test_reload_preserves_each_phases_complete_debug_log(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            logs = directory / "logs"
            logs.mkdir()
            with patch.object(run_packaged_checks, "audit_client"):
                for phase in ("client-fresh", "client-reload"):
                    (logs / "latest.log").write_text("Normal log: " + phase, encoding="utf-8")
                    (logs / "debug.log").write_text("Complete debug log: " + phase, encoding="utf-8")
                    (directory / (phase + "-console.log")).write_text("Normal console", encoding="utf-8")
                    run_packaged_checks.audit(directory, phase, "client")
            for phase in ("client-fresh", "client-reload"):
                self.assertEqual((directory / (phase + "-complete.log")).read_text(encoding="utf-8"),
                                 "Normal log: " + phase)
                self.assertEqual((directory / (phase + "-debug.log")).read_text(encoding="utf-8"),
                                 "Complete debug log: " + phase)

    def test_missing_debug_log_stops_packaged_client_check(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "logs").mkdir()
            (directory / "logs/latest.log").write_text("Normal log", encoding="utf-8")
            (directory / "client-fresh-console.log").write_text("Normal console", encoding="utf-8")
            with patch.object(run_packaged_checks, "audit_client"):
                with self.assertRaisesRegex(FileNotFoundError, "debug.log"):
                    run_packaged_checks.audit(directory, "client-fresh", "client")


if __name__ == "__main__":
    unittest.main()
