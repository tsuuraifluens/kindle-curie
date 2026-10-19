import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.cipher import jl_sfc_cipher
from jltech.utils import nulltermstr

mod_path = 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw'
with open(mod_path, 'rb') as f:
    mod_ufw = f.read()

mod_f = bytearray(mod_ufw[0x400 : 0x400 + 0x34000])

# Decrypt mod_f with SFC cipher
jl_sfc_cipher(mod_f, 0x2000, 0x34000 - 0x2000, base=0x2000, key=0x190D)

print("Checking mod_f app_area_head and subentries:")
# 0x2000 is app_area_head
chunk = mod_f[0x2000 : 0x2020]
hcrc = struct.unpack_from('<H', chunk, 0)[0]
calc_hcrc = jl_crc16(chunk[2:])
edcrc, eoff, esize, eflags, eresvd, eindex, ename = struct.unpack('<HIIBBH16s', chunk[2:])
name = nulltermstr(ename, 'ascii')
status = "OK" if hcrc == calc_hcrc else f"FAIL (exp 0x{calc_hcrc:04X})"

# Check data CRC
# For app_area_head: data size is 0x30C7A
data_slice = mod_f[0x2020 : 0x2020 + 0x30C7A]
calc_edcrc = jl_crc16(data_slice)
data_status = "OK" if edcrc == calc_edcrc else f"FAIL (edcrc in hdr: 0x{edcrc:04X}, actual calc: 0x{calc_edcrc:04X})"

print(f"app_area_head (0x2000): hcrc={status}, {data_status}")

# Sub-entries
for off in range(0x2020, 0x2100, 32):
    c = mod_f[off:off+32]
    h = struct.unpack_from('<H', c, 0)[0]
    ch = jl_crc16(c[2:])
    ed, eo, es, ef, er, ei, en = struct.unpack('<HIIBBH16s', c[2:])
    n = nulltermstr(en, 'ascii')
    h_stat = "OK" if h == ch else f"FAIL(exp 0x{ch:04X})"
    
    # check data crc if regular file
    d_stat = ""
    if ef == 0x82:
        payload = mod_f[0x2000 + eo : 0x2000 + eo + es]
        cd = jl_crc16(payload)
        d_stat = f"data_crc {'OK' if cd == ed else f'FAIL (exp 0x{cd:04X})'}"
    print(f"  0x{off:04X}: [{h_stat}] hcrc=0x{h:04X}, edcrc=0x{ed:04X} {d_stat}, name='{n}'")

# Check p11_code.bin at 0x032C9A
p11_off = 0x032C9A
c = mod_f[p11_off : p11_off + 32]
h = struct.unpack_from('<H', c, 0)[0]
ch = jl_crc16(c[2:])
ed, eo, es, ef, er, ei, en = struct.unpack('<HIIBBH16s', c[2:])
n = nulltermstr(en, 'ascii')
payload = mod_f[p11_off + 32 : p11_off + 32 + es]
cd = jl_crc16(payload)
print(f"\np11_code.bin (0x{p11_off:06X}): hcrc={'OK' if h==ch else 'FAIL'}, data_crc={'OK' if cd==ed else 'FAIL'}")
