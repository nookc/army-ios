#!/bin/bash
# Build app iOS "thuan" cho client Avatar 250 (2.5.8): client J2ME dich san sang C -> ma may ARM64,
# khong gia lap / khong bo thong dich. Chay tren macOS (CI GitHub Actions).
#   bash native/tools/build_avatar_ios.sh [device|sim|ca-hai]
# Ket qua: native/build/avatar-ios/Avatar-Thuan.ipa (chua ky), native/build/avatar-ios/sim/Avatar.app (simulator)
set -euo pipefail
MODE=${1:-ca-hai}
N=$(cd "$(dirname "$0")/.." && pwd)
REPO=$(cd "$N/.." && pwd)
JAR=${JAR:-$REPO/Bundled/Avatar258.jar}
B=$N/build
P=$N/platform/avatar-ios
O=$B/avatar-ios
GEN=$B/gen-avatar
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
rm -rf "$GEN"
python3 "$N/tools/j2c.py" -o "$GEN" "$N/lib/out" "$JAR"
cat "$GEN/stats.txt" | head -8

DEFS="-DGC_THREADS -DTHREAD_LOCAL_ALLOC -DGC_BUILTIN_ATOMIC -DNO_EXECUTE_PERMISSION"
INC="-I$N/runtime -I$GEN -I$TP/bdwgc/include -I$TP/stb"

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
  ls "$GEN"/j_*.c "$GEN/meta.c" | xargs -P 4 -n 1 sh "$OUT/cc1.sh"
  $CC -O2 -fobjc-arc -Wall -Wno-unused-parameter $INC -I"$P" -c "$P/App.m" -o "$OUT/obj/App.o"
  echo "== lien ket"
  $CC -o "$OUT/Avatar" "$OUT"/obj/*.o -framework UIKit -framework Foundation -framework CoreGraphics \
    -framework QuartzCore -framework AVFoundation -framework AudioToolbox -lz -dead_strip
  local APP="$OUT/Avatar.app"
  mkdir -p "$APP"
  cp "$OUT/Avatar" "$APP/"
  sed "s/BUILD_NUMBER/${BUILD_NUMBER:-1}/" "$P/Info.plist" > "$APP/Info.plist"
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
  plutil -lint "$P/PrivacyInfo.xcprivacy"
  cp "$P/PrivacyInfo.xcprivacy" "$APP/"
  cp "$JAR" "$APP/Avatar258.jar"
  # may chu mac dinh (dien san o man cai dat; de trong = nguoi choi tu nhap)
  if [ -f "$REPO/Bundled/server.txt" ]; then cp "$REPO/Bundled/server.txt" "$APP/server.txt"; else : > "$APP/server.txt"; fi
  cp "$P/res/AppIcon60x60@2x.png" "$P/res/AppIcon60x60@3x.png" "$APP/"
  # bieu tuong: Assets.car bang actool nhu Xcode (Info.plist co CFBundleIconName=AppIcon); loi thi van con PNG o tren
  local XA="$OUT/Assets.xcassets"
  rm -rf "$XA" && mkdir -p "$XA/AppIcon.appiconset"
  cp "$P/res/AppIcon-1024.png" "$XA/AppIcon.appiconset/icon1024.png"
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

# Bien the "Avatar 250 HD" (ngang + may ve net cao): CUNG ma may, khac Info.plist (bundle id rieng de cai song song voi ban
# thuong, ten app, chi cho xoay ngang) + tep danh dau hd.cfg (App.m thay tep nay thi bat che do HD). $1 = Avatar.app da chep
lam_hd() {   # $1 = Avatar.app (ban sao), $2 = sdk (iphoneos|iphonesimulator)
  local APP=$1 SDK=$2 PB=/usr/libexec/PlistBuddy
  echo "hd=1" > "$APP/hd.cfg"
  $PB -c "Set :CFBundleIdentifier vn.avatar.diemky.hd" "$APP/Info.plist"
  $PB -c "Set :CFBundleDisplayName Avatar 250 HD" "$APP/Info.plist"
  $PB -c "Delete :UISupportedInterfaceOrientations" "$APP/Info.plist" >/dev/null 2>&1 || true
  $PB -c "Add :UISupportedInterfaceOrientations array" \
      -c "Add :UISupportedInterfaceOrientations:0 string UIInterfaceOrientationLandscapeRight" \
      -c "Add :UISupportedInterfaceOrientations:1 string UIInterfaceOrientationLandscapeLeft" "$APP/Info.plist"
  $PB -c "Print :CFBundleIdentifier" -c "Print :CFBundleDisplayName" -c "Print :UISupportedInterfaceOrientations" "$APP/Info.plist"
  # bieu tuong rieng cua ban HD (platform/avatar-ios/res-hd, sinh boi tools/mkicon_hd.py): thay PNG roi + bien dich lai Assets.car
  if [ -f "$P/res-hd/AppIcon-1024.png" ]; then
    cp "$P/res-hd/AppIcon60x60@2x.png" "$P/res-hd/AppIcon60x60@3x.png" "$APP/"
    local XA="$O/Assets-hd-$SDK.xcassets"
    rm -rf "$XA" && mkdir -p "$XA/AppIcon.appiconset"
    cp "$P/res-hd/AppIcon-1024.png" "$XA/AppIcon.appiconset/icon1024.png"
    echo '{"info":{"version":1,"author":"xcode"}}' > "$XA/Contents.json"
    echo '{"images":[{"filename":"icon1024.png","idiom":"universal","platform":"ios","size":"1024x1024"}],"info":{"version":1,"author":"xcode"}}' \
      > "$XA/AppIcon.appiconset/Contents.json"
    rm -f "$APP/Assets.car"
    xcrun actool --compile "$APP" --platform "$SDK" --minimum-deployment-target 15.0 --target-device iphone \
      --app-icon AppIcon --output-partial-info-plist "$O/icon-info-hd.plist" --output-format human-readable-text "$XA" \
      | tail -3 || echo "actool HD loi: chi dung PNG"
    [ -f "$APP/Assets.car" ] && echo "HD: co Assets.car (bieu tuong rieng)" || echo "HD: KHONG co Assets.car"
  fi
}

if [ "$MODE" = device ] || [ "$MODE" = ca-hai ]; then
  build_one iphoneos "$O/dev"
  rm -rf "$O/ipa" "$O/Avatar-Thuan.ipa" && mkdir -p "$O/ipa/Payload"
  cp -R "$O/dev/Avatar.app" "$O/ipa/Payload/"
  (cd "$O/ipa" && zip -qry ../Avatar-Thuan.ipa Payload)
  ls -la "$O/Avatar-Thuan.ipa"
  # kiem tra: ma may arm64 that, khong co bo thong dich/gia lap
  lipo -info "$O/dev/Avatar.app/Avatar"
  rm -rf "$O/ipa-hd" "$O/AvatarHD-Thuan.ipa" && mkdir -p "$O/ipa-hd/Payload"
  cp -R "$O/dev/Avatar.app" "$O/ipa-hd/Payload/"
  lam_hd "$O/ipa-hd/Payload/Avatar.app" iphoneos
  (cd "$O/ipa-hd" && zip -qry ../AvatarHD-Thuan.ipa Payload)
  ls -la "$O/AvatarHD-Thuan.ipa"
fi
if [ "$MODE" = sim ] || [ "$MODE" = ca-hai ]; then
  build_one iphonesimulator "$O/sim"
  codesign -s - --force "$O/sim/Avatar.app" || true
  rm -rf "$O/sim-hd" && mkdir -p "$O/sim-hd"
  cp -R "$O/sim/Avatar.app" "$O/sim-hd/"
  lam_hd "$O/sim-hd/Avatar.app" iphonesimulator
  codesign -s - --force "$O/sim-hd/Avatar.app" || true
fi
