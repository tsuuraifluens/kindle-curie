with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

from elftools.elf.elffile import ELFFile
import io

elf = ELFFile(io.BytesIO(elf_data))
dynsym = elf.get_section_by_name(".dynsym")

for s in dynsym.iter_symbols():
    if "handleproviderevents" in s.name.lower():
        print(f"{s.name} @ {hex(s['st_value'])} (size {s['st_size']})")
