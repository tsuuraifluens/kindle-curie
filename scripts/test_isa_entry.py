import struct

def test_q32s_decode():
    with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
        data = f.read()

    print("=== Disassembly of Entry Point (Virtual: 0x01E00120) ===")
    
    # Let's inspect the first 64 bytes
    pc = 0
    base = 0x01E00120
    while pc < 64:
        v_pc = base + pc
        w0 = int.from_bytes(data[pc:pc+2], 'little')
        
        # Check instruction patterns
        # Group 0: 0x0xxx -> moves, calls, rts
        # Group 7: 0xFxxx -> extensions, load/store, branches
        # Group 6: 0xExxx -> imm arithmetic
        # Group 5: 0xDxxx
        # Group 1: 0x1xxx
        # Group 2: 0x2xxx
        # Group 3: 0x3xxx
        # Group 4: 0x4xxx
        
        ins_len = 2
        ins_str = ""
        
        if (w0 & 0xF000) == 0xF000: # 32-bit instruction
            w1 = int.from_bytes(data[pc+2:pc+4], 'little')
            ins_len = 4
            ins_str = f"{w0:04X} {w1:04X}"
        elif (w0 & 0xFF00) == 0xC000 or (w0 & 0xFF00) == 0xC100 or (w0 & 0xF000) == 0xC000:
            pass
        
        print(f"0x{v_pc:08X} (app+0x{pc:04X}): {data[pc:pc+ins_len].hex(' '):12s} | raw: 0x{w0:04X}")
        pc += ins_len

if __name__ == '__main__':
    test_q32s_decode()
