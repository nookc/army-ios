#!/bin/bash
# Chay app Avatar "thuan" tren iOS Simulator: tu dien may chu, dang nhap, chon thanh pho, ket noi toi may chu gia
# (127.0.0.1:19129, ghi lai byte client gui) - chung minh go chu + chuyen huong ket noi 127.0.0.1 deu chay.
# Ket qua: native/build/avatar-ios/test (anh chup, log, trang thai)
set -uo pipefail
N=$(cd "$(dirname "$0")/.." && pwd)
APP=$N/build/avatar-ios/sim/Avatar.app
OUT=$N/build/avatar-ios/test
BID=vn.avatar.diemky
mkdir -p "$OUT"

# ---- may chu gia: nhan ket noi, ghi byte dau tien client gui
cat > "$OUT/may_chu_gia.py" <<'PY'
import socket, sys, time
s = socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", 19129))
s.listen(8)
log = open(sys.argv[1], "a")
while True:
    c, a = s.accept()
    c.settimeout(6)
    data = b""
    try:
        while len(data) < 64:
            d = c.recv(64)
            if not d:
                break
            data += d
    except Exception:
        pass
    log.write("ket noi tu %s:%d nhan %d byte: %s\n" % (a[0], a[1], len(data), data[:24].hex()))
    log.flush()
    time.sleep(4)
    c.close()
PY
: > "$OUT/may_chu_gia.txt"
python3 "$OUT/may_chu_gia.py" "$OUT/may_chu_gia.txt" &
FAKE=$!

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
if [ -z "$DEV" ]; then echo "khong co simulator iPhone"; xcrun simctl list devices available; kill $FAKE; exit 1; fi
echo "simulator $DEV"
xcrun simctl boot "$DEV" 2>/dev/null || true
xcrun simctl bootstatus "$DEV" -b
xcrun simctl spawn "$DEV" defaults write -g AppleLanguages -array vi en || true
xcrun simctl spawn "$DEV" defaults write -g AppleLocale -string vi_VN || true
xcrun simctl install "$DEV" "$APP"
xcrun simctl launch --console-pty "$DEV" "$BID" -autotest -avatar.server 127.0.0.1:19129 > "$OUT/console.log" 2>&1 &
LP=$!
# simulator moi bat lan dau rat cham (Data Migration, System App): doi app thuc su khoi dong roi moi tinh gio tu kiem thu
for i in $(seq 1 600); do
  sleep 1
  grep -q "khoi dong Avatar" "$OUT/console.log" 2>/dev/null && break
done
echo "app khoi dong sau ${i} giay"
for i in $(seq 1 240); do
  sleep 1
  grep -q "\[autotest\] xong" "$OUT/console.log" 2>/dev/null && break
done
sleep 2
xcrun simctl io "$DEV" screenshot "$OUT/man_hinh_simulator.png" || true
if pgrep -f "Avatar.app/Avatar" >/dev/null 2>&1; then
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
for f in "$HOME"/Library/Logs/DiagnosticReports/Avatar*; do
  [ -f "$f" ] && cp "$f" "$OUT/" || true
done
kill $LP 2>/dev/null || true
xcrun simctl terminate "$DEV" "$BID" 2>/dev/null || true
kill $FAKE 2>/dev/null || true
ls -la "$OUT"
cat "$OUT/trang_thai_app.txt"
cat "$OUT/autotest.txt" 2>/dev/null || echo "khong co autotest.txt"
echo "== may chu gia:"
cat "$OUT/may_chu_gia.txt"
grep -a "\[mang\]\|\[app\]\|SAP\|NGOAI LE" "$OUT/log_app.txt" 2>/dev/null | tail -20
tail -40 "$OUT/console.log"
if grep -q "e50000" "$OUT/may_chu_gia.txt"; then
  echo "KET QUA: client da ket noi va gui goi dang nhap toi may chu (OK)" | tee "$OUT/KET_QUA_KET_NOI.txt"
else
  echo "KET QUA: CHUA thay goi tin cua client o may chu gia" | tee "$OUT/KET_QUA_KET_NOI.txt"
fi
