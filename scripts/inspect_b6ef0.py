import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start_va = 0x004B6EF0
start_off = va_to_off(start_va)
code = data[start_off : start_off + 200]

# print instructions
for i in range(0, len(code), 16):
    va = start_va + i
    chunk = code[i:i+16]
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in chunk)}")

# search for vtable pointer assignment (mov dword ptr [reg], vtable_va)
for i in range(len(code) - 6):
    if code[i] == 0xC7 and code[i+1] in (0x00, 0x01, 0x02, 0x03, 0x06, 0x07): # mov [reg], imm32
        vtable_va = struct.unpack_from('<I', code, i + 2)[0]
        v_off = va_to_off(vtable_va)
        print(f"Found vtable assignment: 0x{vtable_va:08X} (off 0x{v_off:X} if v_off else 'None')")
        if v_off:
            # dump first 20 entries of vtable
            entries = struct.unpack_from('<20I', data, v_off)
            for idx, entry in enumerate(entries):
                print(f"  vtable[{idx}] (offset +0x{idx*4:02X}): 0x{entry:08X}")
