import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

for name, target in [('app_area_head', 0x00DCBDF0), ('app_dir_head_1', 0x00DCB264), ('app_dir_head_2', 0x00DCB93C), ('uboot.boot', 0x00DCB5B0)]:
    print(f"\nReferences to {name} (0x{target:08X}):")
    target_bytes = struct.pack('<I', target)
    idx = 0
    while True:
        pos = data.find(target_bytes, idx)
        if pos == -1: break
        va = off_to_va(pos)
        print(f"  file 0x{pos:X} (VA {hex(va) if va else 'None'})")
        idx = pos + 1
