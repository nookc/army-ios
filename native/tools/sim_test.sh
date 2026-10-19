#!/bin/bash
# Chay app tren iOS Simulator, tu bam phim/cham (-autotest), lay anh chup + log ra native/build/ios/test
#  - chon iPhone co tai tho (giong may nguoi choi), ngon ngu + ban phim tieng Viet
#  - sau khi chay: kiem tra app con song, chep log.txt / crash.txt / bao cao sap cua he thong
set -uo pipefail
N=$(cd "$(dirname "$0")/.." && pwd)
APP=$N/build/ios/sim/Army2Coffee.app
OUT=$N/build/ios/test
BID=vn.army2coffee.native
mkdir -p "$OUT"
DEV=$(xcrun simctl list devices available -j | python3 -c "
import json,re,sys
d=json.load(sys.stdin)['devices']
cands=[]
for rt,devs in d.items():
    if 'iOS' not in rt: continue
    for x in devs:
        m=re.match(r'^iPhone (\d+)( Pro)?$', x['name'])
        if m: cands.append((int(m.group(1)), 0 if m.group(2) else 1, rt, x['udid'], x['name']))
if not cands:
    for rt,devs in d.items():
        if 'iOS' not in rt: continue
        for x in devs:
            if x['name'].startswith('iPhone'): cands.append((0,0,rt,x['udid'],x['name']))
cands.sort()
print(cands[-1][3] if cands else '')
sys.stderr.write('chon: %s\n' % (cands[-1][4] if cands else '-'))")
if [ -z "$DEV" ]; then echo "khong co simulator iPhone"; xcrun simctl list devices available; exit 1; fi
echo "simulator $DEV"
xcrun simctl boot "$DEV" 2>/dev/null || true
xcrun simctl bootstatus "$DEV" -b
# tieng Viet (ngon ngu, vung, ban phim Telex) nhu may that o VN
xcrun simctl spawn "$DEV" defaults write -g AppleLanguages -array vi en || true
xcrun simctl spawn "$DEV" defaults write -g AppleLocale -string vi_VN || true
xcrun simctl spawn "$DEV" defaults write -g AppleKeyboards -array "vi_VN@sw=Vietnamese;hw=Automatic" "en_US@sw=QWERTY;hw=Automatic" "emoji@sw=Emoji" || true
xcrun simctl spawn "$DEV" defaults write -g AppleKeyboardsExpanded -int 1 || true
xcrun simctl install "$DEV" "$APP"
xcrun simctl launch --console-pty "$DEV" "$BID" -autotest > "$OUT/console.log" 2>&1 &
LP=$!
for i in $(seq 1 110); do
  sleep 1
  grep -q "\[autotest\] xong" "$OUT/console.log" 2>/dev/null && break
done
sleep 3
xcrun simctl io "$DEV" screenshot "$OUT/man_hinh_simulator.png" || true
if xcrun simctl spawn "$DEV" launchctl list 2>/dev/null | grep -q "$BID"; then
  echo "app con chay" > "$OUT/trang_thai_app.txt"
else
  echo "APP DA SAP / THOAT" > "$OUT/trang_thai_app.txt"
fi
C=$(xcrun simctl get_app_container "$DEV" "$BID" data 2>/dev/null)
if [ -n "$C" ]; then
  cp "$C"/Documents/rms/*.png "$OUT/" 2>/dev/null || true
  cp "$C"/Documents/rms/autotest.txt "$OUT/" 2>/dev/null || true
  cp "$C"/Documents/log.txt "$OUT/log_app.txt" 2>/dev/null || true
  cp "$C"/Documents/crash.txt "$OUT/crash_app.txt" 2>/dev/null || true
fi
# bao cao sap cua he thong (neu co)
for f in "$HOME"/Library/Logs/DiagnosticReports/Army2Coffee*; do
  [ -f "$f" ] && cp "$f" "$OUT/" || true
done
kill $LP 2>/dev/null || true
xcrun simctl terminate "$DEV" "$BID" 2>/dev/null || true
ls -la "$OUT"
cat "$OUT/trang_thai_app.txt"
cat "$OUT/autotest.txt" 2>/dev/null || echo "khong co autotest.txt"
grep -a "\[gc\]\|\[app\]\|SAP\|NGOAI LE" "$OUT/log_app.txt" 2>/dev/null | tail -20
tail -40 "$OUT/console.log"
