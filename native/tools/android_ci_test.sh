#!/bin/bash
# Chay thu APK tren emulator CI bang thao tac THAT (adb input): cham ban phim ao, cham o nhap,
# go chu vao hop nhap Android, chup man hinh; do toc do khung hinh, RAM, CPU;
# thu ca truong hop Android tao lai activity (game van chay, hop nhap chu hien lai).
#   bash native/tools/android_ci_test.sh      (chay tu goc repo, emulator da bat)
PKG=vn.army2coffee.thuan
O=native/build/android/test
mkdir -p "$O"
say() { echo "== $*"; }
cap() { adb exec-out screencap -p > "$O/$1.png"; say "chup $1"; }
alog() { adb logcat -d -s Army2Coffee:I | tr -d '\r'; }   # Android cu: adb tra ve CRLF

APK=${APK:-native/build/android/Army2Coffee-test.apk}
adb shell getprop ro.build.version.release > "$O/android.txt"
if ! adb install -r "$APK" > "$O/cai_apk.txt" 2>&1; then
  echo "CAI APK LOI: $(tail -2 "$O/cai_apk.txt")" > "$O/trang_thai_app.txt"
  exit 0
fi
adb shell getprop ro.product.cpu.abi > "$O/abi.txt"
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity

# bo cuc tren man hinh (app ghi vao logcat): [bocuc] canvas L T R B pad L T R B game W H
B=""
for i in $(seq 1 20); do
  B=$(alog | grep -o "\[bocuc\].*" | tail -1)
  [ -n "$B" ] && break
  sleep 1
done
say "$B"
if [ -z "$B" ]; then
  echo "KHONG THAY BO CUC" > "$O/trang_thai_app.txt"
  adb logcat -d > "$O/logcat.txt"
  exit 0
fi
set -- $B
CL=$3; CT=$4; CR=$5; CB=$6; PL=$8; PT=$9; PR=${10}; PB=${11}; GW=${13}; GH=${14}

# cham o ban phim ao: cot 0-3, hang 0-1 (F1 Len Luc F2 / Trai Xuong Phai OK)
pad() {
  local x=$((PL + (2 * $1 + 1) * (PR - PL) / 8)) y=$((PT + (2 * $2 + 1) * (PB - PT) / 4))
  adb shell input swipe $x $y $x $y 120
}
# cham vao khung game theo toa do game (320 x GH)
tapg() {
  local x=$((CL + $1 * (CR - CL) / GW)) y=$((CT + $2 * (CB - CT) / GH))
  adb shell input swipe $x $y $x $y 120
}
hop() { alog | grep -c "hop nhap chu kieu"; }
pid_of() {
  local p
  p=$(adb shell ps 2>/dev/null | tr -d '\r' | awk -v k=$PKG '$NF == k {print $2}' | head -1)
  [ -z "$p" ] && p=$(adb shell ps -A 2>/dev/null | tr -d '\r' | awk -v k=$PKG '$NF == k {print $2}' | head -1)
  echo "$p"
}

sleep 7
cap a1_cauhinh
pad 3 1; sleep 3; cap a2_amthanh
pad 0 0; sleep 5; cap a3_gioithieu
pad 3 0; sleep 5; cap a4_dangnhap

# toc do ve (10 giay o man dang nhap), RAM, CPU
PID=$(pid_of)
adb shell dumpsys gfxinfo $PKG reset > /dev/null
sleep 10
adb shell dumpsys gfxinfo $PKG > "$O/gfxinfo.txt"
adb shell dumpsys meminfo $PKG > "$O/meminfo.txt"
adb shell top -b -n 2 -d 5 -p "$PID" > "$O/cpu.txt" 2>&1
grep -q "Invalid argument" "$O/cpu.txt" && adb shell top -n 2 -d 5 | tr -d '\r' | grep -E "PID|army2" > "$O/cpu.txt"

# o TEN -> hop nhap chu Android, go bang ban phim (adb input text), Enter = OK
n0=$(hop)
tapg 188 $((GH - 124)); sleep 3
[ "$(hop)" -le "$n0" ] && { tapg 188 $((GH - 124)); sleep 3; }
cap a5_hop_ten
adb shell input text tbot6; sleep 1; cap a6_go_ten
adb shell input keyevent 66; sleep 3; cap a7_da_nhap_ten

# o MAT KHAU (lan cham dau co the chi chon o)
n0=$(hop)
tapg 188 $((GH - 89)); sleep 3
[ "$(hop)" -le "$n0" ] && { tapg 188 $((GH - 89)); sleep 3; }
cap a8_hop_mk
adb shell input text 123456; sleep 1
adb shell input keyevent 66; sleep 3; cap a9_da_nhap_mk

# Android huy activity khi ra man hinh chinh (tuy chon "Khong giu hoat dong"): mo lai phai thay game dang chay
adb shell settings put global always_finish_activities 1
sleep 1
adb shell input keyevent 3; sleep 3
adb shell am start -W -n $PKG/.MainActivity; sleep 4
cap b1_mo_lai
# dang mo hop nhap chu thi activity bi huy: mo lai phai hien lai hop (con chu da go)
n0=$(hop)
tapg 188 $((GH - 124)); sleep 3
[ "$(hop)" -le "$n0" ] && { tapg 188 $((GH - 124)); sleep 3; }
adb shell input text abc; sleep 1
adb shell input keyevent 3; sleep 3
adb shell am start -W -n $PKG/.MainActivity; sleep 4
cap b2_hop_hien_lai
adb shell input keyevent 4; sleep 2     # Back lan 1: an ban phim Android
cap b3_an_ban_phim
adb shell input keyevent 4; sleep 3     # Back lan 2: Huy hop, ban phim Android phai an
cap b4_huy_hop
adb shell settings put global always_finish_activities 0

PID2=$(pid_of)
if [ -n "$PID2" ]; then
  echo "app con chay (pid $PID2, luc dau $PID)" > "$O/trang_thai_app.txt"
else
  echo "APP DA SAP / THOAT" > "$O/trang_thai_app.txt"
fi
adb logcat -d > "$O/logcat.txt"
alog > "$O/log_app.txt"
say "xong"
