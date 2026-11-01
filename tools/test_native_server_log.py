"""Negative controls for Forge 36 server completion and native settings."""

from pathlib import Path
import shutil
import gzip
import tempfile
import unittest

from native_client_log import audit_server, classify_server


NOTICE = ('[Thread-1/FATAL] [net.minecraftforge.common.ForgeConfig/CORE]: '
          'Forge config just got changed on the file system!')
DEBUG = ('[Thread-1/DEBUG] [net.minecraftforge.fml.config.ConfigFileTypeHandler/CONFIG]: '
         'Config file forge-server.toml changed, sending notifies')
START = '[Server thread/INFO] [MinecraftServer/]: Preparing level "world"'
LOG = '\n'.join((START, 'Done (1.0s)!'))
DETAILS = '\n'.join((DEBUG, START, 'Done (1.0s)!'))


class NativeServerLogTest(unittest.TestCase):
    def test_clean_native_startup_is_accepted(self):
        receipt = classify_server(LOG, DETAILS, True)
        self.assertEqual(receipt['native_server_config_notices'], 0)
        self.assertEqual(receipt['notices'], [])

    def test_config_change_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(LOG, DETAILS, False)

    def test_late_notice_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server('\n'.join((START, NOTICE)), DETAILS, True)

    def test_repeated_notices_are_rejected(self):
        with self.assertRaises(ValueError):
            classify_server('\n'.join((NOTICE, NOTICE, START)), DETAILS, True)

    def test_previous_target_notices_are_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(NOTICE + '\n' + LOG, DETAILS, True)

    def test_missing_debug_evidence_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(LOG, '', True)

    def test_unrelated_error_and_stderr_are_rejected(self):
        for extra in ('[Server thread/ERROR] [AnotherMod/]: Failed',
                      '[STDERR/]: java.lang.AssertionError'):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                classify_server(LOG + '\n' + extra, DETAILS, True)

    def test_wrong_logger_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(NOTICE.replace('ForgeConfig/CORE', 'OtherConfig/CORE') + '\n' + LOG, DETAILS, True)


class NativeRegistryNoticeTest(unittest.TestCase):
    def setUp(self):
        import json
        from native_registry_notices import audit_text
        root = Path(__file__).resolve().parent.parent
        self.profile = json.loads((root / "tools/native-log-profiles.json").read_text(encoding="utf-8"))["profiles"][
            "03A1DD5E363661D3D71194AC18CB1B53484841BC98A57A93E9056EC5048C5525"]
        self.check = audit_text
        prefix = "[time] [main/ERROR] [net.minecraftforge.registries.GameData/REGISTRIES]: "
        self.valid = "".join(prefix + "Unidentified mapping from registry " + registry + "\n" +
                             "\n".join("\t" + name + ": " + str(number) for name, number in entries.items()) + "\n\n"
                             for registry, entries in self.profile["mappings"].items())
        self.valid += prefix + "There are unidentified mappings in this world - we are going to attempt to process anyway\n"

    def test_exact_native_notices_and_a_clean_reload_pass(self):
        self.assertEqual(len(self.check(self.valid, self.profile)), 5)
        self.assertEqual(self.check("A clean native reload", None), [])

    def test_every_notice_boundary_is_enforced(self):
        candidates = [
            (self.valid, None),
            (self.valid.replace("minecraft:zombie_pigman: 57", "minecraft:zombie_pigman: 58"), self.profile),
            (self.valid.replace("minecraft:zombie_pigman: 57", "minecraft:unknown: 57"), self.profile),
            (self.valid.replace("minecraft:zombie_pigman: 57", "minecraft:zombie_pigman: 57\n\tminecraft:unknown: 58"), self.profile),
            (self.valid.replace("[main/ERROR]", "[Server thread/ERROR]"), self.profile),
            (self.valid.replace("GameData/REGISTRIES", "Other/REGISTRIES"), self.profile),
            (self.valid.replace("/ERROR]", "/FATAL]"), self.profile),
            (self.valid.replace("minecraft:zombie_pigman: 57", "malformed entry"), self.profile),
            (self.valid + self.valid, self.profile),
            (self.valid + "[time] [main/ERROR] [Other/]: Failed", self.profile),
            (self.valid + "[STDERR/]: hidden stack trace", self.profile),
            (self.valid + "Unable to load model", self.profile),
            (self.valid + "cascading worldgen lag", self.profile),
            (self.valid.replace("There are unidentified mappings in this world - we are going to attempt to process anyway", "MissingModsException"), self.profile),
        ]
        for index, (text, profile) in enumerate(candidates):
            with self.subTest(boundary=index), self.assertRaises(ValueError):
                self.check(text, profile)


class NativeServerFilesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.config = self.directory / 'world/serverconfig/forge-server.toml'
        self.config.parent.mkdir(parents=True)
        defaults = Path(__file__).resolve().parent.parent / 'src/bootstrapTest/defaultconfigs'
        shutil.copyfile(defaults / 'defaultconfigs/forge-server.toml', self.config)
        logs = self.directory / 'logs'
        logs.mkdir()
        (logs / 'latest.log').write_text(LOG, encoding='utf-8')
        (logs / 'debug.log').write_text(DETAILS, encoding='utf-8')

    def test_rolled_first_load_failure_stops_a_clean_reload(self):
        for name, failure in (("first.log", "[Server thread/ERROR] [Example/]: Failed"),
                              ("first.log.gz", "[STDERR/]: java.lang.AssertionError")):
            with self.subTest(name=name):
                path = self.directory / "logs" / name
                if name.endswith(".gz"):
                    with gzip.open(path, "wt", encoding="utf-8") as output:
                        output.write(failure)
                else:
                    path.write_text(failure, encoding="utf-8")
                with self.assertRaises(ValueError):
                    audit_server(self.directory, "reload")
                path.unlink()

    def test_clean_rolled_logs_are_retained_in_the_receipt(self):
        with gzip.open(self.directory / "logs/first.log.gz", "wt", encoding="utf-8") as output:
            output.write(LOG)
        self.assertEqual(audit_server(self.directory, "reload")["complete_log_files"], 3)

    def test_unchanged_defaults_pass(self):
        self.assertEqual(audit_server(self.directory, 'test')['native_server_config_notices'], 0)

    def test_changed_defaults_fail(self):
        self.config.write_text(self.config.read_text().replace('fixAdvancementLoading = true',
                                                              'fixAdvancementLoading = false'))
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')

    def test_missing_defaults_fail(self):
        self.config.unlink()
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')

    def test_crash_report_fails(self):
        crashes = self.directory / 'crash-reports'
        crashes.mkdir()
        (crashes / 'failure.txt').write_text('Simulated failure')
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')


if __name__ == '__main__':
    unittest.main()
