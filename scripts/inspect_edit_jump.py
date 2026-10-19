import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

# Jump table at 0x0049B6B4
table_off = va_to_off(0x0049B6B4)
vas = struct.unpack_from('<4I', data, table_off)
for idx, v in enumerate(vas):
    print(f"jl_file_edit case {idx}: VA 0x{v:08X} (off 0x{va_to_off(v):X})")
    code = data[va_to_off(v) : va_to_off(v) + 32]
    print(f"  code: {code.hex(' ')}")
