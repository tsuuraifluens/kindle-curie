with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

start_va = 0x30e00
end_va = 0x31000
code_bytes = elf_data[start_va - 0x10000 : end_va - 0x10000]

print(f"--- Disassembly from {hex(start_va)} to {hex(end_va)} ---")
for ins in md.disasm(code_bytes, start_va):
    print(f"{hex(ins.address)}: {ins.mnemonic:8s} {ins.op_str}")
