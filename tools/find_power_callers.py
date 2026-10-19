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

print(f".text starts at {hex(text_addr)}, size={hex(len(text_data))}")

md_arm = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
md_thumb = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

print("\n--- Scanning for branch targets in ARM mode ---")
for ins in md_arm.disasm(text_data, text_addr):
    if ins.mnemonic in ['bl', 'b', 'blx']:
        try:
            target = int(ins.op_str.strip('#'), 16)
            if target in targets:
                print(f"ARM {hex(ins.address)}: {ins.mnemonic} {ins.op_str} -> {targets[target]}")
        except:
            pass

print("\n--- Scanning for branch targets in THUMB mode ---")
for ins in md_thumb.disasm(text_data, text_addr):
    if ins.mnemonic in ['bl', 'b', 'blx', 'b.w']:
        try:
            target = int(ins.op_str.strip('#'), 16)
            if target in targets:
                print(f"THUMB {hex(ins.address)}: {ins.mnemonic} {ins.op_str} -> {targets[target]}")
        except:
            pass
