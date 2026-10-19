import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

def print_fn(va, length):
    off = va_to_off(va)
    print(f"\nDisassembly of 0x{va:08X} (len {length}):")
    code = data[off : off + length]
    for i in range(0, len(code), 16):
        c_va = va + i
        print(f"0x{c_va:08X}: {' '.join(f'{b:02x}' for b in code[i:i+16])}")

# Let's inspect jl_key::byteArrayDataToKey at 0x00438190
print_fn(0x00438190, 160)
# Let's inspect JL_PACKAGE_FORMAT_COMMON::decrypt at 0x00441B10
print_fn(0x00441B10, 160)
