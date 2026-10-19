import os, sys, struct
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.cipher import jl_sfc_cipher

orig_ufw = open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb').read()
mod_ufw = open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw', 'rb').read()

chipkey = 0x190D
appbase = 0x2000
append = 0x033CBA
app_offset = 0x2120
app_size = 198824

def get_app(ufw):
    flash = bytearray(ufw[0x400:0x400+212992])
    jl_sfc_cipher(flash, appbase, append - appbase, appbase, chipkey)
    return flash[app_offset : app_offset + app_size]

orig_app = get_app(orig_ufw)
mod_app = get_app(mod_ufw)

print("=== TABLE 1 INSPECTION (0x2289C - 0x22914) ===")
# Each entry is 6 bytes: [report_id, modifier, reserved, keycode, reserved2, reserved3]
# Table 1 has 20 entries (0x2289C + 20*6 = 0x22914)
buttons = [
    "A (index 0)", "B (index 1)", "X (index 2)", "Y (index 3)",
    "L (index 4)", "R (index 5)", "ZL (index 6)", "ZR (index 7)",
    "Minus (index 8)", "Plus (index 9)", "L3 (index 10)", "R3 (index 11)",
    "Home (index 12)", "Capture (index 13)", "Pair (index 14)", "Reserved (index 15)",
    "Dpad Up (index 16)", "Dpad Down (index 17)", "Dpad Left (index 18)", "Dpad Right (index 19)"
]

t1_start = 0x2289C
for idx, bname in enumerate(buttons):
    off = t1_start + idx * 6
    o_ent = orig_app[off : off + 6]
    m_ent = mod_app[off : off + 6]
    changed = " [MODIFIED]" if o_ent != m_ent else ""
    print(f"  Entry {idx:2d} (0x{off:05X}) {bname:<22}: Orig [{o_ent.hex(' ')}] -> Mod [{m_ent.hex(' ')}]{changed}")

print("\n=== TABLE 3 INSPECTION (0x22A1C - 0x22A9A) ===")
# Table 3 entries are also 6 bytes:
# Let's inspect the entire range 0x22A1C to 0x22A9A (21 entries * 6 = 126 bytes = 0x7E bytes, 0x22A1C + 0x7E = 0x22A9A)
t3_start = 0x22A1C
for idx in range(21):
    off = t3_start + idx * 6
    o_ent = orig_app[off : off + 6]
    m_ent = mod_app[off : off + 6]
    changed = " [MODIFIED]" if o_ent != m_ent else ""
    print(f"  Entry {idx:2d} (0x{off:05X}): Orig [{o_ent.hex(' ')}] -> Mod [{m_ent.hex(' ')}]{changed}")
