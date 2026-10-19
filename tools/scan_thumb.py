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

md_thumb = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

print("Scanning Thumb instructions for branches...")
for ins in md_thumb.disasm(text_data, text_addr):
    if ins.mnemonic in ['bl', 'blx']:
        # e.g. "bl #0x18e34" or "blx #0x18e34"
        for t_addr, t_name in targets.items():
            if hex(t_addr) in ins.op_str:
                print(f"HIT at {hex(ins.address)}: {ins.mnemonic} {ins.op_str} -> {t_name}")
