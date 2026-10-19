import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.cipher import jl_enc_cipher
from jltech.utils import nulltermstr

with open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb') as f:
    orig = f.read()

# Entry 3 is ota.bin at 0x346C0, size 0x35F78 (221048 bytes)
ota = bytearray(orig[0x346C0 : 0x346C0 + 0x35F78])
print(f"ota.bin size: {len(ota)} (0x{len(ota):X})")
print(f"ota start: {ota[:32].hex(' ')}")
print(f"ota string start: {ota[:32]}")
