import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start_va = 0x00465AF0
start_off = va_to_off(start_va)
print(f"Analyzing jl_format_ufw function at VA 0x{start_va:08X} (off 0x{start_off:X}):")

# Let's inspect 1000 bytes
code = data[start_off : start_off + 1500]

# Print first 200 bytes as hex
for i in range(0, min(len(code), 200), 16):
    va = start_va + i
    chunk = code[i:i+16]
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in chunk)}")

i = 0
while i < len(code) - 5:
    b = code[i]
    cur_va = off_to_va(start_off + i)
    if b == 0xE8:
        rel = struct.unpack_from('<i', code, i + 1)[0]
        target_va = cur_va + 5 + rel
        target_off = va_to_off(target_va)
        off_str = f"0x{target_off:X}" if target_off is not None else "None"
        print(f"  [0x{cur_va:08X}] CALL 0x{target_va:08X} (off {off_str})")
        i += 5
        continue
    elif b in (0x68, 0xB8, 0xBA, 0xB9, 0xBB, 0xBE, 0xBF):
        imm = struct.unpack_from('<I', code, i + 1)[0]
        imm_off = va_to_off(imm)
        if imm_off:
            s = data[imm_off : imm_off + 40].split(b'\x00')[0]
            if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                print(f"  [0x{cur_va:08X}] IMM 0x{imm:08X} -> '{s.decode('latin1')}'")
    elif b == 0xC3:
        print(f"  [0x{cur_va:08X}] RET")
        if i > 50:
            break
    i += 1
