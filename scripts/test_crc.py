import zlib, struct, binascii, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16

def test_crcs(filepath):
    print("Testing CRCs for:", filepath)
    with open(filepath, 'rb') as f:
        data = f.read()

    # jl_crc16
    c16 = jl_crc16(data)
    print(f"jl_crc16: 0x{c16:04X}")

    # standard crc32
    c32 = zlib.crc32(data) & 0xFFFFFFFF
    print(f"zlib crc32: 0x{c32:08X}")

    # inverted / standard crc16?
    # What about jl_crc16 of subsets?
    # What about jl_crc32? Does jieli have a jl_crc32?
    print(f"File len: {len(data)} (0x{len(data):X})")

test_crcs('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw')
test_crcs('extracted/L1161兼容阅星瞳/update.ufw')
