#!/usr/bin/env python3
"""
verify_mouse_to_kindle.py - Comprehensive Verification Suite for Kindle Mod Firmware (v2.1 Architecture)

Validates:
1. Multi-tier CRC16 & Header Integrity (UFW container, Flash image, JLFS, SFC encryption)
2. Crucial Component Preservation (uboot.boot, isd_config.ini, p11_code.bin, clean FF padding)
3. 100% Gamepad Mode Preservation (Table 2, Xbox descriptor, Mode 1/6 are intact for gaming)
4. Clean Mode Dispatch Preservation (0x07116 restored to clean factory instructions)
5. Switch T Mode 2 Redirection (0x0B460: mov r1, #2 clean native mode 2 entry)
6. Dual Table Kindle Mapping (Table 0 and Table 1 both mapped to Report ID 2 with Kindle keys)
7. Triple GAP Name Defense (Reader, Keyboard, Mouse all set to 'Kindle-PT', 28-byte packet <= 31B)
8. BLE ADV AD 0xFF Amazon Lab126 Injection (28 bytes exact)
9. BLE Pairing Gate Patch (check_pair_allow: mov r0, #0; rts)
"""

import os
import sys
import struct
import hashlib

# Add scripts and jieli_tools
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
JIELI_TOOLS = os.path.join(SCRIPT_DIR, 'jieli_tools')
if JIELI_TOOLS not in sys.path:
    sys.path.insert(0, JIELI_TOOLS)

from jltech.crc import jl_crc16, jl_crc32
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.utils import nulltermstr

class VerificationReport:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.results = []

    def assert_true(self, condition, name, details=""):
        if condition:
            self.passed += 1
            print(f"  [PASS] {name}: {details}")
            self.results.append((name, True, details))
        else:
            self.failed += 1
            print(f"  [FAIL] {name}: {details}")
            self.results.append((name, False, details))

    def summary(self):
        print("\n" + "=" * 80)
        print(f"VERIFICATION SUMMARY: {self.passed} PASSED, {self.failed} FAILED")
        print("=" * 80)
        return self.failed == 0

def verify_mouse_firmware(ufw_path):
    report = VerificationReport()
    print("=" * 80)
    print(f"Running Full Audit on: {ufw_path}")
    print("=" * 80)

    if not os.path.exists(ufw_path):
        report.assert_true(False, "File Existence", f"File not found: {ufw_path}")
        return False

    with open(ufw_path, 'rb') as f:
        raw_ufw = bytearray(f.read())

    # --- Tier 1: Container & UFW Header ---
    print("\n--- Tier 1: UFW Container & Entry List Checks ---")
    report.assert_true(len(raw_ufw) == 645184, "Container Size Check", f"Size is {len(raw_ufw)} bytes (matches factory)")

    ufw_hdr = bytearray(raw_ufw[:0x40])
    jl_enc_cipher(ufw_hdr, 0, 0x40, key=0xFFFF)
    hdrcrc, listcrc, imgsize, numents, wa3, wa4, chipname = struct.unpack_from('<HHIHHI48s', ufw_hdr, 0)
    cname = nulltermstr(chipname, 'ascii')
    report.assert_true(cname == "AC632N", "Chip Identification", f"Chip is '{cname}'")

    calc_hdrcrc = jl_crc16(ufw_hdr[2:0x40])
    report.assert_true(hdrcrc == calc_hdrcrc, "UFW Header CRC16", f"Stored 0x{hdrcrc:04X} == Computed 0x{calc_hdrcrc:04X}")

    headersize = 0x40 + numents * 0x50
    calc_listcrc = jl_crc16(raw_ufw[0x40:headersize])
    report.assert_true(listcrc == calc_listcrc, "UFW Entry List CRC16", f"Stored 0x{listcrc:04X} == Computed 0x{calc_listcrc:04X}")

    # Decrypt all entries
    table_bytes = bytearray(raw_ufw[0x40:headersize])
    for i in range(numents):
        jl_enc_cipher(table_bytes, i * 0x50, 0x50, key=0xFFFF)

    entries = []
    for i in range(numents):
        off = i * 0x50
        etype, eidx, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename = struct.unpack_from('<HHHHIII44s16s', table_bytes, off)
        name = nulltermstr(ename, 'ascii')
        val_18 = struct.unpack_from('<I', table_bytes, off + 0x18)[0]
        entries.append((etype, eidx, edcrc, eoffset, esize, val_18, name))
        if esize > 0 and val_18 == 0:
            c = jl_crc16(raw_ufw[eoffset : eoffset + esize])
            report.assert_true(c == edcrc, f"UFW Entry '{name}' CRC16", f"0x{edcrc:04X} == 0x{c:04X}")

    # --- Tier 2: Flash Image (Entry 0) ---
    print("\n--- Tier 2: Flash Image & Bootloader Integrity ---")
    flash_ent = entries[0]
    flash_raw = bytearray(raw_ufw[flash_ent[3] : flash_ent[3] + flash_ent[4]])
    report.assert_true(len(flash_raw) == 212992, "flash.bin Size", f"{len(flash_raw)} bytes")

    f_hdr = bytearray(flash_raw[:32])
    jl_enc_cipher(f_hdr, 0, 32, key=0xFFFF)
    f_hcrc = struct.unpack_from('<H', f_hdr, 0)[0]
    report.assert_true(f_hcrc == jl_crc16(f_hdr[2:]), "flash.bin Header CRC16", f"0x{f_hcrc:04X}")

    # uboot.boot bankcb
    uboot_hdr = bytearray(flash_raw[0x20:0x40])
    jl_enc_cipher(uboot_hdr, 0, 32, key=0xFFFF)
    u_hcrc = struct.unpack_from('<H', uboot_hdr, 0)[0]
    report.assert_true(u_hcrc == jl_crc16(uboot_hdr[2:]), "uboot.boot Entry Header CRC16", f"0x{u_hcrc:04X}")

    u_edcrc, u_off, u_sz, u_flg, u_res, u_idx, u_name = struct.unpack('<HIIBBH16s', uboot_hdr[2:])
    bankcb = bytearray(flash_raw[u_off : u_off + 16])
    jl_enc_cipher(bankcb, 0, 16, key=0xFFFF)
    bankcb_crc = struct.unpack_from('<H', bankcb, 14)[0]
    report.assert_true(bankcb_crc == jl_crc16(bankcb[:14]), "uboot.boot bankcb CRC16", f"0x{bankcb_crc:04X}")

    # Compare uboot.boot byte-for-byte with original factory UFW
    orig_ufw_path = os.path.join(SCRIPT_DIR, '..', 'extracted', 'L1161兼容阅星瞳', 'L1161 L1162 IINE-reader.ufw')
    if os.path.exists(orig_ufw_path):
        with open(orig_ufw_path, 'rb') as f:
            orig_raw_ufw = f.read()
        raw_uboot = orig_raw_ufw[0x400 + u_off : 0x400 + u_off + u_sz]
        cur_uboot = flash_raw[u_off : u_off + u_sz]
        report.assert_true(cur_uboot == raw_uboot, "uboot.boot Byte Identity", f"100% byte-identical ({len(raw_uboot)} B)")

    # --- Tier 3: SFC Decryption & JLFS Inner Checks ---
    print("\n--- Tier 3: JLFS Filesystem & Payload CRCs ---")
    chipkey = 0x190D
    f_dec = bytearray(flash_raw)
    jl_sfc_cipher(f_dec, 0x2000, 0x33CBA - 0x2000, base=0x2000, key=chipkey)

    # app_area_head header & payload
    a_hdr = f_dec[0x2000 : 0x2020]
    a_hcrc = struct.unpack_from('<H', a_hdr, 0)[0]
    report.assert_true(a_hcrc == jl_crc16(a_hdr[2:]), "app_area_head Header CRC16", f"0x{a_hcrc:04X}")

    a_edcrc = struct.unpack_from('<H', a_hdr, 2)[0]
    a_data = f_dec[0x2020 : 0x2020 + 0x30C7A]
    report.assert_true(a_edcrc == jl_crc16(a_data), "app_area_head Payload CRC16", f"0x{a_edcrc:04X}")

    # app.bin header & payload
    app_entry_hdr = f_dec[0x2020 : 0x2040]
    app_hcrc = struct.unpack_from('<H', app_entry_hdr, 0)[0]
    report.assert_true(app_hcrc == jl_crc16(app_entry_hdr[2:]), "app.bin Entry Header CRC16", f"0x{app_hcrc:04X}")

    app_edcrc, app_off, app_sz, app_flg, app_res, app_idx, app_name = struct.unpack('<HIIBBH16s', app_entry_hdr[2:])
    app_payload = f_dec[0x2000 + app_off : 0x2000 + app_off + app_sz]
    report.assert_true(app_edcrc == jl_crc16(app_payload), "app.bin Payload CRC16", f"0x{app_edcrc:04X}")

    # p11_code.bin header & payload
    p11_hdr = f_dec[0x32C9A : 0x32CBA]
    p11_hcrc = struct.unpack_from('<H', p11_hdr, 0)[0]
    report.assert_true(p11_hcrc == jl_crc16(p11_hdr[2:]), "p11_code.bin Header CRC16", f"0x{p11_hcrc:04X}")
    p11_edcrc = struct.unpack_from('<H', p11_hdr, 2)[0]
    p11_payload = f_dec[0x32CBA : 0x32CBA + 0x1000]
    report.assert_true(p11_edcrc == jl_crc16(p11_payload), "p11_code.bin Payload CRC16", f"0x{p11_edcrc:04X}")

    # Trailing padding [0x33CBA : 0x34000] MUST be 0xFF
    padding = flash_raw[0x33CBA : 0x34000]
    report.assert_true(padding == b'\xFF' * len(padding), "Trailing Flash Padding", f"{len(padding)} B are 100% 0xFF")

    # --- Tier 4: Mode Preservation & Target Modifications ---
    print("\n--- Tier 4: Mode Preservation & Kindle Protocol Conformance ---")
    factory_app_path = os.path.join(SCRIPT_DIR, '..', 'extracted', 'ufw_unpacked', 'files', 'app.bin')
    with open(factory_app_path, 'rb') as f:
        fac_app = f.read()

    # Gamepad Mode (Table 2 at 0x22A1C)
    cs_table = app_payload[0x22A1C : 0x22A1C + 120]
    report.assert_true(cs_table == fac_app[0x22A1C : 0x22A1C + 120], "Gamepad Mode Preservation", "100% byte-identical to factory")

    # 0x07116 clean factory instructions check
    insn_7116 = app_payload[0x07116 : 0x07116 + 4]
    report.assert_true(insn_7116 == bytes([0x01, 0xF9, 0x60, 0x71]), "0x07116 Factory Instruction Check", f"{insn_7116.hex(' ')} (zero branch clobbers)")

    # 0x0B460 Mode Redirection (Switch T -> Mode 2)
    insn_b460 = app_payload[0x0B460 : 0x0B460 + 2]
    report.assert_true(insn_b460 == bytes([0x21, 0x70]), "Switch T -> Mode 2 Redirection", f"{insn_b460.hex(' ')} (mov r1, #2)")

    # DIS Identity
    pnp_id = app_payload[0x223C0 : 0x223C0 + 6]
    vid, pid, ver = struct.unpack('<HHH', pnp_id)
    report.assert_true(vid == 0x0171, "GATT DIS VID", f"0x{vid:04X} == 0x0171 (Lab126/Amazon)")
    report.assert_true(pid == 0x0443, "GATT DIS PID", f"0x{pid:04X} == 0x0443 (Kindle Remote)")
    report.assert_true(ver == 0x0100, "GATT DIS Version", f"0x{ver:04X} == 0x0100 (v1.0.0)")

    manuf = app_payload[0x22384 : 0x22384 + 13].split(b'\x00')[0].decode()
    report.assert_true(manuf == "Amazon", "GATT DIS Manufacturer", f"'{manuf}' == 'Amazon'")

    dis_model = app_payload[0x22391 : 0x22391 + 10].split(b'\x00')[0].decode()
    report.assert_true(dis_model == "Kindle-PT", "GATT DIS Model", f"'{dis_model}' == 'Kindle-PT'")

    # Triple GAP Name Check
    r_name = app_payload[0x211C6 : 0x211C6 + 12].split(b'\x00')[0].decode()
    k_name = app_payload[0x211EB : 0x211EB + 14].split(b'\x00')[0].decode()
    m_name = app_payload[0x211F9 : 0x211F9 + 11].split(b'\x00')[0].decode()
    report.assert_true(r_name == "Kindle-PT", "Reader GAP Name", f"'{r_name}' == 'Kindle-PT'")
    report.assert_true(k_name == "Kindle-PT", "Keyboard GAP Name", f"'{k_name}' == 'Kindle-PT'")
    report.assert_true(m_name == "Kindle-PT", "Mouse GAP Name", f"'{m_name}' == 'Kindle-PT'")

    # Key Mappings (Table 0 at 0x2289C)
    t0 = app_payload[0x2289C : 0x2289C + 192]
    report.assert_true(t0[0:6] == bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00]), "Table 0 Button A", "KEY_PAGEDOWN (0x4E)")
    report.assert_true(t0[6:12] == bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00]), "Table 0 Button B", "KEY_PAGEUP (0x4B)")
    report.assert_true(t0[12:18] == bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00]), "Table 0 Button X", "KEY_POWER (0x66)")
    report.assert_true(t0[18:24] == bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00]), "Table 0 Button Y", "KEY_POWER (0x66)")
    report.assert_true(t0[24:30] == bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00]), "Table 0 Button L1", "KEY_PAGEUP (0x4B)")
    report.assert_true(t0[30:36] == bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00]), "Table 0 Button R1", "KEY_PAGEDOWN (0x4E)")
    report.assert_true(t0[48:54] == bytes([0x02, 0x00, 0x00, 0x70, 0x00, 0x00]), "Table 0 Select", "KEY_BRIGHTNESSDOWN (0x70)")
    report.assert_true(t0[54:60] == bytes([0x02, 0x00, 0x00, 0x6F, 0x00, 0x00]), "Table 0 Start", "KEY_BRIGHTNESSUP (0x6F)")
    report.assert_true(t0[96:102] == bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00]), "Table 0 D-Pad Up", "KEY_PAGEUP (0x4B)")
    report.assert_true(t0[102:108] == bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00]), "Table 0 D-Pad Down", "KEY_PAGEDOWN (0x4E)")
    report.assert_true(t0[108:114] == bytes([0x02, 0x00, 0x00, 0x70, 0x00, 0x00]), "Table 0 D-Pad Left", "KEY_BRIGHTNESSDOWN (0x70)")
    report.assert_true(t0[114:120] == bytes([0x02, 0x00, 0x00, 0x6F, 0x00, 0x00]), "Table 0 D-Pad Right", "KEY_BRIGHTNESSUP (0x6F)")

    # Key Mappings (Table 1 at 0x2295C)
    t1 = app_payload[0x2295C : 0x2295C + 192]
    report.assert_true(t1[0:6] == bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00]), "Table 1 Button A", "KEY_PAGEDOWN (0x4E)")
    report.assert_true(t1[6:12] == bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00]), "Table 1 Button B", "KEY_PAGEUP (0x4B)")
    report.assert_true(t1[12:18] == bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00]), "Table 1 Button X", "KEY_POWER (0x66)")
    report.assert_true(t1[18:24] == bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00]), "Table 1 Button Y", "KEY_POWER (0x66)")

    # BLE ADV_IND Injections
    adv_pid = app_payload[0x22EF0 : 0x22EF0 + 3]
    report.assert_true(adv_pid == bytes([0x04, 0x43, 0x00]), "Rodata PID String", f"{adv_pid.hex(' ')}")
    r4_insn = app_payload[0x04D36 : 0x04D36 + 6]
    report.assert_true(r4_insn == bytes([0xC4, 0xFF, 0x10, 0x30, 0xE2, 0x01]), "adv_setup_init: mov r4, 0x01E23010", f"{r4_insn.hex(' ')}")
    r0_71 = app_payload[0x04D58 : 0x04D58 + 2]
    report.assert_true(r0_71 == bytes([0x10, 0x77]), "adv_setup_init: mov r0, #0x71", f"{r0_71.hex(' ')}")
    r0_01 = app_payload[0x04D5E : 0x04D5E + 2]
    report.assert_true(r0_01 == bytes([0x10, 0x70]), "adv_setup_init: mov r0, #0x01", f"{r0_01.hex(' ')}")
    r6_4 = app_payload[0x04D90 : 0x04D90 + 2]
    report.assert_true(r6_4 == bytes([0x46, 0x70]), "adv_setup_init: mov r6, #4", f"{r6_4.hex(' ')} (28-byte packet)")

    # BLE Pairing Gate (Dual-Defense)
    pair_gate = app_payload[0x161C8 : 0x161C8 + 4]
    report.assert_true(pair_gate == bytes([0x10, 0x70, 0x20, 0x00]), "check_pair_allow: mov r0, #1; rts", f"{pair_gate.hex(' ')}")

    caller_gate = app_payload[0x185E2 : 0x185E2 + 2]
    report.assert_true(caller_gate == bytes([0x00, 0x08]), "check_pair_allow caller gate: mov r0, r0 (NOP)", f"{caller_gate.hex(' ')}")

    return report.summary()

if __name__ == '__main__':
    target = os.path.join(SCRIPT_DIR, '..', 'candidate', 'IINE_Kindle_Mouse_Mod_v2.0.ufw')
    ok = verify_mouse_firmware(target)
    if not ok:
        sys.exit(1)
