with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

start_va = 0x21250
end_va = 0x21380
code_bytes = elf_data[start_va - 0x10000 : end_va - 0x10000]

print(f"--- Disassembly of the 3 Lambdas ({hex(start_va)} - {hex(end_va)}) ---")
for ins in md.disasm(code_bytes, start_va):
    op_str = ins.op_str
    extra = ""
    if ins.mnemonic == 'ldr' and '[pc' in op_str:
        disp = ins.operands[1].mem.disp
        target_addr = (ins.address + 4 & ~3) + disp
        val = int.from_bytes(elf_data[target_addr - 0x10000 : target_addr - 0x10000 + 4], 'little')
        extra = f"  ; [pc] -> {hex(val)}"
        pc_rel = ins.address + 4 + val
        if 0x31800 <= pc_rel <= 0x36870:
            str_off = pc_rel - 0x10000
            end = elf_data.find(b"\x00", str_off)
            extra += f" (str: {elf_data[str_off:end]})"
    print(f"{hex(ins.address)}: {ins.mnemonic:8s} {op_str:25s} {extra}")
