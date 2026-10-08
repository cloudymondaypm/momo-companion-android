#!/usr/bin/env bash
# Keep the emulator alive while collecting UI evidence, including on failure.
set +e
./app-phone/gradlew -p app-phone connectedDebugAndroidTest
test_result=$?
mkdir -p phone-preview
adb exec-out run-as space.spacecloud.xiaozhi.fold5 cat files/phone-momo-depth.png > phone-preview/phone-momo-depth.png
python3 - <<'PY'
import base64
from pathlib import Path
p = Path("phone-preview/phone-momo-depth.png")
if p.exists() and p.stat().st_size:
    print("MOMO_PREVIEW_BASE64=" + base64.b64encode(p.read_bytes()).decode())
else:
    print("No emulator preview available")
for report in Path("app-phone/app/build/outputs/androidTest-results/connected").rglob("*.xml"):
    print(report.read_text()[:30000])
PY
exit "$test_result"
