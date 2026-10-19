import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

base = 0x01E00120

def find_calls_to(target_vaddr):
    results = []
    for pc in range(0, len(data)-4, 2):
        w0 = struct.unpack_from('<H', data, pc)[0]
        if (w0 & 0xFFC0) == 0xF380:
            w1 = struct.unpack_from('<H', data, pc+2)[0]
            imm6 = w0 & 0x3F
            if imm6 & 0x20:
                imm6 -= 0x40
            vaddr = base + pc
            target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
            if target == target_vaddr:
                results.append((pc, vaddr))
    return results

for name, target in [("func_4080 (ble_hogp_send_report?)", base + 0x04080), 
                     ("func_3394 (att_send)", base + 0x03394)]:
    calls = find_calls_to(target)
    print(f'Calls to {name} (0x{target:08X}):')
    for pc, vaddr in calls:
        print(f'  at 0x{pc:05X} (V: 0x{vaddr:08X})')
