#!/bin/bash
# so sanh ket qua bo thu tren JVM va tren runtime dich san
set -e
T=$(cd "$(dirname "$0")" && pwd)
N=$(cd "$T/.." && pwd)
rm -rf $T/jvm $T/nat && mkdir -p $T/jvm $T/nat
javac --release 8 -nowarn -d $T/jvm $T/src/t/Suite.java $T/src/t/JvmMain.java 2>&1 | grep -v JAVA_TOOL || true
TZ=Asia/Ho_Chi_Minh java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp $T/jvm t.JvmMain 2>&1 | grep -v "^Picked up JAVA_TOOL_OPTIONS" > $T/jvm.txt || true
javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath $N/lib/out -d $T/nat $T/src/t/Suite.java $T/src/t/TestMidlet.java 2>&1 | grep -v JAVA_TOOL || true
rm -rf $T/gen && python3 $N/tools/j2c.py -o $T/gen $N/lib/out $T/nat > /dev/null
INC="-I$N/runtime -I$T/gen -I$N/third_party/bdwgc/include -I$N/third_party/stb"
CF="-O1 -g -fwrapv -fno-strict-aliasing -pthread -w -DGC_THREADS -DTHREAD_LOCAL_ALLOC -DGC_BUILTIN_ATOMIC -DNO_EXECUTE_PERMISSION"
mkdir -p $T/obj
for f in $T/gen/*.c $N/runtime/rt.c $N/runtime/natives_lang.c $N/runtime/gfx.c $N/runtime/disp.c $N/runtime/io.c $N/runtime/midi.c $N/platform/linux/main.c; do
  gcc $CF $INC -c $f -o $T/obj/$(basename $f .c)_$(echo $f | md5sum | cut -c1-6).o &
done
wait
gcc -pthread -o $T/suite $T/obj/*.o $N/build/obj/gc.o -lz -lm
printf 'sleep 20000\n' > $T/cmd.txt
TZ=Asia/Ho_Chi_Minh timeout 30 $T/suite $N/lib/out/../../build/obj/../../platform/linux/main.c t.TestMidlet 32 32 $T/cmd.txt $T/out 2>&1 | grep -v "^\[harness\]\|^\[Boot\]\|^\[jar\]" > $T/nat.txt || true
diff $T/jvm.txt $T/nat.txt && echo "GIONG NHAU"
