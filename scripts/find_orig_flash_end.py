import struct

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
with open(orig_path, 'rb') as f:
    orig = f.read()

orig_f = orig[0x400 : 0x400 + 0x34000]

# Find last non-0xFF byte in orig_f
last_non_ff = -1
for i in range(len(orig_f)-1, -1, -1):
    if orig_f[i] != 0xFF:
        last_non_ff = i
        break

print(f"In orig_f, last non-0xFF byte is at offset 0x{last_non_ff:06X}")
print(f"Total size of orig_f: 0x{len(orig_f):06X}")
print(f"Number of trailing 0xFF bytes: {len(orig_f) - 1 - last_non_ff}")
