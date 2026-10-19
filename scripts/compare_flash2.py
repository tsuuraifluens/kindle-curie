import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher
from jltech.crc import jl_crc16

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
with open(orig_path, 'rb') as f:
    d = f.read()

# flash.bin is at 0x400, size 0x34000
flash1 = d[0x400 : 0x400 + 0x34000]
# flash2.bin is at 0x6A660, size 0x33100
flash2 = d[0x6A660 : 0x6A660 + 0x33100]

print("flash1 len:", len(flash1), "flash2 len:", len(flash2))
print("flash1 start:", flash1[:32].hex(' '))
print("flash2 start:", flash2[:32].hex(' '))

# Let's compare flash1 and flash2
common_len = min(len(flash1), len(flash2))
diffs = [i for i in range(common_len) if flash1[i] != flash2[i]]
print(f"Differences in first {common_len} bytes: {len(diffs)}")
if diffs:
    print(f"First diff at 0x{diffs[0]:X}")
    print(f"Last diff at 0x{diffs[-1]:X}")
