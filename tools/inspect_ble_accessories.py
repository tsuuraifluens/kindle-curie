with open("binaries/libAccessories.so", "rb") as f:
    elf_data = f.read()

from elftools.elf.elffile import ELFFile
import io

elf = ELFFile(io.BytesIO(elf_data))
dynsym = elf.get_section_by_name(".dynsym")

print("--- Methods of BleAccessoryProvider & BleTransport ---")
for s in dynsym.iter_symbols():
    if any(k in s.name for k in ['BleAccessoryProvider', 'BleTransport', 'AccessoryRegistry']):
        print(f"  {s.name} @ {hex(s['st_value'])}")

# Also search rodata of libAccessories.so for strings
off_ro = elf.get_section_by_name(".rodata")
if off_ro:
    ro_data = off_ro.data()
    ro_addr = off_ro['sh_addr']
    for p in ro_data.split(b"\x00"):
        if any(k in p.lower() for k in [b"page_turner", b"remote", b"wake", b"power", b"button", b"hogp", b"hid"]):
            if len(p) > 3:
                print(f"rodata: {p}")
