with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

start_va = 0x2ff80
end_va = 0x30150
code_bytes = elf_data[start_va - 0x10000 : end_va - 0x10000]

print(f"--- Disassembly from {hex(start_va)} to {hex(end_va)} ---")
insns = list(md.disasm(code_bytes, start_va))

for ins in insns:
    extra = ""
    if ins.mnemonic == 'ldr' and '[pc' in ins.op_str:
        disp = ins.operands[1].mem.disp
        target_addr = (ins.address + 4 & ~3) + disp
        if target_addr < len(elf_data) + 0x10000:
            val = int.from_bytes(elf_data[target_addr - 0x10000 : target_addr - 0x10000 + 4], 'little')
            extra = f"  ; [pc, #{hex(disp)}] = {hex(target_addr)} -> {hex(val)}"
            # If val + pc points to something
            pc_rel = ins.address + 4 + val
            if 0x31800 <= pc_rel <= 0x36870:
                str_off = pc_rel - 0x10000
                end = elf_data.find(b"\x00", str_off)
                extra += f" (str: {elf_data[str_off:end]})"
    print(f"{hex(ins.address)}: {ins.mnemonic:8s} {ins.op_str:25s} {extra}")
