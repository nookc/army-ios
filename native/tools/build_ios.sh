#!/bin/bash
# Build app iOS "thuan" (client dich san sang C) tren macOS (CI GitHub Actions).
#   bash native/tools/build_ios.sh [device|sim|ca-hai]
# Ket qua: native/build/ios/Army250-Native.ipa (chua ky), native/build/ios/sim/Army250.app (simulator)
set -euo pipefail
MODE=${1:-ca-hai}
N=$(cd "$(dirname "$0")/.." && pwd)
REPO=$(cd "$N/.." && pwd)
JAR=${JAR:-$REPO/Bundled/Army250.jar}
B=$N/build
TP=$N/third_party
mkdir -p "$B" "$TP"

# ---- thu vien ben thu ba (ghim phien ban)
if [ ! -f "$TP/bdwgc/extra/gc.c" ]; then
  git clone -q --depth 1 --branch v8.2.8 https://github.com/ivmai/bdwgc.git "$TP/bdwgc"
fi
if [ ! -f "$TP/stb/stb_image.h" ]; then
  git clone -q https://github.com/nothings/stb.git "$TP/stb"
  git -C "$TP/stb" checkout -q 2c980bb59875b0d32144a71867fbdebb2f77cd20 || true
fi

# ---- thu vien Java (CLDC/MIDP rieng) + dich sang C
rm -rf "$N/lib/out" && mkdir -p "$N/lib/out"
javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$N/lib/out" -sourcepath "$N/lib/src" \
  -d "$N/lib/out" $(find "$N/lib/src" -name '*.java') 2>&1 | grep -v -i "warning\|JAVA_TOOL_OPTIONS" || true
rm -rf "$B/gen"
python3 "$N/tools/j2c.py" -o "$B/gen" "$N/lib/out" "$JAR"
cat "$B/gen/stats.txt" | head -8

DEFS="-DGC_THREADS -DTHREAD_LOCAL_ALLOC -DGC_BUILTIN_ATOMIC -DNO_EXECUTE_PERMISSION"
INC="-I$N/runtime -I$B/gen -I$TP/bdwgc/include -I$TP/stb"

build_one() {   # $1 = sdk (iphoneos|iphonesimulator), $2 = thu muc ra
  local SDK=$1 OUT=$2
  local SYS
  SYS=$(xcrun --sdk "$SDK" --show-sdk-path)
  local VMIN="-miphoneos-version-min=15.0"
  [ "$SDK" = iphonesimulator ] && VMIN="-mios-simulator-version-min=15.0"
  local CC="xcrun --sdk $SDK clang -arch arm64 $VMIN -isysroot $SYS"
  rm -rf "$OUT" && mkdir -p "$OUT/obj"
  echo "== bien dich ($SDK)"
  $CC -O2 -w $DEFS -DALL_INTERIOR_POINTERS -I"$TP/bdwgc/include" -c "$TP/bdwgc/extra/gc.c" -o "$OUT/obj/gc.o" &
  for f in rt natives_lang gfx disp io midi; do
    $CC -O2 -fwrapv -fno-strict-aliasing $DEFS $INC -c "$N/runtime/$f.c" -o "$OUT/obj/rt_$f.o" &
  done
  wait
  # ma game: nhieu tep lon, chay song song (xargs -I cua macOS gioi han 255 ky tu -> dung tep lenh)
  cat > "$OUT/cc1.sh" <<CC1
#!/bin/sh
exec $CC -O2 -w -fwrapv -fno-strict-aliasing $DEFS $INC -c "\$1" -o "$OUT/obj/gen_\$(basename "\$1" .c).o"
CC1
  ls "$B/gen"/j_*.c "$B/gen/meta.c" | xargs -P 4 -n 1 sh "$OUT/cc1.sh"
  for f in App PadView; do
    $CC -O2 -fobjc-arc $INC -I"$N/platform/ios" -c "$N/platform/ios/$f.m" -o "$OUT/obj/$f.o"
  done
  echo "== lien ket"
  $CC -o "$OUT/Army250" "$OUT"/obj/*.o -framework UIKit -framework Foundation -framework CoreGraphics \
    -framework QuartzCore -framework AVFoundation -framework AudioToolbox -lz -dead_strip
  local APP="$OUT/Army250.app"
  mkdir -p "$APP"
  cp "$OUT/Army250" "$APP/"
  sed "s/BUILD_NUMBER/${BUILD_NUMBER:-1}/" "$N/platform/ios/Info.plist" > "$APP/Info.plist"
  if [ "$SDK" = iphonesimulator ]; then
    /usr/libexec/PlistBuddy -c "Set :CFBundleSupportedPlatforms:0 iPhoneSimulator" "$APP/Info.plist"
  fi
  # khoa DT* + BuildMachineOSBuild y nhu Xcode them. App Store Connect / TestFlight doc cac khoa nay de biet ban Xcode,
  # SDK, macOS da build: thieu hoac sai dang (truoc day thieu DTXcodeBuild, DTSDKBuild..., DTXcode "263" thay vi "2630")
  # -> "This build is using a beta version of Xcode and can't be submitted" du CI dung Xcode chinh thuc.
  local SV SB XV XB XN MB
  SV=$(xcrun --sdk "$SDK" --show-sdk-version)            # 26.2
  SB=$(xcrun --sdk "$SDK" --show-sdk-build-version)      # 23C57
  XV=$(xcodebuild -version | awk 'NR==1{print $2}')      # 26.3 / 26.0.1
  XB=$(xcodebuild -version | awk 'NR==2{print $3}')      # 17C529
  XN=$(echo "$XV" | awk -F. '{printf "%02d%d%d", $1, $2, $3}')   # 2630 / 2601 (dang Xcode ghi)
  MB=$(sw_vers -buildVersion)                            # macOS may build
  local PB=/usr/libexec/PlistBuddy
  dat_khoa() { $PB -c "Delete :$1" "$APP/Info.plist" >/dev/null 2>&1 || true; $PB -c "Add :$1 string $2" "$APP/Info.plist"; }
  dat_khoa BuildMachineOSBuild "$MB"
  dat_khoa DTCompiler com.apple.compilers.llvm.clang.1_0
  dat_khoa DTPlatformBuild "$SB"
  dat_khoa DTPlatformName "$SDK"
  dat_khoa DTPlatformVersion "$SV"
  dat_khoa DTSDKBuild "$SB"
  dat_khoa DTSDKName "$SDK$SV"
  dat_khoa DTXcode "$XN"
  dat_khoa DTXcodeBuild "$XB"
  echo "== Info.plist: Xcode $XV ($XB) -> DTXcode $XN, SDK $SDK$SV ($SB), macOS $MB"
  for k in BuildMachineOSBuild DTPlatformBuild DTSDKBuild DTXcode DTXcodeBuild; do
    [ -n "$($PB -c "Print :$k" "$APP/Info.plist" 2>/dev/null)" ] || { echo "THIEU $k trong Info.plist"; exit 1; }
  done
  $PB -c "Add :UIRequiredDeviceCapabilities array" -c "Add :UIRequiredDeviceCapabilities:0 string arm64" "$APP/Info.plist"

  plutil -convert binary1 "$APP/Info.plist"
  # bang khai bao quyen rieng tu (Apple yeu cau tu 05/2024 khi dung API "required reason"; doc thuoc tinh tep log)
  plutil -lint "$N/platform/ios/PrivacyInfo.xcprivacy"
  cp "$N/platform/ios/PrivacyInfo.xcprivacy" "$APP/"
  cp "$JAR" "$APP/Army250.jar"
  cp "$N/platform/ios/res/AppIcon60x60@2x.png" "$N/platform/ios/res/AppIcon60x60@3x.png" "$APP/"
  # bieu tuong: Assets.car bang actool nhu Xcode (Info.plist co CFBundleIconName=AppIcon); loi thi van con PNG o tren
  local XA="$OUT/Assets.xcassets"
  rm -rf "$XA" && mkdir -p "$XA/AppIcon.appiconset"
  cp "$N/platform/ios/res/AppIcon-1024.png" "$XA/AppIcon.appiconset/icon1024.png"
  echo '{"info":{"version":1,"author":"xcode"}}' > "$XA/Contents.json"
  echo '{"images":[{"filename":"icon1024.png","idiom":"universal","platform":"ios","size":"1024x1024"}],"info":{"version":1,"author":"xcode"}}' \
    > "$XA/AppIcon.appiconset/Contents.json"
  xcrun actool --compile "$APP" --platform "$SDK" --minimum-deployment-target 15.0 --target-device iphone \
    --app-icon AppIcon --output-partial-info-plist "$OUT/icon-info.plist" --output-format human-readable-text "$XA" \
    | tail -5 || echo "actool loi: chi dung PNG"
  [ -f "$APP/Assets.car" ] && echo "co Assets.car" || echo "KHONG co Assets.car"
  printf 'APPL????' > "$APP/PkgInfo"
  ls -la "$APP"
}

if [ "$MODE" = device ] || [ "$MODE" = ca-hai ]; then
  build_one iphoneos "$B/ios/dev"
  rm -rf "$B/ios/ipa" && mkdir -p "$B/ios/ipa/Payload"
  cp -R "$B/ios/dev/Army250.app" "$B/ios/ipa/Payload/"
  (cd "$B/ios/ipa" && zip -qry ../Army250-Native.ipa Payload)
  ls -la "$B/ios/Army250-Native.ipa"
fi
if [ "$MODE" = sim ] || [ "$MODE" = ca-hai ]; then
  build_one iphonesimulator "$B/ios/sim"
  codesign -s - --force "$B/ios/sim/Army250.app" || true
fi
