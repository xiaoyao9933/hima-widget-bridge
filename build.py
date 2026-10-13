#!/usr/bin/env python3
"""Build a universal APK using standard Android SDK tools or the local pinned runtime."""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parent
WORKSPACE = PROJECT.parent
RUNTIME = Path(os.environ.get("HIMA_RUNTIME", WORKSPACE / ".analysis-tools/runtime"))
BUNDLED_JAVA_HOME = RUNTIME / "jdk-17.0.20.1+1-jre/Contents/Home"
SYSTEM_JAVA_HOME = Path(shutil.which("java") or "java").resolve().parent.parent
JAVA_HOME = Path(os.environ.get("JAVA_HOME", BUNDLED_JAVA_HOME if BUNDLED_JAVA_HOME.is_dir() else SYSTEM_JAVA_HOME))
JAVA = JAVA_HOME / "bin/java"
SDK = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", PROJECT / ".android-sdk")))
LOCAL_ECJ = RUNTIME / "ecj-3.37.0.jar"
LOCAL_TOOLS = RUNTIME / "android-16"
LOCAL_ANDROID = RUNTIME / "platform34/android-34-ext12/android.jar"
ECJ = Path(os.environ.get("HIMA_ECJ", LOCAL_ECJ if LOCAL_ECJ.is_file() else PROJECT / ".build-tools/ecj-3.37.0.jar"))
TOOLS = Path(os.environ.get("HIMA_BUILD_TOOLS", LOCAL_TOOLS if LOCAL_TOOLS.is_dir() else SDK / "build-tools/36.0.0"))
ANDROID = Path(os.environ.get("HIMA_ANDROID_JAR", LOCAL_ANDROID if LOCAL_ANDROID.is_file() else SDK / "platforms/android-34/android.jar"))
BUILD = PROJECT / "build"
LOCAL_KEYSTORE = RUNTIME / "hima-widget-bridge-test.p12"
KEYSTORE = Path(os.environ.get("HIMA_KEYSTORE", LOCAL_KEYSTORE if LOCAL_KEYSTORE.is_file() else PROJECT / ".local-signing/hima-widget-bridge.p12"))
KEY_ALIAS = os.environ.get("HIMA_KEY_ALIAS", "widget-helper")
PASSWORD = os.environ.get("HIMA_KEYSTORE_PASSWORD", "local-widget-validation-only")

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=PROJECT,
                   env={**os.environ, "HIMA_SIGNING_PASSWORD": PASSWORD})

def jar_directory(source, target):
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for item in sorted(source.rglob("*.class")):
            archive.write(item, item.relative_to(source).as_posix())

def main():
    required = [JAVA, ECJ, ANDROID, TOOLS / "aapt2", TOOLS / "zipalign",
                TOOLS / "lib/d8.jar", TOOLS / "lib/apksigner.jar"]
    if not KEYSTORE.exists(): required.append(JAVA_HOME / "bin/keytool")
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise SystemExit("Missing build tools:\n" + "\n".join(missing)
                         + "\nSee README.md: JAVA_HOME, ANDROID_HOME and HIMA_ECJ.")
    for name in ("classes", "dex", "generated", "test-classes"):
        path = BUILD / name
        if path.exists():
            shutil.rmtree(path)
        path.mkdir(parents=True)
    policy = [PROJECT / "src/com/eric/himawidgetbridge" / name
              for name in ("ControlAction.java", "TriggerGate.java", "SlotPolicy.java", "NfcPolicy.java", "NfcEnrollmentPolicy.java", "BluetoothPolicy.java", "KeyReadinessPolicy.java", "ListenerPreparationPolicy.java", "KeySessionPolicy.java", "AutoWarmPolicy.java", "OperationLease.java", "ForegroundHistory.java")]
    run(JAVA, "-jar", ECJ, "-8", "-d", BUILD / "test-classes", *policy,
        PROJECT / "tests/ControlActionTest.java", PROJECT / "tests/TriggerGateTest.java", PROJECT / "tests/NfcPolicyTest.java", PROJECT / "tests/BluetoothPolicyTest.java",
        PROJECT / "tests/KeyReadinessPolicyTest.java", PROJECT / "tests/ListenerPreparationPolicyTest.java",
        PROJECT / "tests/KeySessionPolicyTest.java", PROJECT / "tests/NewKeySessionRegressionTest.java",
        PROJECT / "tests/AutoWarmPolicyTest.java", PROJECT / "tests/AutoWarmRecordTest.java",
        PROJECT / "tests/NfcEnrollmentPolicyTest.java")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.ControlActionTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.TriggerGateTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.NfcPolicyTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.NfcEnrollmentPolicyTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.BluetoothPolicyTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.KeyReadinessPolicyTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.ListenerPreparationPolicyTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.KeySessionPolicyTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.NewKeySessionRegressionTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.AutoWarmPolicyTest")
    run(JAVA, "-cp", BUILD / "test-classes", "com.eric.himawidgetbridge.AutoWarmRecordTest")
    run(sys.executable, PROJECT / "tests/run_widget_compatibility.py")
    run(TOOLS / "aapt2", "compile", "--dir", PROJECT / "res", "-o", BUILD / "resources.zip")
    run(TOOLS / "aapt2", "link", "-o", BUILD / "unsigned.apk", "--manifest",
        PROJECT / "AndroidManifest.xml", "-I", ANDROID, "--java", BUILD / "generated",
        BUILD / "resources.zip")
    sources = sorted((PROJECT / "src").rglob("*.java")) + sorted((BUILD / "generated").rglob("*.java"))
    run(JAVA, "-jar", ECJ, "-8", "-classpath", ANDROID, "-d", BUILD / "classes", *sources)
    jar_directory(BUILD / "classes", BUILD / "classes.jar")
    run(JAVA, "-cp", TOOLS / "lib/d8.jar", "com.android.tools.r8.D8", "--lib", ANDROID,
        "--min-api", "26", "--output", BUILD / "dex", BUILD / "classes.jar")
    with zipfile.ZipFile(BUILD / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as archive:
        archive.write(BUILD / "dex/classes.dex", "classes.dex")
    run(TOOLS / "zipalign", "-P", "16", "-f", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk")
    if not KEYSTORE.exists():
        KEYSTORE.parent.mkdir(parents=True, exist_ok=True)
        run(JAVA_HOME / "bin/keytool", "-genkeypair", "-keystore", KEYSTORE,
            "-storetype", "PKCS12", "-storepass:env", "HIMA_SIGNING_PASSWORD", "-keypass:env", "HIMA_SIGNING_PASSWORD",
            "-alias", KEY_ALIAS, "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "3650", "-dname", "CN=Local Widget Validation")
        KEYSTORE.chmod(0o600)
    version = ET.parse(PROJECT / "AndroidManifest.xml").getroot().get(
        "{http://schemas.android.com/apk/res/android}versionName")
    output = BUILD / ("hima-widget-bridge-" + version + ".apk")
    run(JAVA, "-jar", TOOLS / "lib/apksigner.jar", "sign", "--ks", KEYSTORE,
        "--ks-key-alias", KEY_ALIAS, "--ks-pass", "env:HIMA_SIGNING_PASSWORD",
        "--key-pass", "env:HIMA_SIGNING_PASSWORD", "--out", output, BUILD / "aligned.apk")
    run(JAVA, "-jar", TOOLS / "lib/apksigner.jar", "verify", "--verbose", output)
    run(TOOLS / "zipalign", "-c", "-P", "16", "4", output)
    print("APK:", output)
    print("SHA256:", hashlib.sha256(output.read_bytes()).hexdigest())
    delivery = PROJECT / "dist"
    delivery.mkdir(exist_ok=True)
    shutil.copyfile(output, delivery / output.name)

if __name__ == "__main__":
    main()
