import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import re

for s in [b'app_area_head', b'app_dir_head', b'p11_code', b'uboot.boot']:
    for m in re.finditer(s, data):
        off = m.start()
        va = off_to_va(off)
        print(f"String '{s.decode()}' at file 0x{off:X} (VA {hex(va) if va else 'None'})")
