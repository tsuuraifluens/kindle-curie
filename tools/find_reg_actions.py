with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

from elftools.elf.elffile import ELFFile
import io
import capstone

elf = ELFFile(io.BytesIO(elf_data))
dynsym = elf.get_section_by_name(".dynsym")

print("--- Searching symbols for registerDefaultActions ---")
for sym in dynsym.iter_symbols():
    if "registerdefaultactions" in sym.name.lower():
        print(f"Dynsym: {sym.name} @ {hex(sym['st_value'])}")

# Check demangled names in rodata around 0x34040
off = 0x34000 - 0x10000
ro_chunk = elf_data[off:off+500]
for p in ro_chunk.split(b"\x00"):
    if b"registerDefaultActions" in p:
        print(f"Demangled in rodata: {p}")
