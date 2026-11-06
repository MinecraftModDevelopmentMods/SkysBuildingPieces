"""Negative controls for Forge 47 server completion and native settings."""

from pathlib import Path
import shutil
import gzip
import tempfile
import unittest

from native_client_log import audit_server, classify_server
from native_registry_notices import EMPTY_CAPABILITIES, audit_biome_proof, audit_text, validate_registry_proofs


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
                      '[STDERR/]: java.lang.AssertionError',
                      'java.lang.AssertionError: native fixture failed',
                      'java.lang.IllegalStateException: native fixture failed'):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                classify_server(LOG + '\n' + extra, DETAILS, True)

    def test_empty_capabilities_need_the_exact_fixture_count_and_logger(self):
        profile = {'mappings': {}, 'empty_capabilities_count': 2}
        notice = '[time] [Server thread/ERROR] [net.minecraft.world.level.storage.DimensionDataStorage/]: '
        notice += EMPTY_CAPABILITIES + '\n'
        self.assertEqual(audit_text(notice * 2, profile), [EMPTY_CAPABILITIES] * 2)
        self.assertEqual(audit_text('A clean reload', profile), [])
        for log, allowance in ((notice, profile), (notice * 3, profile), (notice, None),
                               (notice.replace('MapLike[{}]', 'MapLike[{items:[]}]') * 2, profile),
                               (notice.replace('DimensionDataStorage', 'AnotherMod') * 2, profile)):
            with self.subTest(log=log), self.assertRaises(ValueError):
                audit_text(log, allowance)

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
        self.assertEqual(len(self.check(self.valid, self.profile)), len(self.profile["mappings"]) + 1)
        self.assertEqual(self.check("A clean native reload", None), [])

    def test_compact_native_headers_keep_the_same_registry_boundaries(self):
        compact = self.valid.replace(" [net.minecraftforge.registries.GameData/REGISTRIES]", "")
        self.assertEqual(len(self.check(compact, self.profile)), len(self.profile["mappings"]) + 1)
        with self.assertRaises(ValueError):
            self.check(compact.replace("[main/ERROR]", "[Server thread/ERROR]"), self.profile)
        with self.assertRaises(ValueError):
            self.check(compact + "[time] [main/ERROR]: An unrelated failure", self.profile)

    def test_numeric_resource_names_still_require_an_exact_saved_id(self):
        profile = {"mappings": {"minecraft:painting_variant": {"minecraft:aztec2": 3}}}
        log = ("[time] [main/ERROR]: Unidentified mapping from registry minecraft:painting_variant\n"
               "\tminecraft:aztec2: 3\n\n"
               "[time] [main/ERROR]: There are unidentified mappings in this world - "
               "we are going to attempt to process anyway\n")
        self.assertEqual(len(self.check(log, profile)), 2)
        with self.assertRaises(ValueError):
            self.check(log.replace("minecraft:aztec2: 3", "minecraft:aztec2: 4"), profile)

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


class NativeBiomeProofTest(unittest.TestCase):
    def test_native_registry_proofs_reject_changed_sources_uses_and_cells(self):
        import copy
        import hashlib
        import json
        root = Path(__file__).resolve().parent.parent
        profile = json.loads((root / "tools/native-log-profiles.json").read_text(encoding="utf-8"))["profiles"][
            "85432F3E0FED85EF363ED17A9D803115EB3BABB5E81AE82EE5729E55F0D55D49"]
        validate_registry_proofs(root, profile)
        for key, field, value in (("retired_poi_contract", "saved_uses", {"minecraft:nitwit": 1, "minecraft:unemployed": 0}),
                                  ("static_biome_contract", "checked_cells", 528383),
                                  ("static_biome_contract", "native_biome_contract_sha256", "wrong"),
                                  ("retired_poi_contract", "source_world_sha256", "wrong")):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as directory:
                candidate = copy.deepcopy(profile)
                for receipt_key in ("retired_poi_contract", "static_biome_contract",
                                    "native_block_rename_contract", "forge50_unused_registry_contract"):
                    if receipt_key not in profile:
                        continue
                    original = root / profile[receipt_key]["path"]
                    proof = json.loads(original.read_text(encoding="utf-8"))
                    if receipt_key == key:
                        proof[field] = value
                    target = Path(directory) / (receipt_key + ".json")
                    target.write_text(json.dumps(proof), encoding="utf-8")
                    candidate[receipt_key].update(path=target.name,
                        sha256=hashlib.sha256(target.read_bytes()).hexdigest().upper())
                with self.assertRaises(ValueError):
                    validate_registry_proofs(directory, candidate)

    def test_complete_native_volume_is_required(self):
        profile = {"native_biome_contract": {"source": "1.17.1", "checked_cells": 982016,
                                             "renamed_cells": 18240}}
        marker = "BUILDING_PIECES_NATIVE_BIOME_PASS source=1.17.1 cells=982016 renamed=18240"
        audit_biome_proof("[Server thread/INFO]: " + marker, profile)
        for text in ["", marker.replace("982016", "982015"), marker.replace("18240", "182400"),
                     marker.replace("1.17.1", "1.16.5"), marker + " incomplete"]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                audit_biome_proof(text, profile)

    def test_no_biome_notices_need_no_biome_exception(self):
        audit_biome_proof("A normal fresh world", None)


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
        original = self.config.read_text()
        changed = original.replace('advertiseDedicatedServerToLan = true',
                                   'advertiseDedicatedServerToLan = false')
        self.assertNotEqual(original, changed, "The negative control must alter a native setting")
        self.config.write_text(changed)
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


class ForgeWindowsReportTests(unittest.TestCase):
    def setUp(self):
        self.report = "\n".join((
            "[05:22:40] [main/ERROR] [oshi.driver.windows.registry.HkeyPerformanceDataUtil/]: "
            "Unable to locate English counter names in registry Perflib 009. Counters may need to be rebuilt: ",
            "com.sun.jna.platform.win32.Win32Exception: The parameter is incorrect.",
            "\tat com.sun.jna.platform@5.17.0/com.sun.jna.platform.win32.Advapi32Util.registryGetStringArray",
            "\tat com.github.oshi@6.9.0/oshi.driver.windows.registry.HkeyPerformanceDataUtil.mapCounterIndicesFromRegistry",
            "\tat TRANSFORMER/minecraft@26.3/net.minecraft.SystemReport.putSoftware",
            "\tat SECURE-BOOTSTRAP/net.minecraftforge.fmlloader@26.3-66.0.2/Forge",
            "[05:22:46] [Server thread/INFO]: Done (1.5s)!",
            "",
        ))

    def test_the_controlled_report_is_scoped_to_windows(self):
        from native_windows_log import forge_windows_log

        self.assertEqual(forge_windows_log(self.report, enabled=False), self.report)
        result = forge_windows_log(self.report, enabled=True)
        self.assertNotIn("/ERROR]", result)
        self.assertIn("Done (1.5s)!", result)

    def test_a_different_exception_or_loader_is_still_an_error(self):
        from native_windows_log import forge_windows_log

        for changed in (
            self.report.replace("The parameter is incorrect.", "Access is denied."),
            self.report.replace("26.3-66.0.2/Forge", "26.3-66.0.3/Forge"),
            self.report + self.report,
        ):
            self.assertEqual(forge_windows_log(changed, enabled=True), changed)

    def test_an_unrelated_error_is_never_removed(self):
        from native_windows_log import forge_windows_log

        failure = "[05:22:47] [Server thread/ERROR]: Failed to save a chunk"
        self.assertIn(failure, forge_windows_log(self.report + failure, enabled=True))


if __name__ == '__main__':
    unittest.main()
