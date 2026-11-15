#!/bin/bash
# Chay thu app Avatar tren iOS Simulator (CI): app tu bam / go chu (AVATAR_AUTOTEST=1),
# script chup man hinh theo tung buoc (doi dong "[autotest] ..." trong log), kiem tra app con song.
# Dung: Scripts/avatar-sim-test.sh <duong dan phoneME.app ban simulator> <thu muc ket qua>
set -uo pipefail
APP="$1"
OUT="$2"
BID=vn.avatar.diemky
mkdir -p "$OUT"
DEV=$(xcrun simctl list devices available -j | python3 -c "
import json,re,sys
d=json.load(sys.stdin)['devices']
c=[]
for rt,devs in d.items():
    if 'iOS' not in rt: continue
    for x in devs:
        m=re.match(r'^iPhone (\d+)( Pro)?$', x['name'])
        if m: c.append((int(m.group(1)), 0 if m.group(2) else 1, rt, x['udid'], x['name']))
if not c:
    for rt,devs in d.items():
        if 'iOS' not in rt: continue
        for x in devs:
            if x['name'].startswith('iPhone'): c.append((0,0,rt,x['udid'],x['name']))
c.sort()
print(c[-1][3] if c else '')
sys.stderr.write('chon: %s %s\n' % ((c[-1][4], c[-1][2]) if c else ('-','-')))")
if [ -z "$DEV" ]; then
  echo "khong co simulator iPhone"
  xcrun simctl list devices available
  exit 1
fi
echo "simulator $DEV"
xcrun simctl boot "$DEV" 2>/dev/null || true
xcrun simctl bootstatus "$DEV" -b
xcrun simctl install "$DEV" "$APP"
LOG="$OUT/console.log"
: > "$LOG"
# may chu gia 127.0.0.1:1 -> thu duong doi dia chi (bao loi ket noi, khong treo)
SIMCTL_CHILD_AVATAR_AUTOTEST=1 xcrun simctl launch --console-pty "$DEV" "$BID" \
  -avatar.server 127.0.0.1:1 > "$LOG" 2>&1 &
LP=$!
shot() { xcrun simctl io "$DEV" screenshot "$OUT/$1.png" >/dev/null 2>&1 || true; echo "chup $1"; }
waitfor() {
  for _ in $(seq 1 "$2"); do
    grep -aq "$1" "$LOG" 2>/dev/null && return 0
    sleep 1
  done
  echo "khong thay '$1' sau $2 giay"
  return 1
}
waitfor "\[autotest\] bat dau" 90 && sleep 7
shot 1_man_dau
waitfor "xuong o mat khau" 30 && sleep 2.6
shot 2_go_tai_khoan
waitfor "OK luu tai khoan" 20 && sleep 1.8
shot 3_menu_choi_tiep
waitfor "OK nhom thanh pho" 20 && sleep 1.2
shot 4_chon_thanh_pho
waitfor "OK thanh pho" 20 && sleep 7
shot 5_ket_noi
waitfor "\[autotest\] xong" 40
shot 6_cuoi
if pgrep -f "phoneME.app/phoneME" >/dev/null 2>&1; then
  echo "app con chay" > "$OUT/trang_thai_app.txt"
else
  echo "APP DA SAP / THOAT" > "$OUT/trang_thai_app.txt"
fi
for f in "$HOME"/Library/Logs/DiagnosticReports/phoneME*; do
  [ -f "$f" ] && cp "$f" "$OUT/" || true
done
kill $LP 2>/dev/null || true
xcrun simctl terminate "$DEV" "$BID" 2>/dev/null || true
ls -la "$OUT"
cat "$OUT/trang_thai_app.txt"
grep -a "\[autotest\]" "$LOG" | tail -40
tail -60 "$LOG"
