import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

def sign_extend(val, bits):
    if val & (1 << (bits - 1)):
        return val - (1 << bits)
    return val

off = 0x13150
end_off = 0x13300
while off < end_off:
    vaddr = base_addr + off
    w0 = struct.unpack_from('<H', app_data, off)[0]
    
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0xF
        imm = struct.unpack_from('<I', app_data, off+2)[0]
        hint = ''
        if base_addr <= imm < base_addr + len(app_data):
            s = app_data[imm-base_addr:imm-base_addr+32].split(b'\x00')[0]
            if len(s) >= 2: hint = f' ; "{s.decode(errors="ignore")}"'
        print(f'0x{off:04X} (V:0x{vaddr:08X}): mov r{reg}, 0x{imm:08X}{hint}')
        off += 6
        continue
        
    hi10 = (w0 >> 6) & 0x3FF
    if hi10 == 0x3CE or (w0 & 0xFFC0) == 0xF380:
        addr0005s = sign_extend(w0 & 0x3F, 6)
        w1 = struct.unpack_from('<H', app_data, off+2)[0]
        target = ((addr0005s << 17) | (w1 << 1)) + (vaddr + 4)
        target_off = target - base_addr
        print(f'0x{off:04X} (V:0x{vaddr:08X}): call 0x{target:08X} (off 0x{target_off:05X})')
        off += 4
        continue

    print(f'0x{off:04X} (V:0x{vaddr:08X}): {w0:04X}')
    off += 2
