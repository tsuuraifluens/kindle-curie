import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.utils import nulltermstr

with open('extracted/ufw_unpacked/decrypted.bin', 'rb') as f:
    dec = f.read()

print("Top entries in flash.bin (base 0x00):")
# First entry at 0x20 (after 32-byte flash header)
off = 0x20
while off < 0x2000:
    chunk = dec[off : off + 32]
    if chunk == b'\xff' * 32 or chunk == b'\x00' * 32:
        break
    hcrc = struct.unpack_from('<H', chunk, 0)[0]
    calc_hcrc = jl_crc16(chunk[2:])
    edcrc, eoff, esize, eflags, eresvd, eindex, ename = struct.unpack('<HIIBBH16s', chunk[2:])
    try:
        name = nulltermstr(ename, 'ascii')
    except Exception:
        name = str(ename)
    status = "OK" if hcrc == calc_hcrc else f"FAIL (exp 0x{calc_hcrc:04X})"
    
    # Check data CRC if possible
    # For top entries:
    data_crc_stat = ""
    if eflags & 0x10:
        data_crc_stat = "(reserved)"
    else:
        d = dec[eoff : eoff + esize]
        d_crc = jl_crc16(d)
        data_crc_stat = f"data_crc {'OK' if d_crc == edcrc else f'FAIL (calc 0x{d_crc:04X})'}"
        
    print(f"0x{off:04X}: [{status}] hcrc=0x{hcrc:04X}, edcrc=0x{edcrc:04X} {data_crc_stat}, off=0x{eoff:X}, sz=0x{esize:X}, flags=0x{eflags:02X}, idx={eindex}, name='{name}'")
    off += 32
    if eindex != 0:
        break
