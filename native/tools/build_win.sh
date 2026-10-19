#!/bin/bash
# Build ban Windows "thuan" (client dich san sang C -> ma may x86-64, khong gia lap) bang MinGW-w64 (cross tu Linux).
#   bash native/tools/build_win.sh
# Ket qua: native/build/win/Army250.exe (mot tep: jar + bieu tuong nam trong exe)
#          native/build/win/Army250-debug.exe (con ky hieu, de doc crash)
set -euo pipefail
N=$(cd "$(dirname "$0")/.." && pwd)
REPO=$(cd "$N/.." && pwd)
JAR=${JAR:-$REPO/Bundled/Army250.jar}
B=$N/build/win
TP=$N/third_party
CC=${CC_WIN:-x86_64-w64-mingw32-gcc-posix}
WR=${WINDRES:-x86_64-w64-mingw32-windres}
STRIP=${STRIP_WIN:-x86_64-w64-mingw32-strip}
mkdir -p "$TP"

# ---- thu vien ben thu ba (ghim phien ban)
if [ ! -f "$TP/bdwgc/extra/gc.c" ]; then
  git clone -q --depth 1 --branch v8.2.8 https://github.com/ivmai/bdwgc.git "$TP/bdwgc"
fi
if [ ! -f "$TP/stb/stb_image.h" ]; then
  git clone -q https://github.com/nothings/stb.git "$TP/stb"
  git -C "$TP/stb" checkout -q 2c980bb59875b0d32144a71867fbdebb2f77cd20 || true
fi

# ---- thu vien Java (CLDC/MIDP rieng) + dich sang C
if [ "${SKIP_GEN:-0}" != 1 ]; then
  rm -rf "$N/lib/out" && mkdir -p "$N/lib/out"
  javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$N/lib/out" -sourcepath "$N/lib/src" \
    -d "$N/lib/out" $(find "$N/lib/src" -name '*.java') 2>&1 | grep -v -i "warning\|JAVA_TOOL_OPTIONS" || true
  rm -rf "$B/gen"
  python3 "$N/tools/j2c.py" -o "$B/gen" "$N/lib/out" "$JAR"
  head -3 "$B/gen/stats.txt"
fi

DEFS="-DGC_THREADS -DGC_WIN32_PTHREADS -DTHREAD_LOCAL_ALLOC -DGC_BUILTIN_ATOMIC -DJ2N_STB_ZLIB -D_WIN32_WINNT=0x0601"
INC="-I$N/runtime -I$B/gen -I$TP/bdwgc/include -I$TP/stb -I$N/platform/win"
rm -rf "$B/obj" && mkdir -p "$B/obj"
echo "== bien dich (Windows x86-64)"
$CC -O2 -w $DEFS -DALL_INTERIOR_POINTERS -DGC_DISABLE_INCREMENTAL -I"$TP/bdwgc/include" -c "$TP/bdwgc/extra/gc.c" -o "$B/obj/gc.o" &
for f in rt natives_lang gfx disp io midi; do
  $CC -O2 -w -fwrapv -fno-strict-aliasing $DEFS $INC -c "$N/runtime/$f.c" -o "$B/obj/rt_$f.o" &
done
$CC -O2 -Wall -Wno-unused-function -fwrapv $DEFS $INC -municode -c "$N/platform/win/main.c" -o "$B/obj/win_main.o" &
wait
cat > "$B/cc1.sh" <<CC1
#!/bin/sh
exec $CC -O2 -w -fwrapv -fno-strict-aliasing $DEFS $INC -c "\$1" -o "$B/obj/gen_\$(basename "\$1" .c).o"
CC1
ls "$B/gen"/j_*.c "$B/gen/meta.c" | xargs -P "$(nproc 2>/dev/null || echo 4)" -n 1 sh "$B/cc1.sh"

# ---- tai nguyen: bieu tuong, manifest, jar, thong tin phien ban
cp "$N/platform/win/army2.ico" "$N/platform/win/app.manifest" "$B/"
cp "$JAR" "$B/Army250.jar"
sed -e "s#@JAR@#Army250.jar#" -e "s#@BUILD@#${BUILD_NUMBER:-1}#g" "$N/platform/win/res.rc.in" > "$B/res.rc"
(cd "$B" && $WR -O coff res.rc -o obj/res.o)

echo "== lien ket"
$CC -municode -mwindows -static -o "$B/Army250-debug.exe" "$B"/obj/*.o \
  -lws2_32 -lwinmm -lgdi32 -luser32 -lshell32 -lcomctl32 -lole32 -lpthread
$STRIP -o "$B/Army250.exe" "$B/Army250-debug.exe"
ls -la "$B"/*.exe
