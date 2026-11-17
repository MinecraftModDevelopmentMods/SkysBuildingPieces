"""Keep disposable servers independent of host-native Netty transports."""
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest

from run_packaged_checks import prepare


class DisposableServerNetworkTest(unittest.TestCase):
    def test_server_preparation_selects_java_networking(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            probe = root / "probe.jar"
            probe.write_bytes(b"test-only probe")
            options = SimpleNamespace(mod=None, probe=probe, extra_mod=[], fixture=None,
                                      persistence_order=None, mineralogy_slabs_disabled=False)
            directory = root / "server"
            prepare(options, directory)
            properties = dict(line.split("=", 1) for line in
                              (directory / "server.properties").read_text().splitlines())
            self.assertEqual(properties["use-native-transport"], "false")
            self.assertEqual(properties["server-ip"], "127.0.0.1")
            self.assertEqual(properties["server-port"], "0")
            self.assertEqual(properties["online-mode"], "false")
