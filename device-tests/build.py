#!/usr/bin/env python3
"""Build device checks using the existing helper build and signing toolchain."""
from pathlib import Path
import importlib.util
import shutil

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("helper_build", ROOT.parent / "build.py")
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)
BUILD = ROOT.parent / "build/device-tests"
if BUILD.exists():
    shutil.rmtree(BUILD)
for name in ("classes", "dex"):
    (BUILD / name).mkdir(parents=True)
b.run(b.JAVA, "-jar", b.ECJ, "-8", "-classpath", str(b.ANDROID) + ":" + str(b.BUILD / "classes"),
      "-d", BUILD / "classes", ROOT / "NfcDiagnosticInstrumentation.java", ROOT / "NfcFrameworkInstrumentation.java", ROOT / "BluetoothUiInstrumentation.java",
      ROOT / "SilentWakeInstrumentation.java", ROOT / "WidgetRefreshInstrumentation.java",
      ROOT / "KeyReadinessInstrumentation.java", ROOT / "ListenerBindingInstrumentation.java",
      ROOT / "WidgetInitSequenceInstrumentation.java", ROOT / "KeySessionInstrumentation.java",
      ROOT / "ReturnProbeInstrumentation.java", ROOT / "AutoWarmInstrumentation.java", ROOT / "DailyUiInstrumentation.java", ROOT / "ControlRoutingInstrumentation.java")
b.jar_directory(BUILD / "classes", BUILD / "classes.jar")
b.run(b.JAVA, "-cp", b.TOOLS / "lib/d8.jar", "com.android.tools.r8.D8", "--lib", b.ANDROID,
      "--classpath", b.BUILD / "classes.jar", "--min-api", "26", "--output", BUILD / "dex", BUILD / "classes.jar")
b.run(b.TOOLS / "aapt2", "link", "-o", BUILD / "unsigned.apk", "--manifest", ROOT / "AndroidManifest.xml", "-I", b.ANDROID)
with b.zipfile.ZipFile(BUILD / "unsigned.apk", "a", b.zipfile.ZIP_DEFLATED) as archive:
    archive.write(BUILD / "dex/classes.dex", "classes.dex")
b.run(b.TOOLS / "zipalign", "-f", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk")
b.run(b.JAVA, "-jar", b.TOOLS / "lib/apksigner.jar", "sign", "--ks", b.KEYSTORE,
      "--ks-key-alias", b.KEY_ALIAS, "--ks-pass", "env:HIMA_SIGNING_PASSWORD",
      "--key-pass", "env:HIMA_SIGNING_PASSWORD", "--out", BUILD / "nfc-framework-tests.apk", BUILD / "aligned.apk")
b.run(b.JAVA, "-jar", b.TOOLS / "lib/apksigner.jar", "verify", BUILD / "nfc-framework-tests.apk")
print(BUILD / "nfc-framework-tests.apk")
