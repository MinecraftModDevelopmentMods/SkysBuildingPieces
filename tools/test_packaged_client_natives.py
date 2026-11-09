"""Reject missing or changed native files while retaining the official layout."""
from pathlib import Path
import tempfile
import unittest
import zipfile
from run_packaged_checks import verify_native_libraries


class PackagedNativesTest(unittest.TestCase):
    def test_nested_native_layout_is_verified_against_the_original_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "natives.jar"
            name = "windows/x64/org/lwjgl/glfw/glfw.dll"
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr(name, b"native fixture")
            natives = root / "natives"
            target = natives / name
            target.parent.mkdir(parents=True)
            target.write_bytes(b"native fixture")
            verify_native_libraries(natives, [artifact])
            target.write_bytes(b"changed native fixture")
            with self.assertRaisesRegex(ValueError, "checksum changed"):
                verify_native_libraries(natives, [artifact])
            target.unlink()
            with self.assertRaisesRegex(ValueError, "is missing"):
                verify_native_libraries(natives, [artifact])
            flat = natives / Path(name).name
            flat.write_bytes(b"native fixture")
            verify_native_libraries(natives, [artifact])
            flat.write_bytes(b"changed native fixture")
            with self.assertRaisesRegex(ValueError, "checksum changed"):
                verify_native_libraries(natives, [artifact])

    def test_missing_native_artifacts_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "no Windows native libraries"):
                verify_native_libraries(Path(directory), [])
