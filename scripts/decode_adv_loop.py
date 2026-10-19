import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

def decode_ins(off):
    w0 = struct.unpack_from('<H', app, off)[0]
    # Check 32-bit / 48-bit instructions
    grp = (w0 >> 13) & 7
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0xF
        imm = struct.unpack_from('<I', app, off+2)[0]
        s = ''
        if base <= imm < base + len(app):
            st = app[imm-base:imm-base+40].split(b'\x00')[0]
            if len(st) >= 2: s = f' ; "{st.decode(errors="ignore")}"'
        return 6, f'mov r{reg}, 0x{imm:08X}{s}'
    if (w0 & 0xFFC0) == 0xF380:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        target = ((imm6 << 17) | (w1 << 1)) + base + off + 4
        return 4, f'call 0x{target:08X} (off 0x{target-base:05X})'
    if grp == 7 and ((w0 >> 12) & 1) == 0:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        cond = (w1 >> 12) & 0xF
        pcrel = w1 & 0xFFF
        if pcrel & 0x800: pcrel -= 0x1000
        target = base + off + 4 + (pcrel << 1)
        cond_names = {0:'jeq', 1:'jne', 2:'jcs', 3:'jcc', 8:'jhi', 9:'jls', 10:'jge', 11:'jlt', 12:'jgt', 13:'jle'}
        cname = cond_names.get(cond, f'cond{cond}')
        return 4, f'{cname} -> 0x{target-base:04X}'
    if (w0 & 0xF800) == 0x1800:
        imm11 = w0 & 0x7FF
        if imm11 & 0x400: imm11 -= 0x800
        target = base + off + 2 + (imm11 << 1)
        return 2, f'goto 0x{target-base:04X}'
    if grp == 3:
        ins12 = (w0 >> 12) & 1
        ins3 = (w0 >> 3) & 1
        regDl = (w0 >> 9) & 7
        regAl = w0 & 7
        addr = (w0 >> 4) & 0x1F
        if ins12 == 0 and ins3 == 1:
            return 2, f'sb r{regAl}, [r{regDl} + {addr}]'
        elif ins12 == 0 and ins3 == 0:
            return 2, f'lb.u r{regAl}, [r{regDl} + {addr}]'
        elif ins12 == 1:
            imm = (w0 >> 4) & 0xFF
            return 2, f'mov r{regAl}, #{imm}'
    if grp == 4:
        ins12 = (w0 >> 12) & 1
        regA = w0 & 0xF
        if ins12 == 0:
            simm8 = (w0 >> 4) & 0xFF
            if simm8 & 0x80: simm8 -= 0x100
            return 2, f'add r{regA}, #{simm8}'
        else:
            regDl = (w0 >> 9) & 7
            imm5 = (w0 >> 4) & 0x1F
            return 2, f'add r{regA}, r{regDl}, #{imm5}'
    if grp == 0:
        ins0912 = (w0 >> 9) & 0xF
        ins0405 = (w0 >> 4) & 3
        regA = w0 & 0xF
        regCl = (w0 >> 6) & 7
        if ins0912 == 2 and ins0405 == 0:
            return 2, f'uxtb r{regA}, r{regCl}'
        if ins0912 == 2 and ins0405 == 2:
            return 2, f'uxth r{regA}, r{regCl}'
        if (w0 >> 8) & 0x1F == 8:
            return 2, f'mov r{regA}, r{(w0>>4)&0xF}'
    if grp == 7:
        ins0812 = (w0 >> 8) & 0x1F
        ins0412 = (w0 >> 4) & 0x1FF
        if ins0812 == 0x1C:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'add r{w0&0xF}, r{(w0>>4)&0xF}, #{w1}'
        if ins0412 == 0x191:
            w1 = struct.unpack_from('<H', app, off+2)[0]
            regE = (w1 >> 12) & 0xF
            addr = w1 & 0xFFF
            return 4, f'sb r{w0&0xF}, [r{regE} + 0x{addr:X}]'
        if ins0412 == 0x190:
            w1 = struct.unpack_from('<H', app, off+2)[0]
            regE = (w1 >> 12) & 0xF
            addr = w1 & 0xFFF
            return 4, f'lb.u r{w0&0xF}, [r{regE} + 0x{addr:X}]'
    return 2, f'0x{w0:04X}'

off = 0x4D80
while off < 0x4DFE:
    length, s = decode_ins(off)
    print(f'0x{off:04X}: {s}')
    off += length
