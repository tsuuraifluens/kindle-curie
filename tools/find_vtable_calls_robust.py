with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

print("Robust linear scan for indirect calls through vtable offsets 0x14, 0x18, 0x1c...")

offset = 0
total_len = len(text_bytes)

while offset < total_len - 4:
    addr = text_start + offset
    insns = list(md.disasm(text_bytes[offset:offset+16], addr))
    if insns:
        ins = insns[0]
        offset += ins.size
        if ins.mnemonic == 'ldr' and len(ins.operands) >= 2:
            if ins.operands[1].type == capstone.arm.ARM_OP_MEM:
                disp = ins.operands[1].mem.disp
                if disp in [0x14, 0x18, 0x1c] and ins.operands[1].mem.base != capstone.arm.ARM_REG_SP:
                    # check if base is SP or R7 (stack pointer/frame pointer)
                    base_reg = ins.reg_name(ins.operands[1].mem.base)
                    if base_reg not in ['sp', 'r7']:
                        target_name = {0x14: "setWakeUpAsync", 0x18: "setPowerButtonAsync", 0x1c: "setPowerButtonIfActiveAsync"}[disp]
                        print(f"Match at {hex(ins.address)}: {ins.mnemonic} {ins.op_str} (base={base_reg}, disp={hex(disp)} -> {target_name})")
                        # check next instruction
                        if len(insns) > 1:
                            print(f"  Next: {hex(insns[1].address)}: {insns[1].mnemonic} {insns[1].op_str}")
    else:
        offset += 2
