with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()
import struct
base = 0x01E00120

def decode_insn(off):
    w0 = struct.unpack_from('<H', app, off)[0]
    # mov imm32
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0xF
        imm = struct.unpack_from('<I', app, off+2)[0]
        s = ''
        if base <= imm < base + len(app):
            st = app[imm-base:imm-base+30].split(b'\x00')[0]
            if len(st) >= 2: s = f' ; "{st.decode(errors="ignore")}"'
        return 6, f'mov r{reg}, 0x{imm:08X}{s}'
    # mov regA, #imm
    if (w0 >> 13) == 3 and ((w0 >> 12) & 1) == 1:
        reg = w0 & 0xF
        imm = (w0 >> 4) & 0xFF
        return 2, f'mov r{reg}, #{imm}'
    # mov regA, regB
    if (w0 & 0xFF00) == 0x0800:
        regA = w0 & 0xF
        regB = (w0 >> 4) & 0xF
        return 2, f'mov r{regA}, r{regB}'
    # call pcrel23m2
    if (w0 & 0xFFC0) == 0xF380:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        target = ((imm6 << 17) | (w1 << 1)) + base + off + 4
        return 4, f'call 0x{target:08X} (off 0x{target-base:05X})'
    # jmp pcrel23m2
    if (w0 & 0xFFC0) == 0xF3C0:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        target = ((imm6 << 17) | (w1 << 1)) + base + off + 4
        return 4, f'jmp 0x{target:08X} (off 0x{target-base:05X})'
    # conditional branch with imm
    if (w0 >> 13) == 7 and ((w0 >> 12) & 1) == 0:
        regA = w0 & 0xF
        imm = (w0 >> 4) & 0xFF
        w1 = struct.unpack_from('<H', app, off+2)[0]
        pcrel = w1 & 0xFFF
        if pcrel & 0x800: pcrel -= 0x1000
        target = base + off + 4 + (pcrel << 1)
        bnames = {0:'jeq', 1:'jne', 2:'jcs', 3:'jcc', 8:'jhi', 9:'jls', 0xA:'jge', 0xB:'jlt', 0xC:'jgt', 0xD:'jle'}
        bname = bnames.get((w1 >> 12) & 0xF, f'j?{(w1>>12)&0xF}')
        return 4, f'{bname} r{regA}, #{imm}, 0x{target:08X} (off 0x{target-base:05X})'
    return 2, f'0x{w0:04X}'

off = 0x18760
while off < 0x187B0:
    sz, s = decode_insn(off)
    print(f'0x{off:05X} (V: 0x{base+off:08X}): {s}')
    off += sz
