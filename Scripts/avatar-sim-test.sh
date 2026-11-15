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
# may chu gia: nghe cong 19129 tren may CI, ghi lai byte client gui toi -> chung minh app
# doi 127.0.0.1:19128 cua client sang dia chi cai trong app (localhost:19129) va ket noi duoc
python3 - "$OUT/may_chu_gia.txt" <<'PY' &
import socket, sys, time
out = open(sys.argv[1], 'w')
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(('127.0.0.1', 19129)); s.listen(5); s.settimeout(240)
out.write('dang nghe 127.0.0.1:19129\n'); out.flush()
try:
    while True:
        c, a = s.accept()
        c.settimeout(10)
        try:
            d = c.recv(4096)
        except Exception as e:
            d = b''
        out.write('%s ket noi tu %s, nhan %d byte: %s\n' % (time.strftime('%H:%M:%S'), a, len(d), d[:64].hex()))
        out.flush()
        c.close()
except Exception as e:
    out.write('het: %s\n' % e)
PY
FAKE=$!
SIMCTL_CHILD_AVATAR_AUTOTEST=1 xcrun simctl launch --console-pty "$DEV" "$BID" \
  -avatar.server localhost:19129 > "$LOG" 2>&1 &
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
waitfor "\[autotest\] game chay" 150 && sleep 4
shot man_hinh_game
waitfor "\[autotest\] luu 3_go_xong" 60
shot man_hinh_go_chu
waitfor "\[autotest\] xong" 90
shot man_hinh_cuoi
C=$(xcrun simctl get_app_container "$DEV" "$BID" data 2>/dev/null)
if [ -n "$C" ]; then
  cp "$C"/Documents/autotest/*.png "$OUT/" 2>/dev/null || true
fi
if pgrep -f "phoneME.app/phoneME" >/dev/null 2>&1; then
  echo "app con chay" > "$OUT/trang_thai_app.txt"
else
  echo "APP DA SAP / THOAT" > "$OUT/trang_thai_app.txt"
fi
for f in "$HOME"/Library/Logs/DiagnosticReports/phoneME*; do
  [ -f "$f" ] && cp "$f" "$OUT/" || true
done
kill $LP $FAKE 2>/dev/null || true
echo "--- may chu gia ---"; cat "$OUT/may_chu_gia.txt" 2>/dev/null
xcrun simctl terminate "$DEV" "$BID" 2>/dev/null || true
ls -la "$OUT"
cat "$OUT/trang_thai_app.txt"
grep -a "\[autotest\]" "$LOG" | tail -40
tail -60 "$LOG"
