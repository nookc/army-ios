#!/bin/bash
# Chay thu APK Avatar "thuan" tren emulator CI bang thao tac THAT (adb input): hop cai dat may chu, cham man game,
# go chu bang ban phim (adb input text), Enter, nut Aa (ban phim Android); ket noi toi may chu gia tren may CI
# (emulator thay may CI o 10.0.2.2) de chung minh dang nhap + chuyen huong 127.0.0.1 deu chay.
#   bash native/tools/avatar_android_test.sh      (chay tu goc repo, emulator da bat)
PKG=vn.avatar.diemky
O=native/build/avatar-android/test
mkdir -p "$O"
say() { echo "== $*"; }
cap() { adb exec-out screencap -p > "$O/$1.png"; say "chup $1"; }
alog() { adb logcat -d -s Avatar:I | tr -d '\r'; }

# ---- may chu gia (tren may CI): nhan ket noi, ghi byte client gui
cat > "$O/may_chu_gia.py" <<'PY'
import socket, sys, time
s = socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("0.0.0.0", 19129))
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
: > "$O/may_chu_gia.txt"
python3 "$O/may_chu_gia.py" "$O/may_chu_gia.txt" &
FAKE=$!

APK=${APK:-native/build/avatar-android/Avatar-test.apk}
adb shell getprop ro.build.version.release > "$O/android.txt"
if ! adb install -r "$APK" > "$O/cai_apk.txt" 2>&1; then
  echo "CAI APK LOI: $(tail -2 "$O/cai_apk.txt")" > "$O/trang_thai_app.txt"
  kill $FAKE
  exit 0
fi
adb shell getprop ro.product.cpu.abi > "$O/abi.txt"

# 1) mo lan dau: hop cai dat (may chu, co giao dien)
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 4
cap a0_cai_dat
adb shell am force-stop $PKG
sleep 1

# 2) mo voi may chu cua CI (nhu nguoi choi bam "Vao game")
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity --es server 10.0.2.2:19129
B=""
for i in $(seq 1 25); do
  B=$(alog | grep -o "\[bocuc\].*" | tail -1)
  [ -n "$B" ] && break
  sleep 1
done
say "$B"
if [ -z "$B" ]; then
  echo "KHONG THAY BO CUC" > "$O/trang_thai_app.txt"
  adb logcat -d > "$O/logcat.txt"
  kill $FAKE
  exit 0
fi
# [bocuc] canvas L T R B aa X Y game W H
set -- $B
CL=$3; CT=$4; CR=$5; CB=$6; AX=$8; AY=$9; GW=${11}; GH=${12}
say "khung game $CL,$CT - $CR,$CB; Aa $AX,$AY; game ${GW}x${GH}"
tapg() {
  local x=$((CL + $1 * (CR - CL) / GW)) y=$((CT + $2 * (CB - CT) / GH))
  adb shell input swipe $x $y $x $y 100
}
pid_of() {
  local p
  p=$(adb shell pidof $PKG 2>/dev/null | tr -d '\r')
  [ -z "$p" ] && p=$(adb shell ps -A 2>/dev/null | tr -d '\r' | awk -v k=$PKG '$NF == k {print $2}' | head -1)
  echo "$p"
}

sleep 12
cap a1_mo_dau
# toc do ve, RAM, CPU (man mo dau co may bay, xe chay)
PID=$(pid_of)
adb shell dumpsys gfxinfo $PKG reset > /dev/null
sleep 8
adb shell dumpsys gfxinfo $PKG > "$O/gfxinfo.txt"
adb shell dumpsys meminfo $PKG > "$O/meminfo.txt"
adb shell top -b -n 2 -d 4 -p "$PID" > "$O/cpu.txt" 2>&1

# 3) "Doi tai khoan" -> o dang nhap, go ten + mat khau bang ban phim (adb input text)
tapg $((GW / 2)) $((GH / 2 + 5)); sleep 2; cap a2_o_dang_nhap
adb shell input text test1; sleep 1.5; cap a3_go_ten
tapg $((GW / 2 + 28)) $((GH / 2 + 4)); sleep 1
adb shell input text 123456; sleep 1.5; cap a4_go_mat_khau
# 4) Enter = chon: luu tai khoan -> "Choi tiep" -> thanh pho -> thanh pho con -> ket noi
adb shell input keyevent 66; sleep 2; cap a5_choi_tiep
adb shell input keyevent 66; sleep 2
adb shell input keyevent 66; sleep 2; cap a6_thanh_pho
adb shell input keyevent 66; sleep 6; cap a7_ket_noi

# 5) nut Aa: bat ban phim Android, khung game thu nho phia tren ban phim
adb shell input tap "$AX" "$AY"; sleep 3; cap a8_ban_phim_aa
adb shell input tap "$AX" "$AY"; sleep 2

# ---- ban "Avatar 250 HD" (ngang + net cao): cai song song, mo, chup vai man, do toc do ve
HDAPK=native/build/avatar-android/AvatarHD-test.apk
if [ -f "$HDAPK" ]; then
  HPKG=vn.avatar.diemky.hd
  adb shell am force-stop $PKG
  if adb install -r "$HDAPK" > "$O/hd_cai_apk.txt" 2>&1; then
    adb logcat -c
    adb shell am start -W -n $HPKG/vn.avatar.diemky.MainActivity --es server 10.0.2.2:19129
    HB=""
    for i in $(seq 1 30); do
      HB=$(alog | grep -o "\[bocuc\].*" | tail -1)
      [ -n "$HB" ] && break
      sleep 1
    done
    say "HD: $HB"
    sleep 10
    cap hd1_mo_dau
    adb shell dumpsys gfxinfo $HPKG reset > /dev/null
    if [ -n "$HB" ]; then
      set -- $HB
      HL=$3; HT=$4; HR=$5; HBT=$6; HW=${11}; HH=${12}
      htap() { adb shell input tap $((HL + $1 * (HR - HL) / HW)) $((HT + $2 * (HBT - HT) / HH)); }
      # "Choi moi" (giua hop) -> thanh pho -> vao game voi may chu gia (khong tra loi): chi can thay giao dien ngang ve dung
      htap $((HW / 2)) $((HH / 2 + 4)); sleep 3; cap hd2_sau_cham
      adb shell input keyevent 66; sleep 3; cap hd3_enter
    fi
    sleep 4
    adb shell dumpsys gfxinfo $HPKG > "$O/hd_gfxinfo.txt"
    adb shell dumpsys meminfo $HPKG > "$O/hd_meminfo.txt"
    alog > "$O/hd_log_app.txt"
    adb logcat -d | grep -E "FATAL|AndroidRuntime|libavatar|tombstone" | head -40 > "$O/hd_loi.txt"
    HP=$(adb shell pidof $HPKG 2>/dev/null | tr -d '\r')
    if [ -n "$HP" ]; then echo "app HD con chay (pid $HP)" > "$O/hd_trang_thai.txt"; else echo "APP HD DA SAP / THOAT" > "$O/hd_trang_thai.txt"; fi
    cat "$O/hd_trang_thai.txt"
    grep -E "vao game HD|bocuc" "$O/hd_log_app.txt" | tail -3
    grep -E "Janky|Total frames|50th|90th" "$O/hd_gfxinfo.txt" | head -6
    adb shell am force-stop $HPKG
  else
    echo "CAI APK HD LOI: $(tail -2 "$O/hd_cai_apk.txt")" > "$O/hd_trang_thai.txt"
  fi
  adb shell am start -W -n $PKG/.MainActivity --es server 10.0.2.2:19129 > /dev/null 2>&1
  sleep 3
fi

# ---- ket qua
P2=$(pid_of)
if [ -n "$P2" ]; then echo "app con chay (pid $P2)" > "$O/trang_thai_app.txt"; else echo "APP DA SAP / THOAT" > "$O/trang_thai_app.txt"; fi
adb logcat -d > "$O/logcat.txt"
alog > "$O/log_app.txt"
grep -E "FATAL|AndroidRuntime|libavatar|tombstone|SAP" "$O/logcat.txt" | head -40 > "$O/loi.txt"
kill $FAKE 2>/dev/null || true
echo "== may chu gia:"; cat "$O/may_chu_gia.txt"
if grep -q "e50000" "$O/may_chu_gia.txt"; then
  echo "KET QUA: client da ket noi va gui goi dang nhap toi may chu (OK)" | tee "$O/KET_QUA_KET_NOI.txt"
else
  echo "KET QUA: CHUA thay goi tin cua client o may chu gia" | tee "$O/KET_QUA_KET_NOI.txt"
fi
cat "$O/trang_thai_app.txt"
grep -E "Janky|Total frames|50th|90th" "$O/gfxinfo.txt" | head -6
grep -E "TOTAL" "$O/meminfo.txt" | head -2
tail -30 "$O/log_app.txt"
ls -la "$O"
