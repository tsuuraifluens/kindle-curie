with open("binaries/libAccessories.so", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

start_va = 0x353fd & ~1
end_va = start_va + 0x300
code_bytes = elf_data[start_va : end_va]

print(f"--- Disassembly of BleAccessoryProvider::getCategoryJson ({hex(start_va)}) ---")
for ins in md.disasm(code_bytes, start_va):
    extra = ""
    if ins.mnemonic == 'ldr' and '[pc' in ins.op_str:
        disp = ins.operands[1].mem.disp
        target_addr = (ins.address + 4 & ~3) + disp
        if target_addr < len(elf_data):
            val = int.from_bytes(elf_data[target_addr : target_addr + 4], 'little')
            extra = f"  ; [pc] -> {hex(val)}"
            # check string
            s_addr = ins.address + 4 + val
            if 0 < s_addr < len(elf_data):
                end = elf_data.find(b"\x00", s_addr)
                if end != -1 and end - s_addr < 100:
                    extra += f" (str: {elf_data[s_addr:end]})"
    print(f"{hex(ins.address)}: {ins.mnemonic:8s} {ins.op_str:25s} {extra}")
