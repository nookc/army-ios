#!/usr/bin/env python3
"""Ghi hdx/ds = danh sach CRC32 (4 byte big-endian / muc) cua moi tep hdx/<8 hex>.png  -  python3 mkds.py <thu muc hdx>"""
import os, struct, sys
d = sys.argv[1]
ds = sorted(int(f[:8], 16) for f in os.listdir(d) if len(f) == 12 and f.endswith('.png'))
open(os.path.join(d, 'ds'), 'wb').write(b''.join(struct.pack('>I', c) for c in ds))
print('hdx/ds:', len(ds), 'anh thay the')
