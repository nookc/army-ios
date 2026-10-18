#!/bin/bash
# Chay app tren iOS Simulator, tu bam phim (-autotest), lay anh chup + log ra native/build/ios/test
set -uo pipefail
N=$(cd "$(dirname "$0")/.." && pwd)
APP=$N/build/ios/sim/Army2Coffee.app
OUT=$N/build/ios/test
BID=vn.army2coffee.native
mkdir -p "$OUT"
DEV=$(xcrun simctl list devices available -j | python3 -c "
import json,sys
d=json.load(sys.stdin)['devices']
best=None
for rt,devs in d.items():
    if 'iOS' not in rt: continue
    for x in devs:
        if x['name'].startswith('iPhone') and 'Pro Max' not in x['name']:
            best=x['udid']
print(best or '')")
if [ -z "$DEV" ]; then echo "khong co simulator iPhone"; xcrun simctl list devices available; exit 1; fi
echo "simulator $DEV"
xcrun simctl boot "$DEV" 2>/dev/null || true
xcrun simctl bootstatus "$DEV" -b
xcrun simctl install "$DEV" "$APP"
xcrun simctl launch --console-pty "$DEV" "$BID" -autotest > "$OUT/console.log" 2>&1 &
LP=$!
sleep 75
xcrun simctl io "$DEV" screenshot "$OUT/man_hinh_simulator.png" || true
C=$(xcrun simctl get_app_container "$DEV" "$BID" data 2>/dev/null)
if [ -n "$C" ]; then
  cp "$C"/Documents/rms/*.png "$OUT/" 2>/dev/null || true
  cp "$C"/Documents/rms/autotest.txt "$OUT/" 2>/dev/null || true
fi
kill $LP 2>/dev/null || true
xcrun simctl terminate "$DEV" "$BID" 2>/dev/null || true
ls -la "$OUT"
cat "$OUT/autotest.txt" 2>/dev/null || echo "khong co autotest.txt"
tail -50 "$OUT/console.log"
