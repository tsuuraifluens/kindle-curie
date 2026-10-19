with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

start_va = 0x2f900
end_va = 0x2fc00
code_bytes = elf_data[start_va - 0x10000 : end_va - 0x10000]

print(f"--- Disassembly from {hex(start_va)} to {hex(end_va)} ---")
for ins in md.disasm(code_bytes, start_va):
    op_str = ins.op_str
    # If ldr pc-relative, print target address and value
    extra = ""
    if ins.mnemonic == 'ldr' and '[pc' in op_str:
        # compute target
        try:
            disp = ins.operands[1].mem.disp
            target_addr = (ins.address + 4 & ~3) + disp
            if target_addr < end_va:
                val = int.from_bytes(elf_data[target_addr - 0x10000 : target_addr - 0x10000 + 4], 'little')
                extra = f"  ; [pc, #{hex(disp)}] = {hex(target_addr)} -> {hex(val)}"
        except:
            pass
    print(f"{hex(ins.address)}: {ins.mnemonic:8s} {op_str:25s} {extra}")
