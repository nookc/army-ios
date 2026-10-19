#!/bin/bash
# Thu DANG NHAP that tren emulator (cai moi, chua co danh sach may chu luu san):
# man dang nhap -> go ten / mat khau -> Dang nhap -> lay danh sach may chu (http) -> Chon -> ket noi.
# Chup man hinh lien tuc de thay app co ket "Xin cho..." hay khong; ghi log [net] cua app.
#   APK=... bash native/tools/android_login_test.sh      (chay tu goc repo, emulator da bat)
PKG=vn.army250.thuan
O=native/build/android/test
mkdir -p "$O"
say() { echo "== $*"; }
cap() { adb exec-out screencap -p > "$O/$1.png"; say "chup $1"; }
alog() { adb logcat -d -s Army250:V | tr -d '\r'; }

APK=${APK:-native/build/android/Army250-test.apk}
TEN=${TEN:-kiemtra$RANDOM}
MK=${MK:-matkhau123}
adb shell getprop ro.build.version.release > "$O/android.txt"
if ! adb install -r ${ABI:+--abi $ABI} "$APK" > "$O/cai_apk.txt" 2>&1; then
  echo "CAI APK LOI: $(tail -2 "$O/cai_apk.txt")" > "$O/trang_thai_app.txt"
  exit 0
fi
adb shell getprop ro.product.cpu.abi > "$O/abi.txt"
adb shell pm clear $PKG > /dev/null 2>&1      # cai moi: khong co danh sach may chu luu san
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity

B=""
for i in $(seq 1 30); do
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
pad() {
  local x=$((PL + (2 * $1 + 1) * (PR - PL) / 8)) y=$((PT + (2 * $2 + 1) * (PB - PT) / 4))
  adb shell input swipe $x $y $x $y 120
}
tapg() {
  local x=$((CL + $1 * (CR - CL) / GW)) y=$((CT + $2 * (CB - CT) / GH))
  adb shell input swipe $x $y $x $y 120
}
hop() { alog | grep -c "hop nhap chu kieu"; }

sleep 7
cap l1_cauhinh
pad 3 1; sleep 3
pad 0 0; sleep 5
pad 3 0; sleep 5; cap l2_dangnhap

n0=$(hop)
tapg 188 $((GH - 124)); sleep 3
[ "$(hop)" -le "$n0" ] && { tapg 188 $((GH - 124)); sleep 3; }
adb shell input text "$TEN"; sleep 1
adb shell input keyevent 66; sleep 3
n0=$(hop)
tapg 188 $((GH - 89)); sleep 3
[ "$(hop)" -le "$n0" ] && { tapg 188 $((GH - 89)); sleep 3; }
adb shell input text "$MK"; sleep 1
adb shell input keyevent 66; sleep 3
cap l3_da_go

# Dang nhap (phim OK = ban): app lay danh sach may chu qua http
pad 3 1
for t in 1 3 6 10 15 25; do
  sleep $([ $t = 1 ] && echo 1 || echo $((t - prev)))
  prev=$t
  cap l4_sau_${t}s
done
# Chon may chu dau tien -> ket noi + gui dang nhap (tai khoan khong co: may chu phai bao sai tai khoan)
pad 3 1
prev=0
for t in 2 5 10 20 30; do
  sleep $((t - prev))
  prev=$t
  cap l5_chon_sau_${t}s
done

PID2=$(adb shell pidof $PKG 2>/dev/null | tr -d '\r')
if [ -n "$PID2" ]; then
  echo "app con chay (pid $PID2)" > "$O/trang_thai_app.txt"
else
  echo "APP DA SAP / THOAT" > "$O/trang_thai_app.txt"
fi
adb logcat -d > "$O/logcat.txt"
alog > "$O/log_app.txt"
grep -a "\[net\]\|\[http\]\|ngoai le\|exception\|Exception\|SAP" "$O/log_app.txt" | head -50 > "$O/log_mang.txt"
say "xong"
