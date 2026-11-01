"""Negative controls for Forge 31 server completion and native settings."""

from pathlib import Path
import shutil
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
