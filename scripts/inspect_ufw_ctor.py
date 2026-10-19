import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

va = 0x00464CE0
off = va_to_off(va)
chunk = data[off : off + 0x180]

p = 0
while p < len(chunk) - 5:
    cur_va = va + p
    b0 = chunk[p]
    b1 = chunk[p+1]
    
    # mov [esi + off], imm
    if b0 == 0xC7 and chunk[p+1] == 0x86:
        f_off = struct.unpack_from('<I', chunk, p+2)[0]
        imm = struct.unpack_from('<I', chunk, p+6)[0]
        print(f"  0x{cur_va:08X}: mov [esi + 0x{f_off:X}], 0x{imm:X}")
        p += 10
        continue
    elif b0 == 0xC7 and (b1 & 0xC0) == 0x40 and (b1 & 0x07) == 0x06: # mov [esi + byte_off], imm
        f_off = chunk[p+2]
        imm = struct.unpack_from('<I', chunk, p+3)[0]
        print(f"  0x{cur_va:08X}: mov [esi + 0x{f_off:X}], 0x{imm:X}")
        p += 7
        continue
    elif b0 == 0xE8:
        rel = struct.unpack_from('<i', chunk, p+1)[0]
        print(f"  0x{cur_va:08X}: CALL 0x{cur_va+5+rel:08X}")
        p += 5
        continue
    p += 1
