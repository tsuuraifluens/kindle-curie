with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

from elftools.elf.elffile import ELFFile
import io
import struct

elf = ELFFile(io.BytesIO(elf_data))
rel_dyn = elf.get_section_by_name(".rel.dyn")
dynsym = elf.get_section_by_name(".dynsym")

print("--- Relocations in .rel.dyn ---")
rel_dyn_map = {}
for rel in rel_dyn.iter_relocations():
    sym = dynsym.get_symbol(rel['r_info_sym']).name
    rel_dyn_map[rel['r_offset']] = sym
    print(f"  offset={hex(rel['r_offset'])}, type={rel['r_info_type']}, sym={sym}")

# Dump words in .data.rel.ro (0x3b920 - 0x3be88)
data_rel_ro = elf.get_section_by_name(".data.rel.ro")
if data_rel_ro:
    print(f"\n--- .data.rel.ro at {hex(data_rel_ro['sh_addr'])} size {hex(data_rel_ro['sh_size'])} ---")
    d_bytes = data_rel_ro.data()
    for off in range(0, len(d_bytes), 4):
        val = struct.unpack("<I", d_bytes[off:off+4])[0]
        va = data_rel_ro['sh_addr'] + off
        extra = ""
        if va in rel_dyn_map:
            extra = f" (reloc: {rel_dyn_map[va]})"
        # check if val points to code or string
        if 0x18f50 <= val <= 0x317f8:
            extra += f" -> function at {hex(val)}"
        elif 0x31800 <= val <= 0x36870:
            str_off = val - 0x10000
            end = elf_data.find(b"\x00", str_off)
            s = elf_data[str_off:min(str_off+50, end)]
            extra += f" -> rodata: {s}"
        print(f"  {hex(va)}: {hex(val)}{extra}")
