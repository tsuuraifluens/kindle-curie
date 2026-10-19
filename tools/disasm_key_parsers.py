with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

print("--- Function around 0x22900 ---")
chunk1 = elf_data[0x22700 - 0x10000 : 0x22a00 - 0x10000]
for ins in list(md.disasm(chunk1, 0x22700))[:40]:
    print(f"{hex(ins.address)}: {ins.mnemonic} {ins.op_str}")

print("\n--- Function around 0x25000 ---")
chunk2 = elf_data[0x24fa0 - 0x10000 : 0x250d0 - 0x10000]
for ins in list(md.disasm(chunk2, 0x24fa0))[:40]:
    print(f"{hex(ins.address)}: {ins.mnemonic} {ins.op_str}")
