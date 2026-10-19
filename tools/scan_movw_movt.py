with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

# Target string addresses
targets = {
    0x33e34: "D def:action:sleep...",
    0x33e57: "D def:action:brightness_down...",
    0x33e84: "D def:action:brightness_up...",
    0x33de6: "system.sleep",
    0x33db8: "display.brightness_up",
    0x33dce: "display.brightness_down",
}

# In Thumb-2, strings can also be loaded via:
# 1) ldr rX, [pc, #imm] -> value in literal pool
# Wait! Did literal pool contain (target - GOT)? Or target address?
# Or 2) movw rX, #lower16; movt rX, #upper16
# Let's check movw/movt combinations!

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

print("Scanning for movw/movt or adr pointing to targets...")
# Scan instruction by instruction
for i in range(0, len(text_bytes) - 8, 2):
    addr = text_start + i
    chunk = text_bytes[i:i+8]
    insns = list(md.disasm(chunk, addr))
    if len(insns) >= 2:
        ins1, ins2 = insns[0], insns[1]
        # Check movw + movt
        if ins1.mnemonic == 'movw' and ins2.mnemonic == 'movt':
            reg1 = ins1.operands[0].reg
            reg2 = ins2.operands[0].reg
            if reg1 == reg2:
                imm_low = ins1.operands[1].imm
                imm_high = ins2.operands[1].imm
                full_val = (imm_high << 16) | imm_low
                for t_addr, name in targets.items():
                    if full_val == t_addr:
                        print(f"movw+movt at {hex(ins1.address)}: reg={ins1.reg_name(reg1)} -> {name} ({hex(full_val)})")

print("Done scanning movw/movt.")
