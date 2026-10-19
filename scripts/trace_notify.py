import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start_va = 0x0040EA70
end_va = 0x0040F200
start_off = va_to_off(start_va)
end_off = va_to_off(end_va)

print(f"Tracing MainWindow::firmwareContentNotify from 0x{start_va:08X} to 0x{end_va:08X}:")

i = start_off
while i < end_off:
    b = data[i]
    cur_va = off_to_va(i)
    if b == 0xE8:
        rel = struct.unpack_from('<i', data, i + 1)[0]
        t_va = cur_va + 5 + rel
        t_off = va_to_off(t_va)
        t_str = f"0x{t_off:X}" if t_off is not None else "None"
        print(f"  0x{cur_va:08X}: CALL 0x{t_va:08X} (off {t_str})")
        i += 5
        continue
    elif b == 0xE9:
        rel = struct.unpack_from('<i', data, i + 1)[0]
        t_va = cur_va + 5 + rel
        print(f"  0x{cur_va:08X}: JMP 0x{t_va:08X}")
        i += 5
        continue
    elif b == 0x0F and data[i+1] in range(0x80, 0x90): # jcc rel32
        rel = struct.unpack_from('<i', data, i + 2)[0]
        t_va = cur_va + 6 + rel
        cond = ['O', 'NO', 'B', 'NB', 'E', 'NE', 'BE', 'NBE', 'S', 'NS', 'P', 'NP', 'L', 'NL', 'LE', 'NLE'][data[i+1] - 0x80]
        print(f"  0x{cur_va:08X}: J{cond} 0x{t_va:08X}")
        i += 6
        continue
    elif b in range(0x70, 0x80): # jcc rel8
        rel = struct.unpack_from('<b', data, i + 1)[0]
        t_va = cur_va + 2 + rel
        cond = ['O', 'NO', 'B', 'NB', 'E', 'NE', 'BE', 'NBE', 'S', 'NS', 'P', 'NP', 'L', 'NL', 'LE', 'NLE'][b - 0x70]
        print(f"  0x{cur_va:08X}: J{cond} 0x{t_va:08X}")
        i += 2
        continue
    elif b == 0xEB: # jmp rel8
        rel = struct.unpack_from('<b', data, i + 1)[0]
        t_va = cur_va + 2 + rel
        print(f"  0x{cur_va:08X}: JMP 0x{t_va:08X}")
        i += 2
        continue
    elif b in (0x68, 0xB8, 0xBA, 0xB9, 0xBB, 0xBE, 0xBF):
        imm = struct.unpack_from('<I', data, i + 1)[0]
        imm_off = va_to_off(imm)
        if imm_off:
            s = data[imm_off : imm_off + 40].split(b'\x00')[0]
            if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                print(f"  0x{cur_va:08X}: IMM 0x{imm:08X} -> '{s.decode('latin1')}'")
    i += 1
