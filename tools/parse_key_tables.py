import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

BUTTON_NAMES = [
    "Button 0 (A)",
    "Button 1 (B)",
    "Button 2 (X)",
    "Button 3 (Y)",
    "Button 4 (L1)",
    "Button 5 (R1)",
    "Button 6 (L2)",
    "Button 7 (R2)",
    "Button 8 (Select/Minus)",
    "Button 9 (Start/Plus)",
    "Button 10 (L3)",
    "Button 11 (R3)",
    "Button 12 (D-pad Up)",
    "Button 13 (D-pad Down)",
    "Button 14 (D-pad Left)",
    "Button 15 (D-pad Right)",
]

print("=== KEYBOARD TABLE (Offset 0x2289C) ===")
for i in range(16):
    off = 0x2289C + i * 6
    chunk = data[off : off + 6]
    report_id, mod, rsvd, key, extra1, extra2 = chunk
    name = BUTTON_NAMES[i]
    print(f"{name:<24} [off 0x{off:05X}]: {chunk.hex(' ')} -> ID={report_id}, Mod=0x{mod:02X}, Key=0x{key:02X}")

print("\n=== CONSUMER TABLE (Offset 0x22A1C) ===")
for i in range(16):
    off = 0x22A1C + i * 6
    chunk = data[off : off + 6]
    type_id, rsvd, usage_low, usage_high, extra1, extra2 = chunk
    usage = (usage_high << 8) | usage_low
    name = BUTTON_NAMES[i]
    print(f"{name:<24} [off 0x{off:05X}]: {chunk.hex(' ')} -> Type={type_id}, Usage=0x{usage:04X}")
