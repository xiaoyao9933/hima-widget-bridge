"""Build configuration checks; do not run Android tools or access signing keys."""
from pathlib import Path
import importlib.util
import os
import tempfile
import unittest
from unittest.mock import patch

BUILD_SCRIPT = Path(__file__).resolve().parents[1] / "build.py"


def load_build(environment):
    with patch.dict(os.environ, environment, clear=True):
        spec = importlib.util.spec_from_file_location("build_config_under_test", BUILD_SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module


class BuildConfigurationTest(unittest.TestCase):
    def test_standard_sdk_and_jdk_without_workspace_runtime(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = load_build({"HIMA_RUNTIME": str(root / "absent-runtime"),
                                 "JAVA_HOME": str(root / "jdk"),
                                 "ANDROID_HOME": str(root / "sdk")})
            self.assertEqual(config.JAVA, root / "jdk/bin/java")
            self.assertEqual(config.TOOLS, root / "sdk/build-tools/36.0.0")
            self.assertEqual(config.ANDROID, root / "sdk/platforms/android-34/android.jar")
            self.assertEqual(config.KEYSTORE, config.PROJECT / ".local-signing/hima-widget-bridge.p12")

    def test_explicit_tool_paths_and_signing_identity(self):
        environment = {"JAVA_HOME": "/tmp/hima-test-jdk", "HIMA_BUILD_TOOLS": "/tmp/hima-test-tools",
                       "HIMA_ANDROID_JAR": "/tmp/hima-test-android.jar", "HIMA_ECJ": "/tmp/hima-test-ecj.jar",
                       "HIMA_KEYSTORE": "/tmp/hima-test-signing.p12", "HIMA_KEY_ALIAS": "release",
                       "HIMA_KEYSTORE_PASSWORD": "configuration-test-value"}
        config = load_build(environment)
        self.assertEqual(config.TOOLS, Path(environment["HIMA_BUILD_TOOLS"]))
        self.assertEqual(config.ANDROID, Path(environment["HIMA_ANDROID_JAR"]))
        self.assertEqual(config.ECJ, Path(environment["HIMA_ECJ"]))
        self.assertEqual(config.KEYSTORE, Path(environment["HIMA_KEYSTORE"]))
        self.assertEqual(config.KEY_ALIAS, "release")
        self.assertEqual(config.PASSWORD, "configuration-test-value")

    def test_local_signing_identity_is_preserved_when_available(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key = root / "hima-widget-bridge-test.p12"
            key.touch()
            config = load_build({"HIMA_RUNTIME": str(root)})
            self.assertEqual(config.KEYSTORE, key)


if __name__ == "__main__":
    unittest.main()
