import struct
from elftools.elf.elffile import ELFFile
import capstone

with open("binaries/input_manager", "rb") as f:
    elf = ELFFile(f)
    text_sec = elf.get_section_by_name(".text")
    text_data = text_sec.data()
    text_addr = text_sec['sh_addr']

targets = {
    0x18e34: "setWakeUpAsync",
    0x184ec: "setPowerButtonIfActiveAsync",
    0x18d50: "setPowerButtonAsync",
    0x1857c: "stepBrightnessWithUI",
}

print(f".text from {hex(text_addr)} to {hex(text_addr + len(text_data))}")

# 1. Search literal pool references (32-bit words)
for addr, name in targets.items():
    pattern = struct.pack("<I", addr)
    pos = 0
    while True:
        pos = text_data.find(pattern, pos)
        if pos == -1:
            break
        print(f"Literal reference to {name} ({hex(addr)}) at {hex(text_addr + pos)}")
        pos += 4

# 2. Check ARM mode BL/BLX
md_arm = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
md_arm.detail = True
for ins in md_arm.disasm(text_data, text_addr):
    if ins.mnemonic in ['bl', 'blx', 'b']:
        for op in ins.operands:
            if op.type == capstone.arm.ARM_OP_IMM and op.imm in targets:
                print(f"ARM branch at {hex(ins.address)}: {ins.mnemonic} {hex(op.imm)} -> {targets[op.imm]}")

# 3. Check Thumb mode BL/BLX (decode manually to be 100% sure)
def decode_thumb_bl(b0, b1, b2, b3, pc):
    # pc = ins_addr + 4
    hw1 = b0 | (b1 << 8)
    hw2 = b2 | (b3 << 8)
    if (hw1 & 0xf800) == 0xf000 and (hw2 & 0xd000) == 0x9000: # BL or BLX
        s = (hw1 >> 10) & 1
        imm10 = hw1 & 0x3ff
        j1 = (hw2 >> 13) & 1
        j2 = (hw2 >> 11) & 1
        imm11 = hw2 & 0x7ff
        is_blx = (hw2 & 0x1000) == 0
        
        i1 = ~(j1 ^ s) & 1
        i2 = ~(j2 ^ s) & 1
        
        imm32 = (s << 24) | (i1 << 23) | (i2 << 22) | (imm10 << 12) | (imm11 << 1)
        if s:
            imm32 -= (1 << 25)
            
        if is_blx:
            target = (pc & ~3) + (imm32 & ~3)
        else:
            target = pc + imm32
        return is_blx, target
    return None, None

for i in range(0, len(text_data) - 4, 2):
    b = text_data[i:i+4]
    pc = text_addr + i + 4
    is_blx, target = decode_thumb_bl(b[0], b[1], b[2], b[3], pc)
    if target in targets:
        print(f"Thumb {'blx' if is_blx else 'bl'} at {hex(text_addr + i)} -> {hex(target)} ({targets[target]})")
