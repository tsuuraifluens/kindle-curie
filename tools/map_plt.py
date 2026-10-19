from elftools.elf.elffile import ELFFile
from elftools.elf.relocation import RelocationSection
import capstone

with open("binaries/input_manager", "rb") as f:
    elf = ELFFile(f)
    dynsym = elf.get_section_by_name(".dynsym")
    plt_sec = elf.get_section_by_name(".plt")
    text_sec = elf.get_section_by_name(".text")
    
    plt_data = plt_sec.data()
    plt_addr = plt_sec['sh_addr']
    text_data = text_sec.data()
    text_addr = text_sec['sh_addr']
    
    # Map GOT offset to symbol name
    got_to_sym = {}
    rel_plt = elf.get_section_by_name(".rel.plt")
    rel_list = list(rel_plt.iter_relocations())
    for idx, rel in enumerate(rel_list):
        sym = dynsym.get_symbol(rel['r_info_sym'])
        got_to_sym[rel['r_offset']] = (idx, sym.name)

    # In ARM, PLT0 is 20 bytes (0x14) or 16 bytes.
    # Each PLT entry is 12 bytes:
    #   add lr, pc, #offset
    #   ldr pc, [lr, #offset]!
    # Let's disassemble .plt
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    plt_entries = {} # addr -> sym_name
    
    # Let's inspect first few instructions of .plt to see entry size
    plt_insns = list(md.disasm(plt_data, plt_addr))
    print(f"PLT start: {hex(plt_addr)}, first insns:")
    for ins in plt_insns[:10]:
        print(f"  {hex(ins.address)}: {ins.mnemonic} {ins.op_str}")

    # Standard ARM PLT:
    # PLT0 is 20 bytes: 0x182d4 - 0x182e8
    # PLT1: 0x182e8 (12 bytes)
    # PLT2: 0x182f4 (12 bytes)
    # Let's verify by matching with rel_list
    print(f"\nTotal relocations in .rel.plt: {len(rel_list)}")
    for i in range(len(rel_list)):
        entry_addr = plt_addr + 20 + i * 12
        got_offset = rel_list[i]['r_offset']
        sym_name = rel_list[i]['r_info_sym']
        name = dynsym.get_symbol(sym_name).name
        plt_entries[entry_addr] = name
        if any(k.lower() in name.lower() for k in ['power', 'wake', 'sleep', 'brightness']):
            print(f"PLT[{i}] at {hex(entry_addr)} -> GOT {hex(got_offset)}: {name}")

    # Now scan .text for calls to these PLT entries
    # In ARM or THUMB?
    # Let's check whether .text is ARM or THUMB!
    # In ARM ELF, $a / $t mapping symbols tell us ARM or Thumb.
    symtab = elf.get_section_by_name(".symtab")
    if symtab:
        print("Symtab found!")
    else:
        print("No .symtab (stripped), checking ARM vs Thumb heuristic...")
