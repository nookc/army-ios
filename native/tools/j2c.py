#!/usr/bin/env python3
"""j2c - dich bytecode J2ME (CLDC 1.1, class file <= 52, khong jsr/ret/invokedynamic) sang C.

    python3 j2c.py -o OUT_DIR [--exclude-prefix p] nguon1.jar|thu_muc ...

Sinh ra:
    OUT/classes.h     cau truc doi tuong, khai bao lop / ham / truong tinh / ham native
    OUT/natives.h     nguyen mau cac ham native can viet tay (N_...)
    OUT/j_<k>.c       than ham cua tung nhom lop
    OUT/meta.c        mo ta lop (vtable, itable, to tien), bang chuoi hang, bang Class.forName
    OUT/stats.txt     thong ke

Mo hinh doi tuong: xem runtime/rt.h. Moi ham Java -> ham C `R M_x(jctx *t, [jobj self,] tham so...)`.
Ngoai le: t->exc != 0 -> nhay toi nhan xu ly (bang ngoai le cua pc) hoac tra ve.
"""
import collections
import hashlib
import os
import re
import struct
import sys
import zipfile

# ============================================================================ doc class file


def u2(b, o):
    return (b[o] << 8) | b[o + 1]


def u4(b, o):
    return struct.unpack_from('>I', b, o)[0]


def s1(b, o):
    v = b[o]
    return v - 256 if v > 127 else v


def s2(b, o):
    return struct.unpack_from('>h', b, o)[0]


def s4(b, o):
    return struct.unpack_from('>i', b, o)[0]


def mutf8(data):
    """modified UTF-8 -> danh sach don vi UTF-16"""
    out = []
    i = 0
    n = len(data)
    while i < n:
        c = data[i]
        if c < 0x80:
            out.append(c)
            i += 1
        elif (c & 0xE0) == 0xC0 and i + 1 < n:
            out.append(((c & 0x1F) << 6) | (data[i + 1] & 0x3F))
            i += 2
        elif (c & 0xF0) == 0xE0 and i + 2 < n:
            out.append(((c & 0x0F) << 12) | ((data[i + 1] & 0x3F) << 6) | (data[i + 2] & 0x3F))
            i += 3
        else:
            out.append(0xFFFD)
            i += 1
    return out


def u16_to_str(units):
    s = []
    i = 0
    while i < len(units):
        c = units[i]
        if 0xD800 <= c < 0xDC00 and i + 1 < len(units) and 0xDC00 <= units[i + 1] < 0xE000:
            s.append(chr(0x10000 + ((c - 0xD800) << 10) + (units[i + 1] - 0xDC00)))
            i += 2
            continue
        s.append(chr(c) if not (0xD800 <= c < 0xE000) else '�')
        i += 1
    return ''.join(s)


class Method:
    __slots__ = ('cls', 'name', 'desc', 'access', 'code', 'max_stack', 'max_locals', 'exc', 'cname', 'args',
                 'ret', 'vslot', 'is_native', 'is_abstract', 'is_static', 'impl_name', 'sel')

    def __init__(self):
        self.code = None
        self.vslot = None
        self.sel = None


class Field:
    __slots__ = ('cls', 'name', 'desc', 'access', 'const', 'cname')


class ClassFile:
    def __init__(self, data, origin):
        self.origin = origin
        b = data
        if u4(b, 0) != 0xCAFEBABE:
            raise ValueError('not a class file: ' + origin)
        self.minor, self.major = u2(b, 4), u2(b, 6)
        n = u2(b, 8)
        o = 10
        cp = [None] * n
        i = 1
        while i < n:
            tag = b[o]
            if tag == 1:
                ln = u2(b, o + 1)
                cp[i] = (1, bytes(b[o + 3:o + 3 + ln]))
                o += 3 + ln
            elif tag == 3:
                cp[i] = (3, s4(b, o + 1)); o += 5
            elif tag == 4:
                cp[i] = (4, u4(b, o + 1)); o += 5
            elif tag == 5:
                cp[i] = (5, struct.unpack_from('>q', b, o + 1)[0]); o += 9; i += 1
            elif tag == 6:
                cp[i] = (6, struct.unpack_from('>Q', b, o + 1)[0]); o += 9; i += 1
            elif tag in (7, 8, 16, 19, 20):
                cp[i] = (tag, u2(b, o + 1)); o += 3
            elif tag in (9, 10, 11, 12, 17, 18):
                cp[i] = (tag, (u2(b, o + 1), u2(b, o + 3))); o += 5
            elif tag == 15:
                cp[i] = (15, (b[o + 1], u2(b, o + 2))); o += 4
            else:
                raise ValueError('cp tag %d in %s' % (tag, origin))
            i += 1
        self.cp = cp
        self.access, this_c, super_c = u2(b, o), u2(b, o + 2), u2(b, o + 4)
        o += 6
        self.name = self.class_name(this_c)
        self.super = self.class_name(super_c) if super_c else None
        ni = u2(b, o); o += 2
        self.interfaces = [self.class_name(u2(b, o + 2 * k)) for k in range(ni)]
        o += 2 * ni
        self.fields = []
        nf = u2(b, o); o += 2
        for _ in range(nf):
            f = Field()
            f.access, f.name, f.desc = u2(b, o), self.utf(u2(b, o + 2)), self.utf(u2(b, o + 4))
            f.const = None
            o += 6
            na = u2(b, o); o += 2
            for _ in range(na):
                an, ln = self.utf(u2(b, o)), u4(b, o + 2)
                if an == 'ConstantValue':
                    f.const = self.cp[u2(b, o + 6)]
                o += 6 + ln
            self.fields.append(f)
        self.methods = []
        nm = u2(b, o); o += 2
        for _ in range(nm):
            m = Method()
            m.access, m.name, m.desc = u2(b, o), self.utf(u2(b, o + 2)), self.utf(u2(b, o + 4))
            o += 6
            na = u2(b, o); o += 2
            for _ in range(na):
                an, ln = self.utf(u2(b, o)), u4(b, o + 2)
                if an == 'Code':
                    p = o + 6
                    m.max_stack, m.max_locals, cl = u2(b, p), u2(b, p + 2), u4(b, p + 4)
                    m.code = bytes(b[p + 8:p + 8 + cl])
                    q = p + 8 + cl
                    ne = u2(b, q); q += 2
                    m.exc = []
                    for k in range(ne):
                        sp, ep, hp, ct = u2(b, q), u2(b, q + 2), u2(b, q + 4), u2(b, q + 6)
                        m.exc.append((sp, ep, hp, self.class_name(ct) if ct else None))
                        q += 8
                o += 6 + ln
            self.methods.append(m)

    def utf(self, i):
        return u16_to_str(mutf8(self.cp[i][1]))

    def utf16(self, i):
        return mutf8(self.cp[i][1])

    def class_name(self, i):
        return self.utf(self.cp[i][1])

    def nat(self, i):
        n, d = self.cp[i][1]
        return self.utf(n), self.utf(d)

    def ref(self, i):
        tag, (c, nt) = self.cp[i]
        n, d = self.nat(nt)
        return self.class_name(c), n, d


# ============================================================================ mo ta kieu

ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC, ACC_FINAL, ACC_SYNC = 1, 2, 4, 8, 0x10, 0x20
ACC_NATIVE, ACC_IFACE, ACC_ABSTRACT = 0x100, 0x200, 0x400


def parse_desc(desc):
    """(args, ret) - moi kieu la chuoi mo ta"""
    assert desc[0] == '('
    i = 1
    args = []
    while desc[i] != ')':
        j = i
        while desc[j] == '[':
            j += 1
        if desc[j] == 'L':
            j = desc.index(';', j)
        args.append(desc[i:j + 1])
        i = j + 1
    return args, desc[i + 1:]


def kind(t):
    """kieu tren ngan xep: I J F D A (V)"""
    c = t[0]
    if c in 'ZBCSI':
        return 'I'
    if c in 'JFD':
        return c
    if c == 'V':
        return 'V'
    return 'A'


CTYPE = {'I': 'int32_t', 'J': 'int64_t', 'F': 'float', 'D': 'double', 'A': 'jobj', 'V': 'void'}
STORE = {'Z': 'uint8_t', 'B': 'int8_t', 'C': 'uint16_t', 'S': 'int16_t', 'I': 'int32_t', 'J': 'int64_t',
         'F': 'float', 'D': 'double', 'L': 'jobj', '[': 'jobj'}
ZERO = {'I': '0', 'J': '0', 'F': '0.0f', 'D': '0.0', 'A': '0'}
NEWARRAY = {4: 'Z', 5: 'C', 6: 'F', 7: 'D', 8: 'B', 9: 'S', 10: 'I', 11: 'J'}


def size(k):
    return 2 if k in 'JD' else 1


def san(s):
    return re.sub(r'[^A-Za-z0-9]', lambda m: '_' if m.group() == '/' else '_%02x' % ord(m.group()), s)


def h4(s):
    return hashlib.md5(s.encode()).hexdigest()[:4]


def c_int(v):
    if v == -2147483648:
        return '(-2147483647-1)'
    return str(v)


def c_long(v):
    if v == -9223372036854775808:
        return '(INT64_C(-9223372036854775807)-1)'
    return 'INT64_C(%d)' % v


def c_float_bits(bits):
    v = struct.unpack('>f', struct.pack('>I', bits))[0]
    if v != v:
        return '(__builtin_nanf(""))'
    if v in (float('inf'), float('-inf')):
        return '(__builtin_inff())' if v > 0 else '(-__builtin_inff())'
    if v == 0.0 and bits != 0:
        return '(-0.0f)'
    return '(%sf)' % float.hex(v)


def c_double_bits(bits):
    v = struct.unpack('>d', struct.pack('>Q', bits))[0]
    if v != v:
        return '(__builtin_nan(""))'
    if v in (float('inf'), float('-inf')):
        return '(__builtin_inf())' if v > 0 else '(-__builtin_inf())'
    if v == 0.0 and bits != 0:
        return '(-0.0)'
    return '(%s)' % float.hex(v)


def c_str(s):
    """chuoi C an toan cho ten / thong bao"""
    out = []
    for ch in s.encode('utf-8'):
        if ch in (34, 92):
            out.append('\\' + chr(ch))
        elif 32 <= ch < 127 and ch != 63:
            out.append(chr(ch))
        else:
            out.append('\\%03o' % ch)
    return '"' + ''.join(out) + '"'


# ============================================================================ chuong trinh


class Klass:
    def __init__(self, cf):
        self.cf = cf
        self.name = cf.name
        self.access = cf.access
        self.is_iface = bool(cf.access & ACC_IFACE)
        self.is_abstract = bool(cf.access & ACC_ABSTRACT)
        self.super = None
        self.ifaces = []
        self.subs = []
        self.fields = cf.fields
        self.methods = cf.methods
        self.mmap = {}
        for m in self.methods:
            m.cls = self
            m.is_static = bool(m.access & ACC_STATIC)
            m.is_native = bool(m.access & ACC_NATIVE)
            m.is_abstract = bool(m.access & ACC_ABSTRACT)
            m.args, m.ret = parse_desc(m.desc)
            self.mmap[(m.name, m.desc)] = m
        self.fmap = {}
        for f in self.fields:
            f.cls = self
            self.fmap[(f.name, f.desc)] = f
        self.vt = None
        self.cname = None
        self.depth = 0
        self.needs_init = False
        self.all_ifaces = None


class Program:
    def __init__(self):
        self.classes = collections.OrderedDict()
        self.lits = {}            # tuple utf16 -> idx
        self.lit_list = []
        self.sels = {}            # (name, desc) -> selector id
        self.icache_n = 0
        self.missing = collections.Counter()

    # ---------------------------------------------------------------- nap
    def load(self, src, exclude=()):
        def add(data, origin):
            cf = ClassFile(data, origin)
            if any(cf.name.startswith(p) for p in exclude):
                return
            if cf.name in self.classes:
                return  # nguon dau tien thang (thu vien truoc / game sau tuy thu tu dong lenh)
            self.classes[cf.name] = Klass(cf)
        if os.path.isdir(src):
            for root, _, files in os.walk(src):
                for f in sorted(files):
                    if f.endswith('.class'):
                        p = os.path.join(root, f)
                        add(open(p, 'rb').read(), p)
        else:
            z = zipfile.ZipFile(src)
            for n in sorted(z.namelist()):
                if n.endswith('.class'):
                    add(z.read(n), src + '!' + n)

    def k(self, name):
        return self.classes.get(name)

    # ---------------------------------------------------------------- lien ket
    def link(self):
        used = set()
        for k in self.classes.values():
            base = san(k.name)
            c = base
            n = 2
            while c in used:
                c = '%s_%d' % (base, n)
                n += 1
            used.add(c)
            k.cname = c
        for k in self.classes.values():
            if k.cf.super:
                k.super = self.k(k.cf.super)
                if k.super is None:
                    raise SystemExit('thieu lop cha %s cua %s' % (k.cf.super, k.name))
                k.super.subs.append(k)
            for i in k.cf.interfaces:
                ik = self.k(i)
                if ik is None:
                    raise SystemExit('thieu interface %s cua %s' % (i, k.name))
                k.ifaces.append(ik)
        order = []
        seen = set()

        def visit(k):
            if k.name in seen:
                return
            seen.add(k.name)
            if k.super:
                visit(k.super)
            for i in k.ifaces:
                visit(i)
            order.append(k)
        for k in list(self.classes.values()):
            visit(k)
        self.order = order
        for k in order:
            k.depth = k.super.depth + 1 if k.super else 0
            s = set()
            if k.super:
                s |= k.super.all_ifaces
            for i in k.ifaces:
                s.add(i)
                s |= i.all_ifaces
            k.all_ifaces = s
        # ten ham
        for k in order:
            names = set()
            for m in k.methods:
                mn = {'<init>': 'init', '<clinit>': 'clinit'}.get(m.name, m.name)
                base = 'M_%s_%s_%s' % (k.cname, san(mn), h4(m.desc))
                c = base
                n = 2
                while c in names:
                    c = '%s_%d' % (base, n)
                    n += 1
                names.add(c)
                m.cname = c
                if m.is_native:
                    same = sum(1 for x in k.methods if x.name == m.name)
                    m.impl_name = ('N_%s_%s' % (k.cname, san(mn))) if same == 1 else 'N' + c[1:]
                elif m.is_abstract:
                    m.impl_name = None
                else:
                    m.impl_name = c
            fnames = set()
            for f in k.fields:
                if f.access & ACC_STATIC:
                    base = 'S_%s_%s_%s' % (k.cname, san(f.name), san(f.desc[0]))
                else:
                    base = 'f_%s_%s' % (san(f.name), san(f.desc[0]))
                c = base
                n = 2
                while c in fnames:
                    c = '%s_%d' % (base, n)
                    n += 1
                fnames.add(c)
                f.cname = c
        # vtable
        for k in order:
            if k.is_iface:
                k.vt = []
                k.vindex = {}
                continue
            vt = list(k.super.vt) if k.super else []
            index = dict(k.super.vindex) if k.super else {}
            for m in k.methods:
                if m.is_static or (m.access & ACC_PRIVATE) or m.name in ('<init>', '<clinit>'):
                    continue
                key = (m.name, m.desc)
                if key in index:
                    vt[index[key]] = m
                else:
                    index[key] = len(vt)
                    vt.append(m)
            # phuong thuc interface chua cai dat (lop truu tuong) -> khe rieng
            for i in sorted(k.all_ifaces, key=lambda x: x.name):
                for m in i.methods:
                    if m.is_static or m.name == '<clinit>':
                        continue
                    key = (m.name, m.desc)
                    if key not in index:
                        index[key] = len(vt)
                        vt.append(m)
            k.vt = vt
            k.vindex = index
        for k in order:
            for i, m in enumerate(k.vt):
                if m.cls is k and m.vslot is None:
                    m.vslot = i
        # khoi tao lop
        for k in order:
            own = ('<clinit>', '()V') in k.mmap or any(
                (f.access & ACC_STATIC) and f.const is not None and f.const[0] == 8 for f in k.fields)
            k.has_own_init = own
            k.needs_init = own or (not k.is_iface and k.super is not None and k.super.needs_init)
        # selector interface
        for k in order:
            if k.is_iface:
                for m in k.methods:
                    if not m.is_static and m.name != '<clinit>':
                        self.sel((m.name, m.desc))

    def sel(self, key):
        if key not in self.sels:
            self.sels[key] = len(self.sels)
        return self.sels[key]

    def lit(self, units):
        key = tuple(units)
        if key not in self.lits:
            self.lits[key] = len(self.lit_list)
            self.lit_list.append(key)
        return self.lits[key]

    # ---------------------------------------------------------------- phan giai
    def resolve_field(self, cname, name, desc):
        k = self.k(cname)
        while k is not None:
            f = k.fmap.get((name, desc))
            if f:
                return f
            for i in k.all_ifaces:
                f = i.fmap.get((name, desc))
                if f:
                    return f
            k = k.super
        return None

    def resolve_method(self, cname, name, desc):
        k0 = self.k(cname)
        k = k0
        while k is not None:
            m = k.mmap.get((name, desc))
            if m:
                return m
            k = k.super
        if k0 is not None:
            for i in sorted(k0.all_ifaces, key=lambda x: x.name):
                m = i.mmap.get((name, desc))
                if m:
                    return m
        return None

    def resolve_imethod(self, cname, name, desc):
        k0 = self.k(cname)
        if k0 is None:
            return None
        m = k0.mmap.get((name, desc))
        if m:
            return m
        for i in sorted(k0.all_ifaces, key=lambda x: x.name):
            m = i.mmap.get((name, desc))
            if m:
                return m
        return self.resolve_method('java/lang/Object', name, desc)

    def subclasses(self, k):
        out = []
        st = [k]
        while st:
            x = st.pop()
            out.append(x)
            st.extend(x.subs)
        return out

    def is_sub(self, a, b):
        """a la b hoac lop con cua b"""
        while a is not None:
            if a is b:
                return True
            a = a.super
        return False

    def impl_at(self, k, slot):
        m = k.vt[slot]
        return m.impl_name

    def devirt(self, k, slot):
        """ten ham neu moi lop con (khong truu tuong) cua k co cung cai dat o khe slot"""
        impls = set()
        for x in self.subclasses(k):
            if x.is_iface:
                continue
            if x.is_abstract and x is not k:
                continue
            impls.add(x.vt[slot].impl_name)
        if len(impls) == 1:
            v = impls.pop()
            return v
        return None


# ============================================================================ dich 1 ham

OPN = {}


def _ops():
    names = '''nop aconst_null iconst_m1 iconst_0 iconst_1 iconst_2 iconst_3 iconst_4 iconst_5 lconst_0 lconst_1
    fconst_0 fconst_1 fconst_2 dconst_0 dconst_1 bipush sipush ldc ldc_w ldc2_w iload lload fload dload aload
    iload_0 iload_1 iload_2 iload_3 lload_0 lload_1 lload_2 lload_3 fload_0 fload_1 fload_2 fload_3 dload_0 dload_1
    dload_2 dload_3 aload_0 aload_1 aload_2 aload_3 iaload laload faload daload aaload baload caload saload istore
    lstore fstore dstore astore istore_0 istore_1 istore_2 istore_3 lstore_0 lstore_1 lstore_2 lstore_3 fstore_0
    fstore_1 fstore_2 fstore_3 dstore_0 dstore_1 dstore_2 dstore_3 astore_0 astore_1 astore_2 astore_3 iastore
    lastore fastore dastore aastore bastore castore sastore pop pop2 dup dup_x1 dup_x2 dup2 dup2_x1 dup2_x2 swap
    iadd ladd fadd dadd isub lsub fsub dsub imul lmul fmul dmul idiv ldiv fdiv ddiv irem lrem frem drem ineg lneg
    fneg dneg ishl lshl ishr lshr iushr lushr iand land ior lor ixor lxor iinc i2l i2f i2d l2i l2f l2d f2i f2l f2d
    d2i d2l d2f i2b i2c i2s lcmp fcmpl fcmpg dcmpl dcmpg ifeq ifne iflt ifge ifgt ifle if_icmpeq if_icmpne
    if_icmplt if_icmpge if_icmpgt if_icmple if_acmpeq if_acmpne goto jsr ret tableswitch lookupswitch ireturn
    lreturn freturn dreturn areturn return getstatic putstatic getfield putfield invokevirtual invokespecial
    invokestatic invokeinterface invokedynamic new newarray anewarray arraylength athrow checkcast instanceof
    monitorenter monitorexit wide multianewarray ifnull ifnonnull goto_w jsr_w'''.split()
    for i, n in enumerate(names):
        OPN[i] = n


_ops()
OPLEN = {}
for o in list(range(0x00, 0x10)) + list(range(0x1a, 0x36)) + list(range(0x3b, 0x84)) + list(range(0x85, 0x99)) + \
        [0xac, 0xad, 0xae, 0xaf, 0xb0, 0xb1, 0xbe, 0xbf, 0xc2, 0xc3]:
    OPLEN[o] = 1
for o in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc):
    OPLEN[o] = 2
for o in (0x11, 0x13, 0x14, 0x84, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7) + \
        tuple(range(0x99, 0xa9)):
    OPLEN[o] = 3
OPLEN[0xc5] = 4
OPLEN[0xb9] = 5
OPLEN[0xba] = 5
OPLEN[0xc8] = 5
OPLEN[0xc9] = 5


class Inst:
    __slots__ = ('pc', 'op', 'ln', 'a', 'b', 'targets', 'keys')


def decode(code):
    out = []
    o = 0
    n = len(code)
    while o < n:
        op = code[o]
        it = Inst()
        it.pc, it.op, it.a, it.b, it.targets, it.keys = o, op, None, None, None, None
        if op == 0xc4:
            op2 = code[o + 1]
            it.op = op2
            it.a = u2(code, o + 2)
            if op2 == 0x84:
                it.b = s2(code, o + 4)
                ln = 6
            else:
                ln = 4
        elif op == 0xaa:
            p = (o + 4) & ~3
            d, lo, hi = s4(code, p), s4(code, p + 4), s4(code, p + 8)
            it.keys = list(range(lo, hi + 1))
            it.targets = [o + d] + [o + s4(code, p + 12 + 4 * k) for k in range(hi - lo + 1)]
            ln = p + 12 + 4 * (hi - lo + 1) - o
        elif op == 0xab:
            p = (o + 4) & ~3
            d, npairs = s4(code, p), s4(code, p + 4)
            it.keys = [s4(code, p + 8 + 8 * k) for k in range(npairs)]
            it.targets = [o + d] + [o + s4(code, p + 8 + 8 * k + 4) for k in range(npairs)]
            ln = p + 8 + 8 * npairs - o
        else:
            ln = OPLEN.get(op)
            if ln is None:
                raise ValueError('opcode 0x%02x' % op)
            if op in (0x10,):
                it.a = s1(code, o + 1)
            elif op in (0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc):
                it.a = code[o + 1]
            elif op == 0x11:
                it.a = s2(code, o + 1)
            elif op == 0x84:
                it.a = code[o + 1]
                it.b = s1(code, o + 2)
            elif op in (0x13, 0x14, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xb9, 0xba):
                it.a = u2(code, o + 1)
                if op == 0xb9:
                    it.b = code[o + 3]
            elif op == 0xc5:
                it.a = u2(code, o + 1)
                it.b = code[o + 3]
            elif 0x99 <= op <= 0xa8 or op in (0xc6, 0xc7):
                it.targets = [o + s2(code, o + 1)]
            elif op in (0xc8, 0xc9):
                it.targets = [o + s4(code, o + 1)]
        it.ln = ln
        out.append(it)
        o += ln
    return out


class GenError(Exception):
    pass


class MethodGen:
    def __init__(self, prog, k, m, out_statics):
        self.p = prog
        self.k = k
        self.m = m
        self.cf = k.cf
        self.lines = []
        self.svars = set()
        self.lvars = set()
        self.statics = out_statics   # bien tinh muc tep (inline cache ...)
        self.xlabels = {}            # tuple handlers -> nhan
        self.need_xr = False

    # ------------------------------------------------------------ suy luan kieu ngan xep
    def infer(self):
        insts = self.insts
        state = {}
        work = []

        def merge(pc, st):
            if pc not in self.by_pc:
                raise GenError('nhay toi pc %d khong hop le' % pc)
            if pc not in state:
                state[pc] = st
                work.append(pc)
            elif state[pc] != st:
                a, b = state[pc], st
                if len(a) != len(b) or any(x != y for x, y in zip(a, b)):
                    raise GenError('ngan xep khong khop o pc %d: %s / %s' % (pc, a, b))
        merge(0, ())
        while work:
            pc = work.pop()
            st = state[pc]
            it = self.by_pc[pc]
            new, succ = self.effect(it, st)
            for s in succ:
                merge(s, new)
            for (sp, ep, hp, ct) in self.m.exc:
                if sp <= pc < ep:
                    merge(hp, ('A',))
        self.state = state

    def effect(self, it, st):
        op = it.op
        st = list(st)
        nxt = it.pc + it.ln

        def pop(n=1):
            for _ in range(n):
                st.pop()

        def popk():
            return st.pop()
        if op == 0x00:
            pass
        elif op == 0x01:
            st.append('A')
        elif 0x02 <= op <= 0x08 or op in (0x10, 0x11):
            st.append('I')
        elif op in (0x09, 0x0a):
            st.append('J')
        elif 0x0b <= op <= 0x0d:
            st.append('F')
        elif op in (0x0e, 0x0f):
            st.append('D')
        elif op in (0x12, 0x13):
            idx = it.a
            tag = self.cf.cp[idx][0]
            st.append({3: 'I', 4: 'F', 8: 'A', 7: 'A'}[tag])
        elif op == 0x14:
            tag = self.cf.cp[it.a][0]
            st.append({5: 'J', 6: 'D'}[tag])
        elif 0x15 <= op <= 0x19:
            st.append('IJFDA'[op - 0x15])
        elif 0x1a <= op <= 0x2d:
            st.append('IJFDA'[(op - 0x1a) // 4])
        elif 0x2e <= op <= 0x35:
            pop(2)
            st.append('IJFDAIII'[op - 0x2e])
        elif 0x36 <= op <= 0x3a or 0x3b <= op <= 0x4e:
            pop()
        elif 0x4f <= op <= 0x56:
            pop(3)
        elif op == 0x57:
            pop()
        elif op == 0x58:
            t = popk()
            if size(t) == 1:
                pop()
        elif op == 0x59:
            st.append(st[-1])
        elif op == 0x5a:
            a = popk(); b = popk(); st += [a, b, a]
        elif op == 0x5b:
            a = popk(); b = popk()
            if size(b) == 2:
                st += [a, b, a]
            else:
                c = popk(); st += [a, c, b, a]
        elif op == 0x5c:
            a = popk()
            if size(a) == 2:
                st += [a, a]
            else:
                b = popk(); st += [b, a, b, a]
        elif op == 0x5d:
            a = popk()
            if size(a) == 2:
                b = popk(); st += [a, b, a]
            else:
                b = popk(); c = popk(); st += [b, a, c, b, a]
        elif op == 0x5e:
            a = popk()
            if size(a) == 2:
                b = popk()
                if size(b) == 2:
                    st += [a, b, a]
                else:
                    c = popk(); st += [a, c, b, a]
            else:
                b = popk(); c = popk()
                if size(c) == 2:
                    st += [b, a, c, b, a]
                else:
                    d = popk(); st += [b, a, d, c, b, a]
        elif op == 0x5f:
            a = popk(); b = popk(); st += [a, b]
        elif 0x60 <= op <= 0x73:
            t = 'IJFD'[(op - 0x60) % 4]
            pop(2); st.append(t)
        elif 0x74 <= op <= 0x77:
            pass
        elif 0x78 <= op <= 0x7d:
            pop(2); st.append('IJ'[(op - 0x78) % 2])
        elif 0x7e <= op <= 0x83:
            pop(2); st.append('IJ'[(op - 0x7e) % 2])
        elif op == 0x84:
            pass
        elif 0x85 <= op <= 0x93:
            res = {0x85: 'J', 0x86: 'F', 0x87: 'D', 0x88: 'I', 0x89: 'F', 0x8a: 'D', 0x8b: 'I', 0x8c: 'J', 0x8d: 'D',
                   0x8e: 'I', 0x8f: 'J', 0x90: 'F', 0x91: 'I', 0x92: 'I', 0x93: 'I'}[op]
            pop(); st.append(res)
        elif 0x94 <= op <= 0x98:
            pop(2); st.append('I')
        elif 0x99 <= op <= 0x9e:
            pop()
            return st, [it.targets[0], nxt]
        elif 0x9f <= op <= 0xa6:
            pop(2)
            return st, [it.targets[0], nxt]
        elif op in (0xa7, 0xc8):
            return st, [it.targets[0]]
        elif op in (0xa8, 0xa9, 0xc9):
            raise GenError('jsr/ret khong ho tro')
        elif op in (0xaa, 0xab):
            pop()
            return st, list(it.targets)
        elif 0xac <= op <= 0xb1:
            return st, []
        elif op == 0xb2:
            c, n, d = self.cf.ref(it.a)
            st.append(kind(d))
        elif op == 0xb3:
            pop()
        elif op == 0xb4:
            c, n, d = self.cf.ref(it.a)
            pop(); st.append(kind(d))
        elif op == 0xb5:
            pop(2)
        elif op in (0xb6, 0xb7, 0xb8, 0xb9):
            c, n, d = self.cf.ref(it.a)
            args, ret = parse_desc(d)
            pop(len(args) + (0 if op == 0xb8 else 1))
            if ret != 'V':
                st.append(kind(ret))
        elif op == 0xba:
            raise GenError('invokedynamic khong ho tro')
        elif op == 0xbb:
            st.append('A')
        elif op in (0xbc, 0xbd):
            pop(); st.append('A')
        elif op == 0xbe:
            pop(); st.append('I')
        elif op == 0xbf:
            return st, []
        elif op == 0xc0:
            pass
        elif op == 0xc1:
            pop(); st.append('I')
        elif op in (0xc2, 0xc3):
            pop()
        elif op == 0xc5:
            pop(it.b); st.append('A')
        elif op in (0xc6, 0xc7):
            pop()
            return st, [it.targets[0], nxt]
        else:
            raise GenError('opcode 0x%02x' % op)
        return st, [nxt]

    # ------------------------------------------------------------ sinh C
    def emit(self, s):
        self.lines.append(s)

    def xlabel(self, pc):
        hs = tuple((ct, hp) for (sp, ep, hp, ct) in self.m.exc if sp <= pc < ep)
        # chi giu toi lan bat moi kieu dau tien (catch-all cat duoi)
        out = []
        for ct, hp in hs:
            out.append((ct, hp))
            if ct is None or ct == 'java/lang/Throwable':
                break
        hs = tuple(out)
        if not hs:
            self.need_xr = True
            return 'XR'
        if hs not in self.xlabels:
            self.xlabels[hs] = 'XH%d' % len(self.xlabels)
        return self.xlabels[hs]

    def cls_expr(self, name):
        """bieu thuc C (jclass*) cho ten lop hoac mo ta mang"""
        if name.startswith('['):
            comp = name[1:]
            if comp[0] in 'ZBCSIJFD' and len(comp) == 1:
                return '(&rt_arr_%s)' % comp
            if comp[0] == 'L':
                ce = self.cls_expr(comp[1:-1])
            else:
                ce = self.cls_expr(comp)
            if ce is None:
                return None
            return 'rt_arrof(t, %s)' % ce
        k = self.p.k(name)
        if k is None:
            return None
        return '(&K_%s)' % k.cname

    def static_ok(self, k):
        """khong can kiem tra khoi tao lop k khi dang o trong lop hien tai"""
        if not k.needs_init:
            return True
        return self.p.is_sub(self.k, k) and not k.is_iface

    def missing(self, what, xl, kind_name='java/lang/NoSuchMethodError'):
        self.p.missing[what] += 1
        self.emit('    rt_err(t, %s, %s); goto %s;' % (c_str(kind_name), c_str(what), xl))

    def gen(self):
        m = self.m
        self.insts = decode(m.code)
        self.by_pc = {it.pc: it for it in self.insts}
        self.infer()
        targets = set()
        for it in self.insts:
            if it.targets and it.pc in self.state:
                targets.update(it.targets)
        for (sp, ep, hp, ct) in m.exc:
            targets.add(hp)
        self.targets = targets
        body = []
        self.lines = body
        for it in self.insts:
            if it.pc not in self.state:
                continue
            if it.pc in targets:
                self.emit('L%d:;' % it.pc)
            self.gen_inst(it, self.state[it.pc])
        # nhan xu ly ngoai le
        for hs, lab in self.xlabels.items():
            self.emit('%s:' % lab)
            for ct, hp in hs:
                hst = self.state.get(hp)
                if hst is None:
                    continue
                if ct is None:
                    self.emit('    s0A = t->exc; t->exc = 0; goto L%d;' % hp)
                    self.svars.add((0, 'A'))
                    break
                ce = self.cls_expr(ct)
                if ce is None:
                    continue
                self.svars.add((0, 'A'))
                self.emit('    if (rt_isinst(t->exc, %s)) { s0A = t->exc; t->exc = 0; goto L%d; }' % (ce, hp))
            else:
                self.need_xr = True
                self.emit('    goto XR;')
                continue
            if any(ct is None for ct, hp in hs):
                continue
            self.need_xr = True
            self.emit('    goto XR;')
        ret = kind(m.ret)
        if self.need_xr:
            self.emit('XR:')
            self.emit('    return%s;' % ('' if ret == 'V' else ' ' + ('(jobj)0' if ret == 'A' else '0')))
        return body

    # ------------------------------------------------------------ 1 lenh
    def gen_inst(self, it, pre):
        op = it.op
        stk = list(pre)
        dep = []
        d = 0
        for x in stk:
            dep.append(d)
            d += size(x)
        cur = [d]
        p = self.p
        E = self.emit

        def var(kd, dp):
            self.svars.add((dp, kd))
            return 's%d%s' % (dp, kd)

        def pop():
            kd = stk.pop()
            dp = dep.pop()
            cur[0] = dp
            return var(kd, dp)

        def peek(i=0):
            return var(stk[-1 - i], dep[-1 - i])

        def push(kd):
            dp = cur[0]
            stk.append(kd)
            dep.append(dp)
            cur[0] = dp + size(kd)
            return var(kd, dp)

        def lv(idx, kd):
            self.lvars.add((idx, kd))
            return 'l%d%s' % (idx, kd)
        xl = None

        def X():
            nonlocal xl
            if xl is None:
                xl = self.xlabel(it.pc)
            return xl
        n = OPN.get(op, '?')
        if op == 0x00:
            return
        if op == 0x01:
            E('    %s = 0;' % push('A')); return
        if 0x02 <= op <= 0x08:
            E('    %s = %d;' % (push('I'), op - 3)); return
        if op in (0x09, 0x0a):
            E('    %s = %d;' % (push('J'), op - 9)); return
        if 0x0b <= op <= 0x0d:
            E('    %s = %d.0f;' % (push('F'), op - 0x0b)); return
        if op in (0x0e, 0x0f):
            E('    %s = %d.0;' % (push('D'), op - 0x0e)); return
        if op in (0x10, 0x11):
            E('    %s = %d;' % (push('I'), it.a)); return
        if op in (0x12, 0x13, 0x14):
            e = self.cf.cp[it.a]
            tag = e[0]
            if tag == 3:
                E('    %s = %s;' % (push('I'), c_int(e[1])))
            elif tag == 4:
                E('    %s = %s;' % (push('F'), c_float_bits(e[1])))
            elif tag == 5:
                E('    %s = %s;' % (push('J'), c_long(e[1])))
            elif tag == 6:
                E('    %s = %s;' % (push('D'), c_double_bits(e[1])))
            elif tag == 8:
                idx = p.lit(self.cf.utf16(e[1]))
                E('    %s = LIT(%d);' % (push('A'), idx))
            elif tag == 7:
                ce = self.cls_expr(self.cf.class_name(it.a))
                v = push('A')
                if ce is None:
                    self.missing(self.cf.class_name(it.a), X(), 'java/lang/NoClassDefFoundError')
                else:
                    E('    %s = rt_mirror(t, %s); CK(%s);' % (v, ce, X()))
            return
        if 0x15 <= op <= 0x19:
            kd = 'IJFDA'[op - 0x15]
            E('    %s = %s;' % (push(kd), lv(it.a, kd))); return
        if 0x1a <= op <= 0x2d:
            kd = 'IJFDA'[(op - 0x1a) // 4]
            idx = (op - 0x1a) % 4
            E('    %s = %s;' % (push(kd), lv(idx, kd))); return
        if 0x2e <= op <= 0x35:
            i = pop(); a = pop()
            et = ['int32_t', 'int64_t', 'float', 'double', 'jobj', 'int8_t', 'uint16_t', 'int16_t'][op - 0x2e]
            kd = 'IJFDAIII'[op - 0x2e]
            r = push(kd)
            E('    NN(%s, %s); AB(%s, %s, %s); %s = AD(%s, %s)[%s];' % (a, X(), a, i, X(), r, a, et, i))
            return
        if 0x36 <= op <= 0x3a:
            kd = 'IJFDA'[op - 0x36]
            E('    %s = %s;' % (lv(it.a, kd), pop())); return
        if 0x3b <= op <= 0x4e:
            kd = 'IJFDA'[(op - 0x3b) // 4]
            idx = (op - 0x3b) % 4
            E('    %s = %s;' % (lv(idx, kd), pop())); return
        if 0x4f <= op <= 0x56:
            v = pop(); i = pop(); a = pop()
            et = ['int32_t', 'int64_t', 'float', 'double', 'jobj', 'int8_t', 'uint16_t', 'int16_t'][op - 0x4f]
            if op == 0x53:
                E('    NN(%s, %s); AB(%s, %s, %s); ASTORE_CHK(%s, %s, %s); AD(%s, jobj)[%s] = %s;' % (
                    a, X(), a, i, X(), a, v, X(), a, i, v))
            else:
                E('    NN(%s, %s); AB(%s, %s, %s); AD(%s, %s)[%s] = (%s)%s;' % (a, X(), a, i, X(), a, et, i, et, v))
            return
        if op == 0x57:
            pop(); return
        if op == 0x58:
            if size(stk[-1]) == 2:
                pop()
            else:
                pop(); pop()
            return
        if 0x59 <= op <= 0x5f:
            # thao tac ngan xep: tinh danh sach gia tri truoc/sau theo kieu
            vals = list(zip(stk, dep))
            before = list(stk)

            def cat(i):
                return size(stk[-1 - i])
            if op == 0x59:
                pat = (1, [0, 0])
            elif op == 0x5a:
                pat = (2, [0, 1, 0])
            elif op == 0x5b:
                pat = (2, [0, 1, 0]) if cat(1) == 2 else (3, [0, 2, 1, 0])
            elif op == 0x5c:
                pat = (1, [0, 0]) if cat(0) == 2 else (2, [1, 0, 1, 0])
            elif op == 0x5d:
                pat = (2, [0, 1, 0]) if cat(0) == 2 else (3, [1, 0, 2, 1, 0])
            elif op == 0x5e:
                if cat(0) == 2:
                    pat = (2, [0, 1, 0]) if cat(1) == 2 else (3, [0, 2, 1, 0])
                else:
                    pat = (3, [1, 0, 2, 1, 0]) if cat(2) == 2 else (4, [1, 0, 3, 2, 1, 0])
            else:
                pat = (2, [0, 1])
            npop, order = pat
            olds = [peek(i) for i in range(npop)]           # olds[i] = phan tu thu i tu dinh
            kinds = [stk[-1 - i] for i in range(npop)]
            for _ in range(npop):
                pop()
            tmp = []
            for i in range(npop):
                tv = '_t%d' % i
                tmp.append(tv)
            E('    { %s' % ' '.join('%s %s = %s;' % (CTYPE[kinds[i]], tmp[i], olds[i]) for i in range(npop)))
            for idx in order:
                E('      %s = %s;' % (push(kinds[idx]), tmp[idx]))
            E('    }')
            return
        if 0x60 <= op <= 0x77:
            base = (op - 0x60) // 4
            kd = 'IJFD'[(op - 0x60) % 4]
            if base == 5:   # neg
                a = pop()
                r = push(kd)
                if kd == 'I':
                    E('    %s = (int32_t)(0u - (uint32_t)%s);' % (r, a))
                elif kd == 'J':
                    E('    %s = (int64_t)(UINT64_C(0) - (uint64_t)%s);' % (r, a))
                else:
                    E('    %s = -%s;' % (r, a))
                return
            b = pop(); a = pop(); r = push(kd)
            if base == 0:
                if kd == 'I':
                    E('    %s = (int32_t)((uint32_t)%s + (uint32_t)%s);' % (r, a, b))
                elif kd == 'J':
                    E('    %s = (int64_t)((uint64_t)%s + (uint64_t)%s);' % (r, a, b))
                else:
                    E('    %s = %s + %s;' % (r, a, b))
            elif base == 1:
                if kd == 'I':
                    E('    %s = (int32_t)((uint32_t)%s - (uint32_t)%s);' % (r, a, b))
                elif kd == 'J':
                    E('    %s = (int64_t)((uint64_t)%s - (uint64_t)%s);' % (r, a, b))
                else:
                    E('    %s = %s - %s;' % (r, a, b))
            elif base == 2:
                if kd == 'I':
                    E('    %s = (int32_t)((uint32_t)%s * (uint32_t)%s);' % (r, a, b))
                elif kd == 'J':
                    E('    %s = (int64_t)((uint64_t)%s * (uint64_t)%s);' % (r, a, b))
                else:
                    E('    %s = %s * %s;' % (r, a, b))
            elif base == 3:
                if kd == 'I':
                    E('    if (UNLIKELY(%s == 0)) { rt_arith(t); goto %s; } %s = (%s == -1) ? (int32_t)(0u - (uint32_t)%s) : %s / %s;'
                      % (b, X(), r, b, a, a, b))
                elif kd == 'J':
                    E('    if (UNLIKELY(%s == 0)) { rt_arith(t); goto %s; } %s = (%s == -1) ? (int64_t)(UINT64_C(0) - (uint64_t)%s) : %s / %s;'
                      % (b, X(), r, b, a, a, b))
                else:
                    E('    %s = %s / %s;' % (r, a, b))
            elif base == 4:
                if kd in 'IJ':
                    E('    if (UNLIKELY(%s == 0)) { rt_arith(t); goto %s; } %s = (%s == -1) ? 0 : %s %% %s;'
                      % (b, X(), r, b, a, b))
                elif kd == 'F':
                    E('    %s = fmodf(%s, %s);' % (r, a, b))
                else:
                    E('    %s = fmod(%s, %s);' % (r, a, b))
            return
        if 0x78 <= op <= 0x7d:
            b = pop(); a = pop()
            kd = 'IJ'[(op - 0x78) % 2]
            r = push(kd)
            sh = (op - 0x78) // 2
            if kd == 'I':
                E('    %s = %s;' % (r, ['(int32_t)((uint32_t)%s << (%s & 31))', '%s >> (%s & 31)',
                                         '(int32_t)((uint32_t)%s >> (%s & 31))'][sh] % (a, b)))
            else:
                E('    %s = %s;' % (r, ['(int64_t)((uint64_t)%s << (%s & 63))', '%s >> (%s & 63)',
                                         '(int64_t)((uint64_t)%s >> (%s & 63))'][sh] % (a, b)))
            return
        if 0x7e <= op <= 0x83:
            b = pop(); a = pop()
            kd = 'IJ'[(op - 0x7e) % 2]
            r = push(kd)
            E('    %s = %s %s %s;' % (r, a, '&|^'[(op - 0x7e) // 2], b))
            return
        if op == 0x84:
            v = lv(it.a, 'I')
            E('    %s = (int32_t)((uint32_t)%s + (uint32_t)(%d));' % (v, v, it.b))
            return
        if 0x85 <= op <= 0x93:
            a = pop()
            conv = {0x85: ('J', '(int64_t)%s'), 0x86: ('F', '(float)%s'), 0x87: ('D', '(double)%s'),
                    0x88: ('I', '(int32_t)%s'), 0x89: ('F', '(float)%s'), 0x8a: ('D', '(double)%s'),
                    0x8b: ('I', 'rt_f2i(%s)'), 0x8c: ('J', 'rt_f2l(%s)'), 0x8d: ('D', '(double)%s'),
                    0x8e: ('I', 'rt_d2i(%s)'), 0x8f: ('J', 'rt_d2l(%s)'), 0x90: ('F', '(float)%s'),
                    0x91: ('I', '(int32_t)(int8_t)%s'), 0x92: ('I', '(int32_t)(uint16_t)%s'),
                    0x93: ('I', '(int32_t)(int16_t)%s')}[op]
            r = push(conv[0])
            E('    %s = %s;' % (r, conv[1] % a))
            return
        if op == 0x94:
            b = pop(); a = pop(); r = push('I')
            E('    %s = (%s > %s) - (%s < %s);' % (r, a, b, a, b)); return
        if op in (0x95, 0x97):
            b = pop(); a = pop(); r = push('I')
            E('    %s = (%s > %s) ? 1 : (%s == %s) ? 0 : -1;' % (r, a, b, a, b)); return
        if op in (0x96, 0x98):
            b = pop(); a = pop(); r = push('I')
            E('    %s = (%s < %s) ? -1 : (%s == %s) ? 0 : 1;' % (r, a, b, a, b)); return
        if 0x99 <= op <= 0x9e:
            a = pop()
            E('    if (%s %s 0) goto L%d;' % (a, ['==', '!=', '<', '>=', '>', '<='][op - 0x99], it.targets[0]))
            return
        if 0x9f <= op <= 0xa6:
            b = pop(); a = pop()
            E('    if (%s %s %s) goto L%d;' % (a, ['==', '!=', '<', '>=', '>', '<=', '==', '!='][op - 0x9f], b,
                                              it.targets[0]))
            return
        if op in (0xa7, 0xc8):
            E('    goto L%d;' % it.targets[0]); return
        if op in (0xaa, 0xab):
            a = pop()
            E('    switch (%s) {' % a)
            for key, tg in zip(it.keys, it.targets[1:]):
                E('    case %s: goto L%d;' % (c_int(key), tg))
            E('    default: goto L%d;' % it.targets[0])
            E('    }')
            return
        if 0xac <= op <= 0xb0:
            a = pop()
            E('    return %s;' % a); return
        if op == 0xb1:
            E('    return;'); return
        if op in (0xb2, 0xb3):
            c, nme, d = self.cf.ref(it.a)
            f = p.resolve_field(c, nme, d)
            if f is None or not (f.access & ACC_STATIC):
                if op == 0xb3:
                    pop()
                else:
                    E('    %s = %s;' % (push(kind(d)), ZERO[kind(d)]))
                self.missing('%s.%s' % (c, nme), X(), 'java/lang/NoSuchFieldError')
                return
            fk = f.cls
            if not self.static_ok(fk):
                E('    CI(K_%s, %s);' % (fk.cname, X()))
            if op == 0xb2:
                E('    %s = %s;' % (push(kind(d)), f.cname))
            else:
                v = pop()
                E('    %s = (%s)%s;' % (f.cname, STORE[d[0]], v))
            return
        if op in (0xb4, 0xb5):
            c, nme, d = self.cf.ref(it.a)
            f = p.resolve_field(c, nme, d)
            if op == 0xb5:
                v = pop()
            o = pop()
            if f is None or (f.access & ACC_STATIC):
                if op == 0xb4:
                    E('    %s = %s;' % (push(kind(d)), ZERO[kind(d)]))
                self.missing('%s.%s' % (c, nme), X(), 'java/lang/NoSuchFieldError')
                return
            acc = '((struct O_%s *)%s)->%s' % (f.cls.cname, o, f.cname)
            if op == 0xb4:
                r = push(kind(d))
                E('    NN(%s, %s); %s = %s;' % (o, X(), r, acc))
            else:
                E('    NN(%s, %s); %s = (%s)%s;' % (o, X(), acc, STORE[d[0]], v))
            return
        if op in (0xb6, 0xb7, 0xb8, 0xb9):
            self.gen_invoke(it, op, stk, dep, cur, pop, push, X)
            return
        if op == 0xbb:
            cn = self.cf.class_name(it.a)
            k = p.k(cn)
            r = push('A')
            if k is None:
                self.missing(cn, X(), 'java/lang/NoClassDefFoundError')
                return
            if not self.static_ok(k):
                E('    CI(K_%s, %s);' % (k.cname, X()))
            E('    %s = rt_new(t, &K_%s); CK(%s);' % (r, k.cname, X()))
            return
        if op == 0xbc:
            n_ = pop()
            r = push('A')
            E('    %s = rt_newarr(t, &rt_arr_%s, %s); CK(%s);' % (r, NEWARRAY[it.a], n_, X()))
            return
        if op == 0xbd:
            n_ = pop()
            r = push('A')
            cn = self.cf.class_name(it.a)
            ce = self.cls_expr(cn)
            if ce is None:
                self.missing(cn, X(), 'java/lang/NoClassDefFoundError')
                return
            E('    { jclass *_ac = rt_arrof(t, %s); CK(%s); %s = rt_newarr(t, _ac, %s); CK(%s); }' % (
                ce, X(), r, n_, X()))
            return
        if op == 0xbe:
            a = pop()
            r = push('I')
            E('    NN(%s, %s); %s = ALEN(%s);' % (a, X(), r, a))
            return
        if op == 0xbf:
            a = pop()
            E('    NN(%s, %s); t->exc = %s; goto %s;' % (a, X(), a, X()))
            return
        if op == 0xc0:
            a = peek()
            cn = self.cf.class_name(it.a)
            ce = self.cls_expr(cn)
            if ce is None:
                E('    if (%s) { rt_err(t, "java/lang/NoClassDefFoundError", %s); goto %s; }' % (a, c_str(cn), X()))
                return
            k = p.k(cn)
            if k is not None and not k.is_iface:
                E('    if (%s && !rt_sub(%s->cls, %s)) { rt_cce(t, %s, %s); goto %s; }' % (a, a, ce, a, ce, X()))
            else:
                E('    if (%s && !rt_isinst(%s, %s)) { rt_cce(t, %s, %s); goto %s; }' % (a, a, ce, a, ce, X()))
            return
        if op == 0xc1:
            a = pop()
            r = push('I')
            cn = self.cf.class_name(it.a)
            ce = self.cls_expr(cn)
            if ce is None:
                E('    %s = 0;' % r)
                return
            k = p.k(cn)
            if k is not None and not k.is_iface:
                E('    %s = %s ? rt_sub(%s->cls, %s) : 0;' % (r, a, a, ce))
            else:
                E('    %s = %s ? rt_isinst(%s, %s) : 0;' % (r, a, a, ce))
            return
        if op == 0xc2:
            a = pop()
            E('    NN(%s, %s); rt_menter(t, %s);' % (a, X(), a))
            return
        if op == 0xc3:
            a = pop()
            E('    NN(%s, %s); rt_mexit(t, %s); CK(%s);' % (a, X(), a, X()))
            return
        if op == 0xc5:
            nd = it.b
            dims = [pop() for _ in range(nd)][::-1]
            r = push('A')
            cn = self.cf.class_name(it.a)
            ce = self.cls_expr(cn)
            if ce is None:
                self.missing(cn, X(), 'java/lang/NoClassDefFoundError')
                return
            E('    { int32_t _d[%d] = {%s}; jclass *_ac = %s; CK(%s); %s = rt_multianew(t, _ac, %d, _d); CK(%s); }'
              % (nd, ', '.join(dims), ce, X(), r, nd, X()))
            return
        if op in (0xc6, 0xc7):
            a = pop()
            E('    if (%s %s 0) goto L%d;' % (a, '==' if op == 0xc6 else '!=', it.targets[0]))
            return
        raise GenError('chua dich opcode %s' % n)

    def fntype(self, m, recv):
        args = [CTYPE[kind(a)] for a in m.args]
        ps = ['jctx *'] + (['jobj'] if recv else []) + args
        return '%s (*)(%s)' % (CTYPE[kind(m.ret)], ', '.join(ps))

    def gen_invoke(self, it, op, stk, dep, cur, pop, push, X):
        p = self.p
        E = self.emit
        c, nme, d = self.cf.ref(it.a)
        args, ret = parse_desc(d)
        avals = [pop() for _ in range(len(args))][::-1]
        recv = pop() if op != 0xb8 else None
        rk = kind(ret)

        def call(fn_expr):
            ps = ['t'] + ([recv] if recv is not None else []) + avals
            if rk == 'V':
                E('    %s(%s); CK(%s);' % (fn_expr, ', '.join(ps), X()))
            else:
                r = push(rk)
                E('    %s = %s(%s); CK(%s);' % (r, fn_expr, ', '.join(ps), X()))

        def fail(what, cls_name='java/lang/NoSuchMethodError'):
            if rk != 'V':
                E('    %s = %s;' % (push(rk), ZERO[rk]))
            self.missing(what, X(), cls_name)
        if c.startswith('['):
            # phuong thuc cua mang (clone, getClass, hashCode...) -> Object
            c = 'java/lang/Object'
        if op == 0xb9:
            m = p.resolve_imethod(c, nme, d)
            if m is None:
                fail('%s.%s%s' % (c, nme, d))
                return
            E('    NN(%s, %s);' % (recv, X()))
            if m.cls.is_iface:
                sel = p.sel((nme, d))
                ic = 'IC%d' % p.icache_n
                p.icache_n += 1
                self.statics.append('static const itab *%s;' % ic)
                E('    { const itab *_e = %s; if (UNLIKELY(!_e || _e->cls != %s->cls)) { _e = rt_ilookup(t, %s, %d); CK(%s); %s = _e; }'
                  % (ic, recv, recv, sel, X(), ic))
                fn = '((%s)_e->fn)' % self.fntype(m, True)
                ps = ['t', recv] + avals
                if rk == 'V':
                    E('      %s(%s); CK(%s); }' % (fn, ', '.join(ps), X()))
                else:
                    r = push(rk)
                    E('      %s = %s(%s); CK(%s); }' % (r, fn, ', '.join(ps), X()))
                return
            # phuong thuc cua Object qua interface
            k = m.cls
            slot = k.vindex.get((nme, d))
            E('    { void *_f = %s->cls->vt[%d];' % (recv, slot))
            fn = '((%s)_f)' % self.fntype(m, True)
            ps = ['t', recv] + avals
            if rk == 'V':
                E('      %s(%s); CK(%s); }' % (fn, ', '.join(ps), X()))
            else:
                r = push(rk)
                E('      %s = %s(%s); CK(%s); }' % (r, fn, ', '.join(ps), X()))
            return
        if op == 0xb8:
            m = p.resolve_method(c, nme, d)
            if m is None or not m.is_static:
                fail('%s.%s%s' % (c, nme, d))
                return
            if not self.static_ok(m.cls):
                E('    CI(K_%s, %s);' % (m.cls.cname, X()))
            call(m.impl_name)
            return
        if op == 0xb7:
            m = p.resolve_method(c, nme, d)
            if m is not None and nme != '<init>' and (self.k.access & 0x20) and not (m.access & ACC_PRIVATE) \
                    and self.k.super is not None and p.is_sub(self.k.super, p.k(c) or self.k.super) \
                    and p.k(c) is not self.k:
                # goi super.m(): tim tu lop cha cua lop hien tai
                m2 = p.resolve_method(self.k.super.name, nme, d)
                if m2 is not None:
                    m = m2
            if m is None or m.is_static:
                fail('%s.%s%s' % (c, nme, d))
                return
            E('    NN(%s, %s);' % (recv, X()))
            if m.impl_name is None:
                fail('abstract %s.%s%s' % (c, nme, d), 'java/lang/AbstractMethodError')
                return
            call(m.impl_name)
            return
        # invokevirtual
        m = p.resolve_method(c, nme, d)
        if m is None or m.is_static:
            fail('%s.%s%s' % (c, nme, d))
            return
        E('    NN(%s, %s);' % (recv, X()))
        if m.access & ACC_PRIVATE:
            call(m.impl_name)
            return
        k = p.k(c)
        slot = k.vindex.get((nme, d)) if not k.is_iface else None
        if slot is None:
            fail('%s.%s%s (khong co khe vtable)' % (c, nme, d))
            return
        dv = p.devirt(k, slot)
        if dv is not None:
            call(dv)
            return
        fn = '((%s)%s->cls->vt[%d])' % (self.fntype(m, True), recv, slot)
        call(fn)


# ============================================================================ xuat


def proto(m, name=None):
    ps = ['jctx *t'] + ([] if m.is_static else ['jobj self'])
    for i, a in enumerate(m.args):
        ps.append('%s p%d' % (CTYPE[kind(a)], i))
    return '%s %s(%s)' % (CTYPE[kind(m.ret)], name or m.cname, ', '.join(ps))


def gen_method(prog, k, m, statics, log):
    """tra ve chuoi C cua 1 ham"""
    out = []
    if m.code is None:
        return ''
    g = MethodGen(prog, k, m, statics)
    try:
        body = g.gen()
    except GenError as e:
        log.append('%s.%s%s: %s' % (k.name, m.name, m.desc, e))
        rk = kind(m.ret)
        name = m.cname + ('_body' if m.access & ACC_SYNC else '')
        out.append('%s {' % proto(m, name))
        out.append('    rt_err(t, "java/lang/VerifyError", %s);' % c_str('%s.%s%s' % (k.name, m.name, m.desc)))
        out.append('    return%s;' % ('' if rk == 'V' else ' 0'))
        out.append('}')
        return '\n'.join(out)
    name = m.cname + ('_body' if m.access & ACC_SYNC else '')
    sig = proto(m, name)
    out.append('%s {' % sig)
    # bien cuc bo: tham so -> lN
    params = []
    idx = 0
    if not m.is_static:
        params.append((0, 'A', 'self'))
        idx = 1
    for i, a in enumerate(m.args):
        kd = kind(a)
        params.append((idx, kd, 'p%d' % i))
        idx += size(kd)
    pset = {(i, kd) for i, kd, _ in params}
    decl = []
    for (i, kd, pn) in params:
        decl.append('    %s l%d%s = %s;' % (CTYPE[kd], i, kd, pn))
    for (i, kd) in sorted(g.lvars):
        if (i, kd) not in pset:
            decl.append('    %s l%d%s = %s;' % (CTYPE[kd], i, kd, ZERO[kd]))
    sv = sorted(g.svars)
    if sv:
        by = collections.defaultdict(list)
        for dp, kd in sv:
            by[kd].append('s%d%s' % (dp, kd))
        for kd in 'IJFDA':
            if by[kd]:
                decl.append('    %s %s;' % (CTYPE[kd], ', '.join('%s = %s' % (v, ZERO[kd]) for v in by[kd])))
    out.extend(decl)
    out.extend(body)
    out.append('}')
    if m.access & ACC_SYNC:
        rk = kind(m.ret)
        out.append('%s {' % proto(m))
        lock = 'self' if not m.is_static else 'rt_mirror(t, &K_%s)' % k.cname
        out.append('    jobj _lk = %s; rt_menter(t, _lk);' % lock)
        args = ['t'] + ([] if m.is_static else ['self']) + ['p%d' % i for i in range(len(m.args))]
        if rk == 'V':
            out.append('    %s_body(%s);' % (m.cname, ', '.join(args)))
            out.append('    rt_mexit_keep(t, _lk);')
            out.append('}')
        else:
            out.append('    %s _r = %s_body(%s);' % (CTYPE[rk], m.cname, ', '.join(args)))
            out.append('    rt_mexit_keep(t, _lk);')
            out.append('    return _r;')
            out.append('}')
    return '\n'.join(out)


def write(prog, outdir, groups):
    os.makedirs(outdir, exist_ok=True)
    order = prog.order
    log = []
    H = []
    H.append('/* sinh boi j2c.py - khong sua tay */')
    H.append('#pragma once')
    H.append('#include "rt.h"')
    H.append('')
    # cau truc
    for k in order:
        H.append('extern jclass K_%s;' % k.cname)
    H.append('')
    for k in order:
        if k.is_iface:
            continue
        H.append('struct O_%s {' % k.cname)
        if k.super is None:
            H.append('    jobject h;')
        else:
            H.append('    struct O_%s s;' % k.super.cname)
        for f in k.fields:
            if not (f.access & ACC_STATIC):
                H.append('    %s %s;' % (STORE[f.desc[0]], f.cname))
        H.append('};')
    H.append('')
    for k in order:
        for f in k.fields:
            if f.access & ACC_STATIC:
                H.append('extern %s %s;' % (STORE[f.desc[0]], f.cname))
    H.append('')
    NH = ['/* ham native can viet - sinh boi j2c.py */', '#pragma once', '#include "classes.h"']
    for k in order:
        for m in k.methods:
            if m.is_native:
                H.append('%s;' % proto(m, m.impl_name))
                NH.append('%s;  /* %s.%s%s */' % (proto(m, m.impl_name), k.name, m.name, m.desc))
            elif m.code is not None:
                H.append('%s;' % proto(m))
    # chi so vtable cho thu vien
    H.append('')
    for k in order:
        if k.is_iface:
            continue
        if not (k.name.startswith('java/') or k.name.startswith('javax/') or k.name.startswith('j2n/')):
            continue
        for (nm, ds), slot in sorted(k.vindex.items(), key=lambda x: x[1]):
            m = k.vt[slot]
            if m.cls is k:
                mn = {'<init>': 'init'}.get(nm, nm)
                H.append('#define VT_%s_%s_%s %d' % (k.cname, san(mn), h4(ds), slot))
    for (nm, ds), sid in sorted(prog.sels.items(), key=lambda x: x[1]):
        H.append('#define SEL_%s_%s %d' % (san(nm), h4(ds), sid))
    # JM_<lop>_<ten>: ten ham cua thu vien khi ten khong bi nap chong (de C goi)
    for k in order:
        if not (k.name.startswith('java/') or k.name.startswith('javax/') or k.name.startswith('j2n/')):
            continue
        cnt = collections.Counter(m.name for m in k.methods)
        for m in k.methods:
            if cnt[m.name] == 1 and m.impl_name and m.code is not None:
                mn = {'<init>': 'init', '<clinit>': 'clinit'}.get(m.name, m.name)
                H.append('#define JM_%s_%s %s' % (k.cname, san(mn), m.impl_name))
    open(os.path.join(outdir, 'classes.h'), 'w').write('\n'.join(H) + '\n')
    open(os.path.join(outdir, 'natives.h'), 'w').write('\n'.join(NH) + '\n')
    # than ham
    files = []
    gi = 0
    for grp in groups:
        statics = []
        parts = []
        for k in grp:
            parts.append('/* ================= %s */' % k.name)
            for f in k.fields:
                if f.access & ACC_STATIC:
                    init = '0'
                    if f.const is not None:
                        tag, v = f.const
                        if tag == 3:
                            init = c_int(v) if f.desc[0] not in 'ZBCS' else str(v)
                        elif tag == 4:
                            init = c_float_bits(v)
                        elif tag == 5:
                            init = c_long(v)
                        elif tag == 6:
                            init = c_double_bits(v)
                    parts.append('%s %s = %s;' % (STORE[f.desc[0]], f.cname, init))
            # ham khoi tao lop
            if k.has_own_init:
                parts.append('void I_%s(jctx *t) {' % k.cname)
                for f in k.fields:
                    if (f.access & ACC_STATIC) and f.const is not None and f.const[0] == 8:
                        idx = prog.lit(k.cf.utf16(f.const[1]))
                        parts.append('    %s = LIT(%d);' % (f.cname, idx))
                if ('<clinit>', '()V') in k.mmap:
                    parts.append('    %s(t);' % k.mmap[('<clinit>', '()V')].cname)
                parts.append('}')
            for m in k.methods:
                s = gen_method(prog, k, m, statics, log)
                if s:
                    parts.append(s)
        fn = 'j_%03d.c' % gi
        gi += 1
        txt = '/* sinh boi j2c.py */\n#include "classes.h"\n#include <math.h>\n' + '\n'.join(statics) + '\n' + \
            '\n'.join(parts) + '\n'
        open(os.path.join(outdir, fn), 'w').write(txt)
        files.append(fn)
    # meta
    M = ['/* sinh boi j2c.py */', '#include "classes.h"', '']
    for k in order:
        if k.has_own_init:
            M.append('void I_%s(jctx *t);' % k.cname)
    for k in order:
        anc = []
        x = k
        while x is not None:
            anc.append(x)
            x = x.super
        anc.reverse()
        M.append('static jclass *A_%s[] = {%s};' % (k.cname, ', '.join('&K_%s' % a.cname for a in anc)))
        ifs = sorted(k.all_ifaces, key=lambda x: x.name)
        if ifs:
            M.append('static jclass *F_%s[] = {%s};' % (k.cname, ', '.join('&K_%s' % a.cname for a in ifs)))
        if not k.is_iface and k.vt:
            ents = []
            for m in k.vt:
                ents.append('(void *)%s' % (m.impl_name if m.impl_name else 'rt_abstract'))
            M.append('static void *V_%s[] = {%s};' % (k.cname, ', '.join(ents)))
        # itable
        if not k.is_iface and not k.is_abstract:
            ents = []
            for (nm, ds), sid in sorted(prog.sels.items(), key=lambda x: x[1]):
                slot = k.vindex.get((nm, ds))
                if slot is None:
                    continue
                # chi khi lop that su cai dat interface khai bao selector nay
                ok = False
                for i in k.all_ifaces:
                    if (nm, ds) in i.mmap or any((nm, ds) in j.mmap for j in i.all_ifaces):
                        ok = True
                        break
                if not ok:
                    continue
                impl = k.vt[slot].impl_name
                ents.append('{&K_%s, %d, (void *)%s}' % (k.cname, sid, impl if impl else 'rt_abstract'))
            if ents:
                M.append('static const itab T_%s[] = {%s};' % (k.cname, ', '.join(ents)))
                k.n_itab = len(ents)
            else:
                k.n_itab = 0
        else:
            k.n_itab = 0
        # newInstance
        if not k.is_iface and not k.is_abstract and ('<init>', '()V') in k.mmap:
            M.append('static jobj NI_%s(jctx *t) { jobj o = rt_new(t, &K_%s); if (!o) return 0; %s(t, o); return o; }'
                     % (k.cname, k.cname, k.mmap[('<init>', '()V')].cname))
    M.append('')
    for k in order:
        flags = []
        if k.is_iface:
            flags.append('F_IFACE')
        if k.is_abstract:
            flags.append('F_ABSTRACT')
        if k.access & ACC_FINAL:
            flags.append('F_FINAL')
        fl = '|'.join(flags) or '0'
        isz = 'sizeof(struct O_%s)' % k.cname if not k.is_iface else '0'
        ifs = sorted(k.all_ifaces, key=lambda x: x.name)
        M.append('jclass K_%s = {' % k.cname)
        M.append('    .name = %s, .super = %s, .flags = %s, .depth = %d, .anc = A_%s,' % (
            c_str(k.name), '&K_%s' % k.super.cname if k.super else '0', fl, k.depth, k.cname))
        M.append('    .isize = %s, .nifc = %d, .ifc = %s,' % (isz, len(ifs), 'F_%s' % k.cname if ifs else '0'))
        M.append('    .itab = %s, .nitab = %d,' % ('T_%s' % k.cname if getattr(k, 'n_itab', 0) else '0', k.n_itab))
        M.append('    .init = %s, .istate = %d,' % ('I_%s' % k.cname if k.has_own_init else '0',
                                                   0 if k.needs_init else 2))
        M.append('    .vt = %s, .nvt = %d,' % ('V_%s' % k.cname if (not k.is_iface and k.vt) else '0',
                                              len(k.vt) if not k.is_iface else 0))
        ni = 'NI_%s' % k.cname if (not k.is_iface and not k.is_abstract and ('<init>', '()V') in k.mmap) else '0'
        M.append('    .newinst = %s,' % ni)
        M.append('};')
    M.append('')
    M.append('jclass *const rt_all_classes[] = {%s, 0};' % ', '.join('&K_%s' % k.cname for k in order))
    M.append('const int32_t rt_nclasses = %d;' % len(order))
    # chuoi hang
    for i, units in enumerate(prog.lit_list):
        if units:
            M.append('static const uint16_t LD%d[] = {%s};' % (i, ','.join(str(u) for u in units)))
    M.append('const uint16_t *const rt_litdat[] = {%s};' % ', '.join(
        ('LD%d' % i) if u else '0' for i, u in enumerate(prog.lit_list)) if prog.lit_list else 'const uint16_t *const rt_litdat[1];')
    M.append('const int32_t rt_litlen[] = {%s};' % (', '.join(str(len(u)) for u in prog.lit_list) or '0'))
    M.append('jobj rt_lits[%d];' % max(1, len(prog.lit_list)))
    M.append('const int32_t rt_nlits = %d;' % len(prog.lit_list))
    open(os.path.join(outdir, 'meta.c'), 'w').write('\n'.join(M) + '\n')
    files.append('meta.c')
    st = ['classes %d' % len(order), 'methods %d' % sum(len(k.methods) for k in order),
          'literals %d' % len(prog.lit_list), 'selectors %d' % len(prog.sels), 'icache %d' % prog.icache_n]
    st.append('loi dich %d' % len(log))
    st.extend(log)
    st.append('tham chieu thieu:')
    for w, c in prog.missing.most_common():
        st.append('  %4d %s' % (c, w))
    open(os.path.join(outdir, 'stats.txt'), 'w').write('\n'.join(st) + '\n')
    open(os.path.join(outdir, 'files.txt'), 'w').write('\n'.join(files) + '\n')
    return log


def main():
    args = sys.argv[1:]
    out = 'out'
    exclude = []
    srcs = []
    per_file = 60000
    i = 0
    while i < len(args):
        a = args[i]
        if a == '-o':
            out = args[i + 1]; i += 2; continue
        if a == '--exclude-prefix':
            exclude.append(args[i + 1]); i += 2; continue
        if a == '--group-bytes':
            per_file = int(args[i + 1]); i += 2; continue
        srcs.append(a)
        i += 1
    prog = Program()
    for s in srcs:
        prog.load(s, exclude)
    prog.link()
    # chia nhom theo kich thuoc bytecode
    groups = []
    cur = []
    tot = 0
    for k in prog.order:
        sz = sum(len(m.code) for m in k.methods if m.code)
        if cur and tot + sz > per_file:
            groups.append(cur)
            cur, tot = [], 0
        cur.append(k)
        tot += sz
    if cur:
        groups.append(cur)
    log = write(prog, out, groups)
    print('j2c: %d lop, %d tep, %d loi dich, %d tham chieu thieu' % (
        len(prog.order), len(groups) + 1, len(log), sum(prog.missing.values())))
    for l in log[:20]:
        print('  ', l)


if __name__ == '__main__':
    main()
