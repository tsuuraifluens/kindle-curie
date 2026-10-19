import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.utils import nulltermstr

with open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb') as f:
    orig = f.read()

# Entry 5 is flash2.bin at 0x6A660, size 0x33100
flash2 = bytearray(orig[0x6A660 : 0x6A660 + 0x33100])
print(f"flash2 size: {len(flash2)} (0x{len(flash2):X})")

# First 32 bytes is flash header
hdr = bytearray(flash2[:32])
jl_enc_cipher(hdr, 0, 32, key=0xFFFF)
print("Decrypted flash2 header:", hdr.hex(' '))
hcrc = struct.unpack_from('<H', hdr, 0)[0]
calc_hcrc = jl_crc16(hdr[2:])
print(f"Header CRC: 0x{hcrc:04X} (calc 0x{calc_hcrc:04X})")

fhcrc, fburnersz, fvid, fflashsz, ffsver, fblockalign, fresvd, fspecopt, fpid = struct.unpack('<HH4sIBBBB16s', hdr)
print(f"VID: {fvid}, PID: {fpid}")

# Top entries in flash2
off = 0x20
while off < 0x2000:
    chunk = bytearray(flash2[off:off+32])
    jl_enc_cipher(chunk, 0, 32, key=0xFFFF)
    if chunk == b'\xff'*32 or chunk == b'\x00'*32:
        break
    h = struct.unpack_from('<H', chunk, 0)[0]
    ch = jl_crc16(chunk[2:])
    ed, eo, es, ef, er, ei, en = struct.unpack('<HIIBBH16s', chunk[2:])
    try:
        n = nulltermstr(en, 'ascii')
    except Exception:
        n = str(en)
    print(f"  0x{off:04X}: [{ 'OK' if h==ch else 'FAIL' }] name='{n}', off=0x{eo:X}, sz=0x{es:X}, edcrc=0x{ed:04X}")
    off += 32
    if ei != 0: break
