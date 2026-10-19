with open("binaries/acsbtfd", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

from elftools.elf.elffile import ELFFile
import io

elf = ELFFile(io.BytesIO(elf_data))
dynsym = elf.get_section_by_name(".dynsym")

for s in dynsym.iter_symbols():
    if any(k in s.name.lower() for k in ['powersave', 'onsuspend', 'readpowersaveconfig']):
        print(f"{s.name} @ {hex(s['st_value'])} (size {s['st_size']})")
