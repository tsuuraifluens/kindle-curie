import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base = 0x01E00120

from inspect_setup_funcs import disasm_range

print("=== 0x16B2A - 0x16BD0 ===")
disasm_range(0x16B2A, 0x16BD0)

print("\n=== 0x186C0 - 0x18720 ===")
disasm_range(0x186C0, 0x18720)
