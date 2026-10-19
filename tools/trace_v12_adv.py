import struct
import sys
sys.path.insert(0, 'kindle_click_jieli_port/tools/jieli_tools')
from jltech.cipher import jl_sfc_cipher

v12_ufw = open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.2.ufw', 'rb').read()
dec_flash = bytearray(v12_ufw[0x400:0x400+212992])
jl_sfc_cipher(dec_flash, 0x2000, 0x32000, 0x2000, key=0x190D)
mod_app = dec_flash[0x2120:0x2120+198824]
base = 0x01E00120

from decode_conn_event import decode_all
print('=== v1.2 adv_setup_init (0x04D30 - 0x04E50) ===')
# Let's decode mod_app
def decode_mod(start, end):
    pc = start
    while pc < end:
        vaddr = base + pc
        w0 = struct.unpack_from('<H', mod_app, pc)[0]
        if (w0 & 0xFFF0) == 0xFFC0:
            reg = w0 & 0x0F
            imm = struct.unpack_from('<I', mod_app, pc+2)[0]
            comment = ''
            if base <= imm < base + len(mod_app):
                str_cand = mod_app[imm-base:imm-base+40].split(b'\x00')[0]
                if len(str_cand) >= 2:
                    comment = f' ; "{str_cand.decode(errors="ignore")}"'
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): mov r{reg}, 0x{imm:08X}{comment}')
            pc += 6
            continue
        if (w0 & 0xFFC0) == 0xF380:
            w1 = struct.unpack_from('<H', mod_app, pc+2)[0]
            imm6 = w0 & 0x3F
            if imm6 & 0x20: imm6 -= 0x40
            target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
            target_off = target - base
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): call 0x{target:08X} (off 0x{target_off:05X})')
            pc += 4
            continue
        if (w0 & 0xFFC0) == 0xF3C0:
            w1 = struct.unpack_from('<H', mod_app, pc+2)[0]
            imm6 = w0 & 0x3F
            if imm6 & 0x20: imm6 -= 0x40
            target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
            target_off = target - base
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): jmp 0x{target:08X} (off 0x{target_off:05X})')
            pc += 4
            continue
        if w0 == 0x0020:
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): rts')
            pc += 2
            continue
        print(f'0x{pc:05X} (V: 0x{vaddr:08X}): {w0:04X}')
        pc += 2

decode_mod(0x04D30, 0x04E50)
