import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16

with open('extracted/ufw_unpacked/decrypted.bin', 'rb') as f:
    dec = f.read()

target = 0x23C2
print(f"Searching for slice matching target CRC16: 0x{target:04X}")

# Let's test slices starting at 0x2020
for end in range(0x2020 + 32, 0x2000 + 0x30C9A + 1, 16):
    sub = dec[0x2020 : end]
    c = jl_crc16(sub)
    if c == target:
        print(f"MATCH: dec[0x2020 : 0x{end:X}] (len 0x{end-0x2020:X})")
        break
else:
    print("No match found from 0x2020 with step 16, testing all lengths...")
    for length in range(32, 0x1000):
        sub = dec[0x2020 : 0x2020 + length]
        if jl_crc16(sub) == target:
            print(f"MATCH: dec[0x2020 : 0x{0x2020+length:X}] (len 0x{length:X})")
            break
