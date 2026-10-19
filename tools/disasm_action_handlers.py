with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

start_va = 0x21000
end_va = 0x21450
code_bytes = elf_data[start_va - 0x10000 : end_va - 0x10000]

print(f"--- Disassembly of Action Handlers ({hex(start_va)} - {hex(end_va)}) ---")
insns = list(md.disasm(code_bytes, start_va))

# First pass: collect literal pool values
literals = {}
for ins in insns:
    if ins.mnemonic == 'ldr' and '[pc' in ins.op_str:
        disp = ins.operands[1].mem.disp
        target_addr = (ins.address + 4 & ~3) + disp
        if target_addr < end_va:
            val = int.from_bytes(elf_data[target_addr - 0x10000 : target_addr - 0x10000 + 4], 'little')
            literals[ins.address] = (target_addr, val)

for ins in insns:
    extra = ""
    if ins.address in literals:
        t_addr, val = literals[ins.address]
        extra = f"  ; [pc, #{hex(t_addr - (ins.address + 4 & ~3))}] -> {hex(val)}"
        # Check if val is offset or address
        if 0x31800 <= val <= 0x36870:
            str_off = val - 0x10000
            end = elf_data.find(b"\x00", str_off)
            extra += f" (str: {elf_data[str_off:end]})"
        # Check if val + PC points to rodata
        pc_rel = ins.address + 4 + val
        if 0x31800 <= pc_rel <= 0x36870:
            str_off = pc_rel - 0x10000
            end = elf_data.find(b"\x00", str_off)
            extra += f" (pc+rel str: {elf_data[str_off:end]})"
            
    print(f"{hex(ins.address)}: {ins.mnemonic:8s} {ins.op_str:25s} {extra}")
