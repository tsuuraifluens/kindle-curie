with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

keycodes = {
    104: "KEY_PAGEUP (104)",
    109: "KEY_PAGEDOWN (109)",
    116: "KEY_POWER (116)",
    142: "KEY_SLEEP (142)",
    143: "KEY_WAKEUP (143)",
    224: "KEY_BRIGHTNESSDOWN (224)",
    225: "KEY_BRIGHTNESSUP (225)",
}

offset = 0
while offset < len(text_bytes) - 4:
    chunk = text_bytes[offset:offset+16]
    insns = list(md.disasm(chunk, text_start + offset))
    if insns:
        ins = insns[0]
        offset += ins.size
        # Check operands for immediate matching keycode
        for op in ins.operands:
            if op.type == capstone.arm.ARM_OP_IMM:
                val = op.imm
                if val in keycodes:
                    if ins.mnemonic in ['cmp', 'mov', 'movs', 'mov.w', 'teq']:
                        print(f"Match at {hex(ins.address)}: {ins.mnemonic} {ins.op_str} -> {keycodes[val]}")
    else:
        offset += 2
