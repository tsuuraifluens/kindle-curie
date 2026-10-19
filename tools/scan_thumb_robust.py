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

offset = 0
total_len = len(text_data)
hits = []

while offset < total_len:
    # Try disassembling from current offset
    curr_addr = text_addr + offset
    # Take a chunk of up to 1024 bytes
    chunk = text_data[offset:min(offset + 1024, total_len)]
    disassembled_any = False
    for ins in md_thumb.disasm(chunk, curr_addr):
        disassembled_any = True
        offset += ins.size
        if ins.mnemonic in ['bl', 'blx']:
            op = ins.op_str.strip()
            # Check target
            for t_addr, t_name in targets.items():
                if hex(t_addr) in op or f"#{t_addr}" in op or f"#{hex(t_addr)}" in op:
                    print(f"FOUND HIT at {hex(ins.address)}: {ins.mnemonic} {ins.op_str} -> {t_name}")
                    hits.append((ins.address, t_name))
    if not disassembled_any:
        # Advance by 2 bytes (Thumb instruction alignment)
        offset += 2

print(f"Scan complete. Total hits: {len(hits)}")
