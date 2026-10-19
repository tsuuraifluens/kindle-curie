with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

from elftools.elf.elffile import ELFFile
import io

elf = ELFFile(io.BytesIO(elf_data))
dynsym = elf.get_section_by_name(".dynsym")
rel_plt = elf.get_section_by_name(".rel.plt")

got_map = {}
for rel in rel_plt.iter_relocations():
    sym = dynsym.get_symbol(rel['r_info_sym'])
    got_map[rel['r_offset']] = sym.name

# Check GOT offsets around 0x3c3c0 - 0x3c3e0
print("GOT entries around 0x3c380 - 0x3c3e0:")
for off in sorted(got_map.keys()):
    if 0x3c360 <= off <= 0x3c400:
        print(f"  GOT {hex(off)}: {got_map[off]}")

# Now let's calculate the exact PLT entry for any GOT offset:
# For an entry at plt_entry:
# insn 0 (addr): add ip, pc, #0, #12 -> ip = addr + 8
# insn 1 (addr+4): add ip, ip, #0x23000 (or #0x24000)
# insn 2 (addr+8): ldr pc, [ip, #imm]
# Total target = addr + 8 + imm_high + imm_low
# So target = GOT offset!
# Let's verify by checking every 12 bytes in .plt from 0x182e8
plt_addr = 0x182e8
plt_to_sym = {}
while plt_addr < 0x18f50:
    off = plt_addr - 0x10000
    insn0 = int.from_bytes(elf_data[off:off+4], 'little')
    insn1 = int.from_bytes(elf_data[off+4:off+8], 'little')
    insn2 = int.from_bytes(elf_data[off+8:off+12], 'little')
    
    # decode add ip, pc, #rot_imm
    # in ARM, insn1: e28cc023 -> add ip, ip, #0x23000
    # insn2: e5bcf588 -> ldr pc, [ip, #0x588]!
    imm_high = (insn1 & 0xff) << (((insn1 >> 8) & 0xf) * -2 % 32) # roughly
    # simpler: just compute ip after insn1
    # insn0 is e28fc...: add ip, pc, ...
    rot0 = (insn0 >> 8) & 0xf
    val0 = insn0 & 0xff
    val0_rot = (val0 >> (rot0 * 2)) | ((val0 << (32 - rot0 * 2)) & 0xffffffff)
    ip = plt_addr + 8 + val0_rot
    
    rot1 = (insn1 >> 8) & 0xf
    val1 = insn1 & 0xff
    val1_rot = (val1 >> (rot1 * 2)) | ((val1 << (32 - rot1 * 2)) & 0xffffffff)
    ip = ip + val1_rot
    
    imm2 = insn2 & 0xfff
    got_target = ip + imm2
    
    if got_target in got_map:
        sym = got_map[got_target]
        plt_to_sym[plt_addr] = sym
        if any(k.lower() in sym.lower() for k in ['power', 'wake', 'sleep', 'brightness']):
            print(f"PLT at {hex(plt_addr)} -> GOT {hex(got_target)}: {sym}")
    else:
        print(f"PLT at {hex(plt_addr)} -> GOT {hex(got_target)} NOT FOUND")
    
    plt_addr += 12
