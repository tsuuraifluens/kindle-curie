from elftools.elf.elffile import ELFFile
from elftools.elf.relocation import RelocationSection
import capstone

with open("binaries/libAccessories.so", "rb") as f:
    elf = ELFFile(f)
    dynsym = elf.get_section_by_name(".dynsym")
    rel_plt = elf.get_section_by_name(".rel.plt")
    plt_sec = elf.get_section_by_name(".plt")
    text_sec = elf.get_section_by_name(".text")
    
    text_data = text_sec.data()
    text_addr = text_sec['sh_addr']
    plt_data = plt_sec.data()
    plt_addr = plt_sec['sh_addr']
    
    print("libAccessories.so PLT start:", hex(plt_addr))
    
    # Map symbols in rel.plt
    power_plts = {}
    for i, r in enumerate(rel_plt.iter_relocations()):
        sym = dynsym.get_symbol(r['r_info_sym']).name
        plt_entry = plt_addr + 20 + i * 12
        if any(k in sym.lower() for k in ['wakeup', 'powerbutton', 'setsuspend']):
            print(f"PLT[{i}] at {hex(plt_entry)}: {sym}")
            power_plts[plt_entry] = sym

# Scan text for calls to power_plts
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
print("\nScanning libAccessories.so for calls to power PLTs...")
for i in range(0, len(text_data) - 4, 2):
    chunk = text_data[i:i+4]
    insns = list(md.disasm(chunk, text_addr + i))
    if insns:
        ins = insns[0]
        if ins.mnemonic in ['bl', 'blx']:
            for p_addr, p_name in power_plts.items():
                if hex(p_addr) in ins.op_str:
                    print(f"Call at {hex(ins.address)}: {ins.mnemonic} {ins.op_str} -> {p_name}")
