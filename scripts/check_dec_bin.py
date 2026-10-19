import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
with open(orig_path, 'rb') as f:
    orig_ufw = f.read()

with open('extracted/ufw_unpacked/decrypted.bin', 'rb') as f:
    dec_bin = f.read()

orig_f = bytearray(orig_ufw[0x400 : 0x400 + 0x34000])
# Let's decrypt orig_f with jl_sfc_cipher(key=0x190D) from 0x2000
orig_f_dec = bytearray(orig_f)
jl_sfc_cipher(orig_f_dec, 0x2000, 0x34000 - 0x2000, base=0, key=0x190D)

print("Comparing orig_f_dec with dec_bin:")
diffs = [i for i in range(len(orig_f_dec)) if orig_f_dec[i] != dec_bin[i]]
print(f"Diff count: {len(diffs)}")
for d in diffs[:20]:
    print(f"  0x{d:06X}: orig_f_dec=0x{orig_f_dec[d]:02X}, dec_bin=0x{dec_bin[d]:02X}")
if diffs:
    print(f"First diff: 0x{diffs[0]:06X}, Last diff: 0x{diffs[-1]:06X}")
