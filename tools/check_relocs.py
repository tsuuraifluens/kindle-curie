from elftools.elf.elffile import ELFFile
from elftools.elf.relocation import RelocationSection

with open("binaries/input_manager", "rb") as f:
    elf = ELFFile(f)
    dynsym = elf.get_section_by_name(".dynsym")
    
    for sec in elf.iter_sections():
        if isinstance(sec, RelocationSection):
            print(f"Relocation Section: {sec.name}")
            for rel in sec.iter_relocations():
                sym = dynsym.get_symbol(rel['r_info_sym'])
                if any(k.lower() in sym.name.lower() for k in ['power', 'wake', 'sleep', 'button', 'brightness']):
                    print(f"  offset={hex(rel['r_offset'])}, type={rel['r_info_type']}, sym={sym.name}")
