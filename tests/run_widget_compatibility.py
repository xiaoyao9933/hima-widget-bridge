"""Compile the production widget adapter with host-only Android API fixtures."""
from pathlib import Path
import importlib.util

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("helper_build", ROOT.parent / "build.py")
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)

def main():
    output = b.BUILD / "widget-compatibility-tests"
    output.mkdir(parents=True, exist_ok=True)
    production = b.PROJECT / "src/com/eric/himawidgetbridge"
    sources = sorted((ROOT / "android-stubs").rglob("*.java"))
    sources += [production / name for name in ("WidgetAdapter.java", "ControlAction.java", "NfcPolicy.java", "SlotPolicy.java", "TriggerGate.java")]
    b.run(b.JAVA, "-jar", b.ECJ, "-8", "-d", output, *sources, ROOT / "WidgetCompatibilityTest.java")
    b.run(b.JAVA, "-cp", output, "com.eric.himawidgetbridge.WidgetCompatibilityTest")

if __name__ == "__main__":
    main()
