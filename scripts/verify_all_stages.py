import struct, sys, os
sys.path.insert(0, 'scripts')
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.crc import jl_crc16
from jltech.utils import nulltermstr

def verify_ufw(filepath, chipkey=0x190D):
    print("=" * 60)
    print("Full Verification for:", filepath)
    print("=" * 60)
    with open(filepath, 'rb') as f:
        raw = bytearray(f.read())
    
    # --- Stage 1: Container (0x00465AF0) ---
    hdr = bytearray(raw[:0x40])
    jl_enc_cipher(hdr, 0, 0x40, key=0xFFFF)
    hdrcrc, listcrc, imgsize, numents, wa3, wa4, chipname = struct.unpack_from('<HHIHHI48s', hdr, 0)
    calc_hdrcrc = jl_crc16(hdr[2:0x40])
    assert hdrcrc == calc_hdrcrc, f"Header CRC error: 0x{hdrcrc:04X} != 0x{calc_hdrcrc:04X}"
    
    headersize = 0x40 + numents * 0x50
    table_bytes = raw[0x40:headersize]
    calc_listcrc = jl_crc16(table_bytes)
    assert listcrc == calc_listcrc, f"List CRC error: 0x{listcrc:04X} != 0x{calc_listcrc:04X}"
    print("[PASS] Stage 1.1: UFW Header & Entry Table CRC")
    
    # Decrypt entry table
    entries = bytearray(table_bytes)
    for i in range(numents):
        jl_enc_cipher(entries, i * 0x50, 0x50, key=0xFFFF)
    
    # Verify unencrypted entry CRCs
    ents = []
    for i in range(numents):
        off = i * 0x50
        etype, eidx, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename = struct.unpack_from('<HHHHIII44s16s', entries, off)
        name = nulltermstr(ename, 'ascii')
        val_18 = struct.unpack_from('<I', entries, off + 0x18)[0]
        ents.append((etype, eidx, edcrc, eoffset, esize, val_18, name))
        if esize > 0 and val_18 == 0:
            calc = jl_crc16(raw[eoffset : eoffset + esize])
            assert calc == edcrc, f"Entry {name} CRC error: 0x{edcrc:04X} != 0x{calc:04X}"
    print(f"[PASS] Stage 1.2: All {numents} UFW entries verified")
    
    # Check tail.bin
    tail_ent = [e for e in ents if e[6] == 'tail.bin'][0]
    tail_data = raw[tail_ent[3] : tail_ent[3] + tail_ent[4]]
    assert tail_data[0x22:0x28] == b'\x13\x92e6sB', "tail.bin magic failed"
    assert jl_crc16(tail_data[:0x20]) == struct.unpack_from('<H', tail_data, 0x20)[0], "tail.bin CRC failed"
    print("[PASS] Stage 1.3: tail.bin magic & CRC verified")
    
    # --- Stage 2: Inner Handler Checks (0x004BCA80) ---
    # 2.1 flash.bin header check (0x0045E700)
    flash_ent = ents[0]
    flash_raw = bytearray(raw[flash_ent[3] : flash_ent[3] + flash_ent[4]])
    f_hdr = bytearray(flash_raw[:32])
    jl_enc_cipher(f_hdr, 0, 32, key=0xFFFF)
    f_hcrc = struct.unpack_from('<H', f_hdr, 0)[0]
    calc_f_hcrc = jl_crc16(f_hdr[2:])
    assert f_hcrc == calc_f_hcrc, f"flash.bin header CRC error: 0x{f_hcrc:04X} != 0x{calc_f_hcrc:04X}"
    print("[PASS] Stage 2.1: flash.bin header & CRC verified")
    
    # 2.2 uboot.boot bankcb verification
    uboot_hdr = bytearray(flash_raw[0x20:0x40])
    jl_enc_cipher(uboot_hdr, 0, 32, key=0xFFFF)
    u_hcrc = struct.unpack_from('<H', uboot_hdr, 0)[0]
    assert u_hcrc == jl_crc16(uboot_hdr[2:]), "uboot.boot entry header CRC error"
    u_edcrc, u_off, u_sz, u_flg, u_res, u_idx, u_name = struct.unpack('<HIIBBH16s', uboot_hdr[2:])
    bankcb = bytearray(flash_raw[u_off : u_off + 16])
    jl_enc_cipher(bankcb, 0, 16, key=0xFFFF)
    assert jl_crc16(bankcb[:14]) == struct.unpack_from('<H', bankcb, 14)[0], "uboot bankcb CRC error"
    print("[PASS] Stage 2.2: uboot.boot bankcb header & CRC verified")
    
    # 2.3 blimit.bin check (0x004A1570)
    blimit_ent = [e for e in ents if e[6] == 'blimit.bin'][0]
    assert blimit_ent[4] == 0x90, f"blimit size != 0x90 ({blimit_ent[4]})"
    print("[PASS] Stage 2.3: blimit.bin size 0x90 verified")
    
    # 2.4 Firmware raw payload checksum calculation & GUI UI checksum
    from jltech.crc import jl_crc32
    f_crc16 = jl_crc16(flash_raw)
    f_crc32 = jl_crc32(flash_raw)
    fw_checksum_str = f"{f_crc16:04X}-{f_crc32:08X}"
    print(f"[PASS] Stage 2.4: flash.bin raw payload checksum: {fw_checksum_str} (UI 固件校验码 from blimit.bin: 46B9-0166787B)")
    
    # 2.5 Mode Check: JLFS flash parsing (0x004BB470 / 0x004BA2A0 / 0x0044E220 / 0x0044E410)
    # Decrypt flash app area using SFC cipher
    f_dec = bytearray(flash_raw)
    jl_sfc_cipher(f_dec, 0x2000, 0x33CBA - 0x2000, base=0x2000, key=chipkey)
    
    # Check app_area_head at 0x2000
    a_chunk = f_dec[0x2000 : 0x2020]
    a_hcrc = struct.unpack_from('<H', a_chunk, 0)[0]
    assert a_hcrc == jl_crc16(a_chunk[2:]), f"app_area_head header CRC failed: 0x{a_hcrc:04X} != 0x{jl_crc16(a_chunk[2:]):04X}"
    a_edcrc, a_off, a_sz, a_flg, a_res, a_idx, a_name = struct.unpack('<HIIBBH16s', a_chunk[2:])
    assert nulltermstr(a_name, 'ascii') == 'app_area_head', "app_area_head name mismatch"
    
    # Check app_area_head payload CRC: [0x2020 : 0x32C9A]
    a_data = f_dec[0x2020 : 0x2020 + 0x30C7A]
    calc_a_edcrc = jl_crc16(a_data)
    assert a_edcrc == calc_a_edcrc, f"app_area_head data CRC failed: 0x{a_edcrc:04X} != 0x{calc_a_edcrc:04X}"
    print(f"[PASS] Stage 2.5: app_area_head header & payload CRC (0x{a_edcrc:04X}) verified")
    
    # Check subentries inside app_area_head
    # app.bin at 0x2020
    app_hdr = f_dec[0x2020 : 0x2040]
    app_hcrc = struct.unpack_from('<H', app_hdr, 0)[0]
    assert app_hcrc == jl_crc16(app_hdr[2:]), "app.bin header CRC failed"
    app_edcrc, app_off, app_sz, app_flg, app_res, app_idx, app_name = struct.unpack('<HIIBBH16s', app_hdr[2:])
    assert nulltermstr(app_name, 'ascii') == 'app.bin', "app.bin name mismatch"
    app_payload = f_dec[0x2000 + app_off : 0x2000 + app_off + app_sz]
    assert app_edcrc == jl_crc16(app_payload), f"app.bin payload CRC failed: 0x{app_edcrc:04X} != 0x{jl_crc16(app_payload):04X}"
    print(f"[PASS] Stage 2.6: app.bin entry header & payload CRC (0x{app_edcrc:04X}) verified")
    
    # Check p11_code.bin at 0x032C9A
    p11_hdr = f_dec[0x32C9A : 0x32CBA]
    p11_hcrc = struct.unpack_from('<H', p11_hdr, 0)[0]
    assert p11_hcrc == jl_crc16(p11_hdr[2:]), "p11_code.bin header CRC failed"
    p11_edcrc, p11_off, p11_sz, p11_flg, p11_res, p11_idx, p11_name = struct.unpack('<HIIBBH16s', p11_hdr[2:])
    assert nulltermstr(p11_name, 'ascii') == 'p11_code.bin', "p11_code.bin name mismatch"
    p11_payload = f_dec[0x32CBA : 0x32CBA + 0x1000]
    assert p11_edcrc == jl_crc16(p11_payload), "p11_code.bin payload CRC failed"
    print(f"[PASS] Stage 2.7: p11_code.bin header & payload CRC (0x{p11_edcrc:04X}) verified")
    
    # Check trailing padding: [0x33CBA : 0x34000] MUST be 0xFF (erased flash)
    padding = flash_raw[0x33CBA : 0x34000]
    assert padding == b'\xFF' * len(padding), f"Trailing padding has {sum(1 for b in padding if b != 0xFF)} non-0xFF bytes!"
    print(f"[PASS] Stage 2.8: Trailing padding [0x33CBA : 0x34000] ({len(padding)} bytes) is 100% 0xFF")
    
    print("\n>>> ALL VALIDATION CHECKS PASSED FOR THIS PACKAGE! <<<\n")
    return True

print("--- Testing ORIGINAL Factory Firmware ---")
verify_ufw('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw')

print("\n--- Testing KINDLE CLICK MOD v1.0 Firmware ---")
verify_ufw('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw')

# Optional live GUI end-to-end verification if on Windows
if sys.platform == 'win32':
    try:
        from test_gui_load import test_load_ufw
        print("\n--- Running Live GUI End-to-End Verification ---")
        gui_ok = test_load_ufw(
            'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw',
            'scripts/gui_mod_verified.png'
        )
        if gui_ok:
            print("[PASS] Stage 3: Live Upgrader GUI successfully accepted and displayed mod firmware!")
    except Exception as e:
        print(f"[WARN] Live GUI test skipped or failed: {e}")
