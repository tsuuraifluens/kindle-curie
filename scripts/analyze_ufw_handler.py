import struct

with open('extracted/updater_unpacked.exe', 'rb') as f:
    data = f.read()

def off_to_va(off):
    if 0x400 <= off < 0x927A00:
        return 0x401000 + (off - 0x400)
    elif 0x927A00 <= off < 0xD39400:
        return 0xD29000 + (off - 0x927A00)
    elif 0xD39400 <= off < 0xD55E00:
        return 0x113B000 + (off - 0xD39400)
    return None

def va_to_off(va):
    if 0x401000 <= va < 0xD29000:
        return 0x400 + (va - 0x401000)
    elif 0xD29000 <= va < 0x113B000:
        return 0x927A00 + (va - 0xD29000)
    elif 0x113B000 <= va < 0x1166000:
        return 0xD39400 + (va - 0x113B000)
    return None

start_off = 0xBBE80 # VA 0x004BCA80
print(f"Analyzing UFW handler at file 0x{start_off:X} (VA 0x{off_to_va(start_off):08X}):")

code = data[start_off : start_off + 2048]
i = 0
while i < len(code) - 5:
    b = code[i]
    cur_va = off_to_va(start_off + i)
    if b == 0xE8: # call rel32
        rel = struct.unpack_from('<i', code, i + 1)[0]
        target_va = cur_va + 5 + rel
        target_off = va_to_off(target_va)
        off_str = f"0x{target_off:X}" if target_off is not None else "None"
        print(f"  [0x{cur_va:08X}] CALL 0x{target_va:08X} (off {off_str})")
        i += 5
        continue
    elif b in (0x68, 0xB8, 0xBA, 0xB9, 0xBB, 0xBE, 0xBF): # push imm32 or mov reg, imm32
        imm = struct.unpack_from('<I', code, i + 1)[0]
        imm_off = va_to_off(imm)
        if imm_off:
            s = data[imm_off : imm_off + 32].split(b'\x00')[0]
            if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                print(f"  [0x{cur_va:08X}] IMM 0x{imm:08X} -> '{s.decode('latin1')}'")
    elif b == 0xC3: # ret
        print(f"  [0x{cur_va:08X}] RET")
    i += 1
