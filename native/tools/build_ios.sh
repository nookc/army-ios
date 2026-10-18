#!/bin/bash
# Build app iOS "thuan" (client dich san sang C) tren macOS (CI GitHub Actions).
#   bash native/tools/build_ios.sh [device|sim|ca-hai]
# Ket qua: native/build/ios/Army2Coffee-Native.ipa (chua ky), native/build/ios/sim/Army2Coffee.app (simulator)
set -euo pipefail
MODE=${1:-ca-hai}
N=$(cd "$(dirname "$0")/.." && pwd)
REPO=$(cd "$N/.." && pwd)
JAR=${JAR:-$REPO/Bundled/Army2Coffee.jar}
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
  for f in rt natives_lang gfx disp io; do
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
  $CC -o "$OUT/Army2Coffee" "$OUT"/obj/*.o -framework UIKit -framework Foundation -framework CoreGraphics \
    -framework QuartzCore -framework AVFoundation -framework AudioToolbox -lz -dead_strip
  local APP="$OUT/Army2Coffee.app"
  mkdir -p "$APP"
  cp "$OUT/Army2Coffee" "$APP/"
  sed "s/BUILD_NUMBER/${BUILD_NUMBER:-1}/" "$N/platform/ios/Info.plist" > "$APP/Info.plist"
  if [ "$SDK" = iphonesimulator ]; then
    /usr/libexec/PlistBuddy -c "Set :CFBundleSupportedPlatforms:0 iPhoneSimulator" "$APP/Info.plist"
  fi
  # khoa DT* nhu Xcode them (mot so cong cu ky / cai dat can)
  local SV
  SV=$(xcrun --sdk "$SDK" --show-sdk-version)
  local PB=/usr/libexec/PlistBuddy
  $PB -c "Add :DTPlatformName string $SDK" "$APP/Info.plist"
  $PB -c "Add :DTPlatformVersion string $SV" "$APP/Info.plist"
  $PB -c "Add :DTSDKName string $SDK$SV" "$APP/Info.plist"
  $PB -c "Add :DTXcode string $(xcodebuild -version | head -1 | awk '{print $2}' | tr -d .)" "$APP/Info.plist" || true
  $PB -c "Add :UIRequiredDeviceCapabilities array" -c "Add :UIRequiredDeviceCapabilities:0 string arm64" "$APP/Info.plist"

  plutil -convert binary1 "$APP/Info.plist"
  cp "$JAR" "$APP/Army2Coffee.jar"
  cp "$N/platform/ios/res/AppIcon60x60@2x.png" "$N/platform/ios/res/AppIcon60x60@3x.png" "$APP/"
  printf 'APPL????' > "$APP/PkgInfo"
  ls -la "$APP"
}

if [ "$MODE" = device ] || [ "$MODE" = ca-hai ]; then
  build_one iphoneos "$B/ios/dev"
  rm -rf "$B/ios/ipa" && mkdir -p "$B/ios/ipa/Payload"
  cp -R "$B/ios/dev/Army2Coffee.app" "$B/ios/ipa/Payload/"
  (cd "$B/ios/ipa" && zip -qry ../Army2Coffee-Native.ipa Payload)
  ls -la "$B/ios/Army2Coffee-Native.ipa"
fi
if [ "$MODE" = sim ] || [ "$MODE" = ca-hai ]; then
  build_one iphonesimulator "$B/ios/sim"
  codesign -s - --force "$B/ios/sim/Army2Coffee.app" || true
fi
