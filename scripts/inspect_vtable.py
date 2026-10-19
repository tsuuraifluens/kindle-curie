import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

vtable_off = va_to_off(0x00DD5EDC)
print(f"Vtable offset: 0x{vtable_off:X}")
entries = struct.unpack_from('<30I', data, vtable_off)
for idx, entry in enumerate(entries):
    e_off = va_to_off(entry)
    e_str = f"0x{e_off:X}" if e_off is not None else "None"
    print(f"  +0x{idx*4:02X}: 0x{entry:08X} (off {e_str})")
