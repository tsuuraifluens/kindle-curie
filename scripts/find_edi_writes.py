import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004BCA80
end_va = 0x004BDA80

start_off = va_to_off(start_va)
end_off = va_to_off(end_va)
code = data[start_off:end_off]

print("Searching for writes to EDI in 0x004BCA80:")
for i in range(len(code)-3):
    va = start_va + i
    b0 = code[i]
    b1 = code[i+1]
    b2 = code[i+2]
    
    # xor edi, edi: 31 FF or 33 FF
    if (b0 == 0x31 and b1 == 0xFF) or (b0 == 0x33 and b1 == 0xFF):
        print(f"0x{va:08X}: xor edi, edi")
    # mov edi, imm32: BF xx xx xx xx
    elif b0 == 0xBF:
        imm = struct.unpack_from('<I', code, i+1)[0]
        print(f"0x{va:08X}: mov edi, 0x{imm:X}")
    # mov edi, eax: 89 C7 or 8B F8
    elif (b0 == 0x89 and b1 == 0xC7) or (b0 == 0x8B and b1 == 0xF8):
        print(f"0x{va:08X}: mov edi, eax")
    # mov edi, ecx: 8B F9
    elif b0 == 0x8B and b1 == 0xF9:
        print(f"0x{va:08X}: mov edi, ecx")
    # mov edi, edx: 8B FA
    elif b0 == 0x8B and b1 == 0xFA:
        print(f"0x{va:08X}: mov edi, edx")
    # mov edi, esi: 8B FE
    elif b0 == 0x8B and b1 == 0xFE:
        print(f"0x{va:08X}: mov edi, esi")
    # mov edi, [esp/ebp+...]: 8B 7D .. or 8B 7C 24 ..
    elif b0 == 0x8B and (b1 & 0xF8) == 0x78:
        print(f"0x{va:08X}: mov edi, [{b1:02x}...]")
    # or edi, ...
    elif b0 == 0x83 and (b1 == 0xC7 or b1 == 0xCF):
        print(f"0x{va:08X}: add/or edi, ...")
    # dec edi: 4F
    # inc edi: 47
    # lea edi, ...: 8D ...
