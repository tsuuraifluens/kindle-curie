import struct

with open('extracted/updater_unpacked.exe', 'rb') as f:
    d = f.read()

def va_to_off(va):
    if 0x401000 <= va < 0xD29000:
        return 0x400 + (va - 0x401000)
    elif 0xD29000 <= va < 0x113B000:
        return 0x927A00 + (va - 0xD29000)
    elif 0x113B000 <= va < 0x1166000:
        return 0xD39400 + (va - 0x113B000)
    return None

def get_rtti_name(vtable_va):
    off = va_to_off(vtable_va - 4)
    if not off: return "no off"
    col_va = struct.unpack_from('<I', d, off)[0]
    col_off = va_to_off(col_va)
    if not col_off: return f"no col {hex(col_va)}"
    td_va = struct.unpack_from('<I', d, col_off + 12)[0]
    td_off = va_to_off(td_va)
    if not td_off: return f"no td {hex(td_va)}"
    return d[td_off+8 : td_off+80].split(b'\x00')[0].decode('latin1', errors='ignore')

# Mode 1 created vtable 0x00DD5EF0
print("Mode 1 (0x00DD5EF0):", get_rtti_name(0x00DD5EF0))

# What vtables are in Mode 2 (0x004BA2A0), Mode 3 (0x004B9120), Mode 4 (0x004B8DB0)?
# Let's search for "mov [esi], imm" or similar in those functions
import re
for name, fn in [("Mode 1", 0x004BB470), ("Mode 2", 0x004BA2A0), ("Mode 3", 0x004B9120), ("Mode 4", 0x004B8DB0)]:
    fn_off = va_to_off(fn)
    code = d[fn_off : fn_off + 200]
    print(f"\n--- {name} at 0x{fn:08X} ---")
    for i in range(len(code)-5):
        if code[i] == 0xE8:
            rel = struct.unpack_from('<i', code, i+1)[0]
            callee = fn + i + 5 + rel
            print(f"  calls 0x{callee:08X}")
