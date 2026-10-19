with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

print("Searching for indirect calls to vtable offsets 0x14 (setWakeUpAsync) and 0x18 (setPowerButtonAsync)...")

# We look for:
# ldr rX, [rY, #0x14] / #0x18
# followed by blx rX

insns = list(md.disasm(text_bytes, text_start))
for i in range(len(insns) - 1):
    ins = insns[i]
    if ins.mnemonic == 'ldr' and len(ins.operands) >= 2:
        if ins.operands[1].type == capstone.arm.ARM_OP_MEM:
            disp = ins.operands[1].mem.disp
            if disp in [0x14, 0x18, 0x1c]:
                reg = ins.operands[0].reg
                # check if next instruction is blx reg
                next_ins = insns[i+1]
                target_name = {0x14: "setWakeUpAsync", 0x18: "setPowerButtonAsync", 0x1c: "setPowerButtonIfActiveAsync"}[disp]
                print(f"Candidate at {hex(ins.address)}: {ins.mnemonic} {ins.op_str} (disp={hex(disp)} -> {target_name})")
                print(f"  Next ins: {hex(next_ins.address)}: {next_ins.mnemonic} {next_ins.op_str}")
                # Print context around this instruction (previous 5 insns, next 5 insns)
                ctx_start = max(0, i - 8)
                ctx_end = min(len(insns), i + 8)
                print("  --- Context ---")
                for j in range(ctx_start, ctx_end):
                    print(f"    {hex(insns[j].address)}: {insns[j].mnemonic} {insns[j].op_str}")
                print()
