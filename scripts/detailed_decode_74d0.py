import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()

def decode_one(off):
    w0 = struct.unpack_from('<H', app, off)[0]
    grp = (w0 >> 13) & 7
    # Check 16-bit instructions
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
    if grp == 0:
        if (w0 >> 8) & 0x1F == 8:
            return 2, f'mov r{w0&0xF}, r{(w0>>4)&0xF}'
        ins0912 = (w0 >> 9) & 0xF
        ins0405 = (w0 >> 4) & 3
        if ins0912 == 2:
            names = ['uxtb', 'sxtb', 'uxth', 'sxth']
            return 2, f'{names[ins0405]} r{w0&0xF}, r{(w0>>6)&7}'
    if grp == 7:
        if (w0 & 0xFFF0) == 0xFFC0:
            imm = struct.unpack_from('<I', app, off+2)[0]
            return 6, f'mov r{w0&0xF}, 0x{imm:08X}'
        if (w0 & 0xFFC0) == 0xF380:
            w1 = struct.unpack_from('<H', app, off+2)[0]
            imm6 = w0 & 0x3F
            if imm6 & 0x20: imm6 -= 0x40
            target = ((imm6 << 17) | (w1 << 1)) + 0x01E00120 + off + 4
            return 4, f'call 0x{target:08X}'
        if (w0 & 0xFF00) == 0xFF00:
            # 48-bit branch
            w1 = struct.unpack_from('<H', app, off+2)[0]
            w2 = struct.unpack_from('<H', app, off+4)[0]
            return 6, f'48-bit branch w0=0x{w0:04X} w1=0x{w1:04X} w2=0x{w2:04X}'
        if (w0 >> 12) & 1 == 0:
            w1 = struct.unpack_from('<H', app, off+2)[0]
            cond = (w1 >> 12) & 0xF
            pcrel = w1 & 0xFFF
            if pcrel & 0x800: pcrel -= 0x1000
            target = 0x01E00120 + off + 4 + (pcrel << 1)
            return 4, f'branch cond {cond} to 0x{target - 0x01E00120:04X}'
        # load/store
        ins0412 = (w0 >> 4) & 0x1FF
        if ins0412 == 0x190:
            w1 = struct.unpack_from('<H', app, off+2)[0]
            return 4, f'lb.u r{w0&0xF}, [r{(w1>>12)&0xF} + 0x{w1&0xFFF:X}]'
        if ins0412 == 0x191:
            w1 = struct.unpack_from('<H', app, off+2)[0]
            return 4, f'sb r{w0&0xF}, [r{(w1>>12)&0xF} + 0x{w1&0xFFF:X}]'
        if ins0412 == 0x150:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'lw r{w0&0xF}, [sp + {w1}]'
        if ins0412 == 0x151:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'sw r{w0&0xF}, [sp + {w1}]'
        if ins0412 == 0x158:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'lh.u r{w0&0xF}, [sp + {w1}]'
        if ins0412 == 0x159:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'sh r{w0&0xF}, [sp + {w1}]'
        if ins0412 == 0x15C:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'lb.u r{w0&0xF}, [sp + {w1}]'
        if ins0412 == 0x15D:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'sb r{w0&0xF}, [sp + {w1}]'
        if ins0412 == 0x1B0:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'mov r{w0&0xF}, #{w1}'
        if (w0 >> 8) & 0x1F == 0x1C:
            w1 = struct.unpack_from('<h', app, off+2)[0]
            return 4, f'add r{w0&0xF}, r{(w0>>4)&0xF}, #{w1}'
    if grp == 4:
        if (w0 >> 12) & 1 == 0:
            imm = (w0 >> 4) & 0xFF
            if imm & 0x80: imm -= 0x100
            return 2, f'add r{w0&0xF}, #{imm}'
        else:
            return 2, f'add r{w0&0xF}, r{(w0>>9)&7}, #{(w0>>4)&0x1F}'
    return 2, f'raw 0x{w0:04X}'

off = 0x74D0
while off < 0x7520:
    length, text = decode_one(off)
    b = app[off:off+length]
    print(f'0x{off:04X} ({b.hex(" ")}): {text}')
    off += length
