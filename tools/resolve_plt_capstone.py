from elftools.elf.elffile import ELFFile
import capstone

with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import io
elf = ELFFile(io.BytesIO(elf_data))
dynsym = elf.get_section_by_name(".dynsym")
rel_plt = elf.get_section_by_name(".rel.plt")

got_map = {}
for rel in rel_plt.iter_relocations():
    sym = dynsym.get_symbol(rel['r_info_sym'])
    got_map[rel['r_offset']] = sym.name

md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
md.detail = True

plt_map = {} # plt_addr -> (got_addr, sym_name)

# Disassemble PLT from 0x182e8 to 0x18f50 in steps of 12
for plt_addr in range(0x182e8, 0x18f50, 12):
    off = plt_addr - 0x10000
    insns = list(md.disasm(elf_data[off:off+12], plt_addr))
    if len(insns) == 3:
        # insn 0: add ip, pc, #imm
        # insn 1: add ip, ip, #imm
        # insn 2: ldr pc, [ip, #imm]!
        ip = 0
        # insn 0: ip = pc + imm
        imm0 = insns[0].operands[2].imm
        ip = plt_addr + 8 + imm0
        imm1 = insns[1].operands[2].imm
        ip = ip + imm1
        imm2 = insns[2].operands[1].mem.disp
        got_addr = ip + imm2
        if got_addr in got_map:
            name = got_map[got_addr]
            plt_map[plt_addr] = (got_addr, name)
            if any(k.lower() in name.lower() for k in ['power', 'wake', 'sleep', 'brightness']):
                print(f"PLT {hex(plt_addr)} -> GOT {hex(got_addr)}: {name}")

print(f"Total resolved PLT entries: {len(plt_map)} / 260")
