#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
A = f"{{{ANDROID_NS}}}"


def main() -> int:
    repo = Path(__file__).resolve().parents[2]
    patcher = repo / "tools" / "bugjaeger_mod" / "patch_defaults.py"

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "res" / "xml").mkdir(parents=True)
        (root / "res" / "values").mkdir(parents=True)

        (root / "res" / "xml" / "prefs.xml").write_text(
            '''<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
<SwitchPreference android:key="key.reconnect.last.wifi.targets" android:defaultValue="false" />
<SwitchPreference android:key="key.autofetch.connection.params" android:defaultValue="false" />
<SwitchPreference android:key="key.autofetch.pairing.info" android:defaultValue="false" />
<SwitchPreference android:key="key.start.adb.server.foreground" android:defaultValue="false" />
<SwitchPreference android:key="unrelated" android:defaultValue="false" />
</PreferenceScreen>''',
            encoding="utf-8",
        )
        (root / "res" / "values" / "strings.xml").write_text(
            '<resources><string name="app_name">Bugjaeger</string></resources>',
            encoding="utf-8",
        )

        proc = subprocess.run(
            [sys.executable, str(patcher), str(root)],
            check=True,
            capture_output=True,
            text=True,
        )
        print(proc.stdout)

        tree = ET.parse(root / "res" / "xml" / "prefs.xml")
        values = {
            node.attrib.get(A + "key"): node.attrib.get(A + "defaultValue")
            for node in tree.getroot().iter()
            if node.attrib.get(A + "key")
        }
        for key in (
            "key.reconnect.last.wifi.targets",
            "key.autofetch.connection.params",
            "key.autofetch.pairing.info",
            "key.start.adb.server.foreground",
        ):
            assert values[key] == "true", (key, values[key])
        assert values["unrelated"] == "false"

        strings = ET.parse(root / "res" / "values" / "strings.xml")
        app_name = next(
            n.text for n in strings.getroot().findall("string") if n.attrib.get("name") == "app_name"
        )
        assert app_name == "CUSTOMROM ADB"

    print("PATCH_DEFAULTS_TEST=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
