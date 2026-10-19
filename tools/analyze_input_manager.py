import sys
from elftools.elf.elffile import ELFFile
from elftools.elf.sections import SymbolTableSection
import capstone

with open("binaries/input_manager", "rb") as f:
    elf = ELFFile(f)
    print("ELF Architecture:", elf.get_machine_arch())
    
    # List sections
    for i, sec in enumerate(elf.iter_sections()):
        print(f"[{i:2d}] {sec.name:20s} type={sec['sh_type']:10s} addr={hex(sec['sh_addr'])} size={hex(sec['sh_size'])}")
        
    # Search symbols
    print("\n--- Symbols matching 'Power' or 'Wake' or 'Sleep' or 'Key' ---")
    for sec in elf.iter_sections():
        if isinstance(sec, SymbolTableSection):
            for sym in sec.iter_symbols():
                name = sym.name
                if any(k.lower() in name.lower() for k in ['power', 'wake', 'sleep', 'button', 'category', 'action']):
                    print(f"{sym.name} @ {hex(sym['st_value'])} (size {sym['st_size']}, info {sym['st_info']['type']})")
