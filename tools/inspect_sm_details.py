import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base = 0x01E00120

print("=== Disassembling sm_cb (0x026F0 to 0x02830) ===")
pc = 0x026F0
while pc < 0x02830:
    w0 = struct.unpack_from('<H', app, pc)[0]
    b = app[pc:pc+6]
    vaddr = base + pc

    # mov rX, 32-bit imm (6 bytes)
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0x0F
        imm = struct.unpack_from('<I', app, pc+2)[0]
        comment = ''
        if base <= imm < base + len(app):
            str_cand = app[imm-base:imm-base+40].split(b'\x00')[0]
            if len(str_cand) >= 2:
                comment = f' ; "{str_cand.decode(errors="ignore")}"'
        print(f"0x{pc:05X} (V: 0x{vaddr:08X}): mov r{reg}, 0x{imm:08X}{comment}")
        pc += 6
        continue

    # 6-byte instruction with FF prefix like 00 FF / 01 FF (cmp/branch)
    if (w0 & 0xFF00) == 0xFF00:
        cond = w0 & 0xFF
        val = app[pc+2]
        imm16 = struct.unpack_from('<h', app, pc+4)[0]
        target = pc + 6 + imm16 * 2
        print(f"0x{pc:05X} (V: 0x{vaddr:08X}): cmp/branch op=0x{cond:02X}, val=0x{val:02X}, target=0x{target:05X} (raw: {b[:6].hex(' ')})")
        pc += 6
        continue
        
    # call 32-bit (4 bytes)
    if (w0 & 0xFFC0) == 0xF380:
        w1 = struct.unpack_from('<H', app, pc+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        target = ((imm6 << 17) | (w1 << 1)) + (base + pc) + 4
        target_off = target - base
        print(f"0x{pc:05X} (V: 0x{vaddr:08X}): call 0x{target:08X} (off 0x{target_off:05X})")
        pc += 4
        continue

    # jmp 32-bit (4 bytes)
    if (w0 & 0xFFC0) == 0xF3C0:
        w1 = struct.unpack_from('<H', app, pc+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        target = ((imm6 << 17) | (w1 << 1)) + (base + pc) + 4
        target_off = target - base
        print(f"0x{pc:05X} (V: 0x{vaddr:08X}): jmp 0x{target:08X} (off 0x{target_off:05X})")
        pc += 4
        continue

    if w0 == 0x0020:
        print(f"0x{pc:05X} (V: 0x{vaddr:08X}): rts")
        pc += 2
        continue

    print(f"0x{pc:05X} (V: 0x{vaddr:08X}): {w0:04X} ({app[pc:pc+2].hex(' ')})")
    pc += 2
