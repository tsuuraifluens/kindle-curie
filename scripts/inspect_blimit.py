import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.cipher import jl_enc_cipher

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
with open(orig_path, 'rb') as f: orig = f.read()

# blimit.bin is Entry 6, offset 0x9D760, size 0x90 (144 bytes)
blimit = bytearray(orig[0x9D760 : 0x9D760 + 0x90])
print("blimit raw:", blimit.hex(' '))

# Let's inspect blimit verification in updater at 0x004A1570
# Earlier we saw 0x004A1570 is called with blimit
