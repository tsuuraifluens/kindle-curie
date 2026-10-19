import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

# Let's inspect jl_file_edit::fileOpenClicked at VA 0x0049A1A0
start_va = 0x0049A1A0
off = va_to_off(start_va)

# Let's disassemble 200 bytes
print(f"jl_file_edit::fileOpenClicked (0x{start_va:08X}):")
code = data[off : off + 200]
for i in range(0, len(code), 16):
    va = start_va + i
    chunk = code[i:i+16]
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in chunk)}")
