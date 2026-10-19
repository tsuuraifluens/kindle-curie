import os
from elftools.elf.elffile import ELFFile
from elftools.elf.relocation import RelocationSection

bin_dir = "binaries"
for fname in os.listdir(bin_dir):
    fpath = os.path.join(bin_dir, fname)
    if not os.path.isfile(fpath):
        continue
    try:
        with open(fpath, "rb") as f:
            elf = ELFFile(f)
            dynsym = elf.get_section_by_name(".dynsym")
            if not dynsym:
                continue
            matches = []
            for sym in dynsym.iter_symbols():
                if any(k in sym.name.lower() for k in ['wakeup', 'powerbutton', 'setsuspend', 'resume']):
                    matches.append(sym.name)
            if matches:
                print(f"=== {fname} ===")
                for m in matches:
                    print(f"  {m}")
    except Exception as e:
        pass
