#!/bin/bash
# Build APK Android "thuan" cho client Avatar 250 (2.5.8): client J2ME dich san sang C -> ma may ARM/x86,
# KHONG gia lap / J2ME Loader. Dung chung runtime + lop JNI voi ban Army (platform/android/jni/android.c).
# Can: ANDROID_HOME (build-tools, platforms), NDK (ANDROID_NDK_HOME / ANDROID_NDK_LATEST_HOME), JDK 17.
#   bash native/tools/build_avatar_android.sh
# Ket qua (native/build/avatar-android):
#   Avatar-unsigned.apk   - da zipalign, CHUA ky (ky ban phat hanh bang khoa avatar.jks, ngoai CI)
#   Avatar-test.apk       - ky bang khoa thu tam thoi (chi de chay thu tren emulator CI)
set -euo pipefail
N=$(cd "$(dirname "$0")/.." && pwd)
REPO=$(cd "$N/.." && pwd)
JAR=${JAR:-$REPO/Bundled/Avatar258.jar}
B=$N/build/avatar-android
TP=$N/third_party
A=$N/platform/avatar-android
JNI=$N/platform/android/jni/android.c
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}
NDK=${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
PLAT=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)
API=21
# ban cu (gia lap J2ME) la versionCode 5: ban thuan bat dau tu 100 de cai de len duoc
VCODE=$((100 + ${BUILD_NUMBER:-1}))
ABIS=${ABIS:-"arm64-v8a armeabi-v7a x86_64"}
echo "NDK $NDK"
echo "build-tools $BT"
echo "platform $PLAT"
mkdir -p "$TP"

# ---- thu vien ben thu ba (ghim phien ban)
if [ ! -f "$TP/bdwgc/extra/gc.c" ]; then
  git clone -q --depth 1 --branch v8.2.8 https://github.com/ivmai/bdwgc.git "$TP/bdwgc"
fi
if [ ! -f "$TP/stb/stb_image.h" ]; then
  git clone -q https://github.com/nothings/stb.git "$TP/stb"
  git -C "$TP/stb" checkout -q 2c980bb59875b0d32144a71867fbdebb2f77cd20 || true
fi

# ---- thu vien Java (CLDC/MIDP rieng) + dich client sang C
rm -rf "$N/lib/out" && mkdir -p "$N/lib/out"
javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$N/lib/out" -sourcepath "$N/lib/src" \
  -d "$N/lib/out" $(find "$N/lib/src" -name '*.java') 2>&1 | grep -v -i "warning\|JAVA_TOOL_OPTIONS" || true
rm -rf "$B" && mkdir -p "$B"
python3 "$N/tools/j2c.py" -o "$B/gen" "$N/lib/out" "$JAR"
head -3 "$B/gen/stats.txt"

# ---- ma may cho tung ABI -> libavatar.so
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
# -fsigned-char: char co dau nhu x86 va iOS arm64 (Android ARM mac dinh char khong dau)
DEFS="-fsigned-char -DGC_THREADS -DTHREAD_LOCAL_ALLOC -DGC_BUILTIN_ATOMIC -DNO_EXECUTE_PERMISSION"
# lop JNI dung chung, dat ten lop Java / MIDlet / jar cua Avatar (chi cho android.c, khong dua vao cc1.sh)
AVDEFS='-DJ2N_JNI_CLASS=vn_avatar_diemky_Native -DJ2N_JNI_PATH="vn/avatar/diemky/Native" -DJ2N_MIDLET="main.GameMidlet" -DJ2N_JAR_ASSET="Avatar258.jar" -DJ2N_TAG="Avatar"'
INC="-I$N/runtime -I$B/gen -I$TP/bdwgc/include -I$TP/stb"
for ABI in $ABIS; do
  case $ABI in
    arm64-v8a) TRIPLE=aarch64-linux-android ;;
    armeabi-v7a) TRIPLE=armv7a-linux-androideabi ;;
    x86_64) TRIPLE=x86_64-linux-android ;;
    x86) TRIPLE=i686-linux-android ;;
    *) echo "ABI la: $ABI"; exit 1 ;;
  esac
  CC="$TC/bin/${TRIPLE}${API}-clang"
  O=$B/obj/$ABI
  mkdir -p "$O" "$B/stage/lib/$ABI"
  echo "== bien dich $ABI"
  $CC -O2 -fPIC -w $DEFS -DALL_INTERIOR_POINTERS -DGC_DISABLE_INCREMENTAL -I"$TP/bdwgc/include" -c "$TP/bdwgc/extra/gc.c" -o "$O/gc.o" &
  for f in rt natives_lang gfx disp io midi; do
    $CC -O2 -fPIC -w -fwrapv -fno-strict-aliasing $DEFS $INC -c "$N/runtime/$f.c" -o "$O/rt_$f.o" &
  done
  # $AVDEFS khong ngoac: tach tu giu nguyen dau " ben trong (chuoi C)
  $CC -O2 -fPIC -Wall -Wno-unused-parameter $DEFS $INC $AVDEFS -c "$JNI" -o "$O/android.o" &
  wait
  cat > "$O/cc1.sh" <<CC1
#!/bin/sh
exec $CC -O2 -fPIC -w -fwrapv -fno-strict-aliasing $DEFS $INC -c "\$1" -o "$O/gen_\$(basename "\$1" .c).o"
CC1
  ls "$B/gen"/j_*.c "$B/gen/meta.c" | xargs -P "$(nproc 2>/dev/null || echo 4)" -n 1 sh "$O/cc1.sh"
  $CC -shared -o "$B/stage/lib/$ABI/libavatar.so" "$O"/*.o -llog -landroid -ljnigraphics -lz -lm \
    -Wl,--no-undefined -Wl,-z,max-page-size=16384 -Wl,--build-id=sha1
  cp "$B/stage/lib/$ABI/libavatar.so" "$B/libavatar-$ABI-debug.so"
  "$TC/bin/llvm-strip" --strip-unneeded "$B/stage/lib/$ABI/libavatar.so"
  ls -la "$B/stage/lib/$ABI/libavatar.so"
  # dung ten ham JNI cua Avatar
  "$TC/bin/llvm-nm" -D "$B/stage/lib/$ABI/libavatar.so" | grep -c "Java_vn_avatar_diemky_Native_" 
done

# ---- Java -> dex
echo "== java"
mkdir -p "$B/classes" "$B/dex"
javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$PLAT/android.jar" -d "$B/classes" \
  $(find "$A/java" -name '*.java') 2>&1 | grep -v "JAVA_TOOL_OPTIONS" || true
ls "$B/classes/vn/avatar/diemky/"
"$BT/d8" --release --min-api $API --lib "$PLAT/android.jar" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/dex/classes.dex" "$B/stage/"

# ---- tai nguyen + manifest + jar game (khong nen de doc thang tu apk)
echo "== apk"
mkdir -p "$B/assets"
cp "$JAR" "$B/assets/Avatar258.jar"
# may chu mac dinh (dien san o hop cai dat; de trong = nguoi choi tu nhap)
if [ -f "$REPO/Bundled/server.txt" ]; then cp "$REPO/Bundled/server.txt" "$B/assets/server.txt"; else : > "$B/assets/server.txt"; fi
"$BT/aapt2" compile --dir "$A/res" -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$PLAT/android.jar" --manifest "$A/AndroidManifest.xml" \
  --min-sdk-version $API --target-sdk-version 34 --version-code "$VCODE" --version-name "2.5.8-thuan" \
  -A "$B/assets" -0 jar --auto-add-overlay "$B/res.zip"
(cd "$B/stage" && zip -q -r "$B/base.apk" classes.dex lib)
"$BT/zipalign" -f -p 4 "$B/base.apk" "$B/Avatar-unsigned.apk"
unzip -l "$B/Avatar-unsigned.apk" | tail -12

# ---- ban thu cho emulator CI (khoa tam thoi, khong dung de phat hanh)
keytool -genkeypair -keystore "$B/thu.keystore" -storepass thuthu -keypass thuthu -alias thu -keyalg RSA \
  -keysize 2048 -validity 30 -dname "CN=Avatar Test" > /dev/null 2>&1
"$BT/apksigner" sign --ks "$B/thu.keystore" --ks-pass pass:thuthu --key-pass pass:thuthu \
  --out "$B/Avatar-test.apk" "$B/Avatar-unsigned.apk"
"$BT/apksigner" verify --print-certs "$B/Avatar-test.apk" | head -3
ls -la "$B"/*.apk
