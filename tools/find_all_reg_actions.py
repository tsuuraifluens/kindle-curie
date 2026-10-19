with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

print("Searching for ALL calls to ActionRegistry::registerAction (0x3020c)...")
target = 0x3020c

offset = 0
calls = []
while offset < len(text_bytes) - 4:
    chunk = text_bytes[offset:offset+8]
    insns = list(md.disasm(chunk, text_start + offset))
    if insns:
        ins = insns[0]
        offset += ins.size
        if ins.mnemonic in ['bl', 'blx'] and hex(target) in ins.op_str:
            print(f"Call at {hex(ins.address)}: {ins.mnemonic} {ins.op_str}")
            calls.append(ins.address)
    else:
        offset += 2

print(f"Total calls to registerAction: {len(calls)}")
