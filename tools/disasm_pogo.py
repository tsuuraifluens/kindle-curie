with open("binaries/libAccessories.so", "rb") as f:
    elf_data = f.read()

from elftools.elf.elffile import ELFFile
import io
import capstone

elf = ELFFile(io.BytesIO(elf_data))
dynsym = elf.get_section_by_name(".dynsym")

# Find Pogo symbols
pogo_syms = []
for s in dynsym.iter_symbols():
    if 'pogo' in s.name.lower():
        pogo_syms.append((s.name, s['st_value'], s['st_size']))
        print(f"Pogo sym: {s.name} @ {hex(s['st_value'])} size {s['st_size']}")

md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

# Disassemble Pogo functions
for name, val, size in pogo_syms:
    if size > 0 and val > 0:
        addr = val & ~1
        code = elf_data[addr : addr + size]
        print(f"\n--- {name} ({hex(addr)}) ---")
        for ins in md.disasm(code, addr):
            # check indirect calls
            if ins.mnemonic == 'ldr' and len(ins.operands) >= 2 and ins.operands[1].type == capstone.arm.ARM_OP_MEM:
                disp = ins.operands[1].mem.disp
                if disp in [0x14, 0x18, 0x1c]:
                    name_v = {0x14: "setWakeUpAsync", 0x18: "setPowerButtonAsync", 0x1c: "setPowerButtonIfActiveAsync"}[disp]
                    print(f"  VTABLE CALL: {hex(ins.address)}: {ins.mnemonic} {ins.op_str} -> {name_v}")
