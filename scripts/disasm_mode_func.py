import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

def sign_extend(val, bits):
    if val & (1 << (bits - 1)):
        return val - (1 << bits)
    return val

def decode_chunk(start_off, length):
    off = start_off
    end_off = start_off + length
    while off < end_off:
        vaddr = base_addr + off
        w0 = struct.unpack_from('<H', app_data, off)[0]
        group = (w0 >> 13) & 7
        
        # Check for 6-byte mov imm32: group=7, ins0412=0x1FC
        if (w0 & 0xFFF0) == 0xFFC0:
            reg = w0 & 0xF
            imm = struct.unpack_from('<I', app_data, off+2)[0]
            hint = ''
            if base_addr <= imm < base_addr + len(app_data):
                s = app_data[imm-base_addr:imm-base_addr+32].split(b'\x00')[0]
                if len(s) >= 2:
                    hint = f' ; "{s.decode(errors="ignore")}"'
            print(f'0x{off:04X} (V:0x{vaddr:08X}): mov r{reg}, 0x{imm:08X}{hint}')
            off += 6
            continue
            
        # Check for 4-byte call: group=7, ins0612=0x4E (bits 15..6 == 0x3CD or 0xF380..0xF3BF)
        # In q32s: group=7 (bits 15..13=7), ins0612=0x4E (bits 12..6 = 01001110b = 0x4E)
        # => bits 15..6 = (7 << 7) | 0x4E = 0x3CE or similar
        # Let's check bits 15..6:
        hi10 = (w0 >> 6) & 0x3FF
        if hi10 == 0x3CE or (w0 & 0xFFC0) == 0xF380:
            addr0005s = sign_extend(w0 & 0x3F, 6)
            w1 = struct.unpack_from('<H', app_data, off+2)[0]
            addr1631 = w1
            inst_next = vaddr + 4
            target = ((addr0005s << 17) | (addr1631 << 1)) + inst_next
            target_off = target - base_addr
            print(f'0x{off:04X} (V:0x{vaddr:08X}): call 0x{target:08X} (app off 0x{target_off:05X})')
            off += 4
            continue
            
        # 2-byte or other 4-byte
        print(f'0x{off:04X} (V:0x{vaddr:08X}): {w0:04X}')
        off += 2

print('=== Decoded function 0x07230 - 0x072A0 ===')
decode_chunk(0x07230, 0x70)




