with open("binaries/libAccessories.so", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

start_va = 0x34259 & ~1
end_va = start_va + 0x200
code_bytes = elf_data[start_va : end_va]

print(f"--- Disassembly of BleAccessoryProvider::onEvent ({hex(start_va)}) ---")
for ins in md.disasm(code_bytes, start_va):
    print(f"{hex(ins.address)}: {ins.mnemonic:8s} {ins.op_str}")
