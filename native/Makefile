# Build ban Linux de thu: make JAR=... (mac dinh client hien tai)
JAR ?= /home/claude/clientpatch/client_tia3.jar
OPT ?= -O1 -g
CC ?= gcc
GEN := build/gen
OBJ := build/obj
INC := -Iruntime -I$(GEN) -Ithird_party/bdwgc/include -Ithird_party/stb
CFLAGS := $(OPT) -fwrapv -fno-strict-aliasing -pthread $(INC) -DGC_THREADS -DTHREAD_LOCAL_ALLOC -DGC_BUILTIN_ATOMIC -DNO_EXECUTE_PERMISSION
GCFLAGS := -O2 -pthread -Ithird_party/bdwgc/include -DGC_THREADS -DTHREAD_LOCAL_ALLOC -DGC_BUILTIN_ATOMIC -DNO_EXECUTE_PERMISSION -DALL_INTERIOR_POINTERS -DGC_DISABLE_INCREMENTAL

LIBSRC := $(shell find lib/src -name '*.java')
RTSRC := runtime/rt.c runtime/natives_lang.c runtime/gfx.c runtime/disp.c runtime/io.c runtime/midi.c
GENC = $(wildcard $(GEN)/j_*.c) $(GEN)/meta.c

all: build/army2n

lib/out/.stamp: $(LIBSRC)
	rm -rf lib/out && mkdir -p lib/out
	javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath lib/out -sourcepath lib/src -d lib/out $(LIBSRC) 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
	touch $@

$(GEN)/.stamp: lib/out/.stamp tools/j2c.py $(JAR)
	rm -rf $(GEN)
	python3 tools/j2c.py -o $(GEN) lib/out $(JAR)
	touch $@

gen: $(GEN)/.stamp

$(OBJ)/gc.o: third_party/bdwgc/extra/gc.c
	@mkdir -p $(OBJ)
	$(CC) $(GCFLAGS) -c $< -o $@

$(OBJ)/rt_%.o: runtime/%.c $(GEN)/.stamp runtime/rt.h runtime/platform.h
	@mkdir -p $(OBJ)
	$(CC) $(CFLAGS) -c $< -o $@

$(OBJ)/harness.o: platform/linux/main.c runtime/platform.h
	@mkdir -p $(OBJ)
	$(CC) $(CFLAGS) -c $< -o $@

build/army2n: $(GEN)/.stamp $(OBJ)/gc.o $(patsubst runtime/%.c,$(OBJ)/rt_%.o,$(RTSRC)) $(OBJ)/harness.o
	$(MAKE) genobj
	$(CC) -pthread -o $@ $(OBJ)/gc.o $(patsubst runtime/%.c,$(OBJ)/rt_%.o,$(RTSRC)) $(OBJ)/harness.o $(OBJ)/gen_*.o -lz -lm

genobj: $(patsubst $(GEN)/%.c,$(OBJ)/gen_%.o,$(GENC))

$(OBJ)/gen_%.o: $(GEN)/%.c $(GEN)/.stamp
	@mkdir -p $(OBJ)
	$(CC) $(CFLAGS) -w -c $< -o $@

clean:
	rm -rf build lib/out
.PHONY: all gen genobj clean
