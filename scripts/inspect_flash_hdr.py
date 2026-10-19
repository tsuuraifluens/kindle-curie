import os, sys, struct, hashlib
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.crc import jl_crc16, jl_crc32
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.chipkeybin import chipkeybin_decode
from jltech.utils import nulltermstr

orig_ufw = open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb').read()
mod_ufw = open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw', 'rb').read()

orig_flash = bytearray(orig_ufw[0x400 : 0x400 + 212992])
mod_flash = bytearray(mod_ufw[0x400 : 0x400 + 212992])

print(f"Orig flash.bin size: {len(orig_flash)}, CRC16: 0x{jl_crc16(orig_flash):04X}")
print(f"Mod  flash.bin size: {len(mod_flash)}, CRC16: 0x{jl_crc16(mod_flash):04X}")

# Parse flash header
# Flash header (0x00 - 0x20)
def parse_flash_header(fbin):
    hdr = bytearray(fbin[:32])
    jl_enc_cipher(hdr, 0, 32, 0xFFFF)
    hcrc = int.from_bytes(hdr[:2], 'little')
    calc_hcrc = jl_crc16(hdr[2:])
    fhcrc, fburnersz, fvid, fflashsz, ffsver, fblockalign, fresvd, fspecopt, fpid = struct.unpack('<HH4sIBBBB16s', hdr)
    # The VID and PID strings in raw flash are unencrypted at 4:8 and 16:32
    vid_str = fbin[4:8]
    pid_str = fbin[16:32]
    return {
        'hcrc': hcrc, 'calc_hcrc': calc_hcrc, 'burnersz': fburnersz,
        'vid': vid_str, 'flashsz': fflashsz, 'fsver': ffsver,
        'blockalign': fblockalign, 'specopt': fspecopt, 'pid': pid_str
    }

print("Flash header (orig):", parse_flash_header(orig_flash))
print("Flash header (mod): ", parse_flash_header(mod_flash))
print("Flash header diff:", [i for i in range(32) if orig_flash[i] != mod_flash[i]])
