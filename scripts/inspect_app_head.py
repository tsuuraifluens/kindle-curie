import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.utils import nulltermstr

with open('extracted/ufw_unpacked/decrypted.bin', 'rb') as f:
    dec = f.read()

print("Bytes from 0x2000 to 0x2140:")
for off in range(0x2000, 0x2140, 32):
    chunk = dec[off : off + 32]
    hcrc = struct.unpack_from('<H', chunk, 0)[0]
    calc_hcrc = jl_crc16(chunk[2:])
    edcrc, eoff, esize, eflags, eresvd, eindex, ename = struct.unpack('<HIIBBH16s', chunk[2:])
    name = nulltermstr(ename, 'ascii')
    status = "OK" if hcrc == calc_hcrc else f"CRC ERR (exp 0x{calc_hcrc:04X})"
    print(f"0x{off:06X}: [{status}] hcrc=0x{hcrc:04X}, edcrc=0x{edcrc:04X}, off=0x{eoff:X}, sz=0x{esize:X}, flags=0x{eflags:02X}, idx={eindex}, name='{name}'")
