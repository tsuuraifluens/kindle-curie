import os, sys, struct, hashlib
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.crc import jl_crc16, jl_crc32
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.chipkeybin import chipkeybin_decode
from jltech.utils import nulltermstr, align_to

def inspect_jlfs(flash_bytes, label):
    print(f"\n==================== {label} ====================")
    fw = bytearray(flash_bytes)
    baseoff = 0
    headerkey = 0xFFFF
    
    # Check top entries
    print("--- Top Level Entries ---")
    entoff = baseoff + 32
    top_entries = []
    while True:
        hdr_raw = fw[entoff : entoff + 32]
        jl_enc_cipher(fw, entoff, 32, headerkey)
        hcrc, hdr = struct.unpack_from('<H30s', fw, entoff)
        calc_hcrc = jl_crc16(hdr)
        edcrc, eoff, esize, eflags, eresvd, eindex, ename = struct.unpack('<HIIBBH16s', hdr)
        name = nulltermstr(ename, 'ascii')
        top_entries.append({
            'name': name, 'hcrc': hcrc, 'calc_hcrc': calc_hcrc,
            'edcrc': edcrc, 'eoff': eoff, 'esize': esize,
            'eflags': eflags, 'eindex': eindex, 'entoff': entoff
        })
        print(f"  Top Entry '{name}': hcrc=0x{hcrc:04X} ({'OK' if hcrc==calc_hcrc else 'FAIL'}), edcrc=0x{edcrc:04X}, off=0x{eoff:06X}, size={esize}, flags=0x{eflags:02X}, idx={eindex}")
        if eindex != 0:
            break
        entoff += 32

    # Find isd_config.ini to get chipkey
    isd_ent = next(e for e in top_entries if e['name'] == 'isd_config.ini')
    ckdata, ckcrc = struct.unpack_from('<32sH', fw, isd_ent['eoff'])
    chipkey = chipkeybin_decode(ckdata)
    print(f"Chipkey from isd_config.ini: 0x{chipkey:04X}")

    # Check uboot.boot
    uboot_ent = next(e for e in top_entries if e['name'] == 'uboot.boot')
    print(f"uboot.boot: offset=0x{uboot_ent['eoff']:06X}, size={uboot_ent['esize']}, edcrc=0x{uboot_ent['edcrc']:04X}")

    # app_dir_head
    app_ent = next(e for e in top_entries if e['name'] == 'app_dir_head')
    appbase = app_ent['eoff']
    print(f"app_dir_head points to 0x{appbase:06X}")

    # Now let's examine the app area at appbase
    # app_area_head entry is at appbase
    # In flash, app area is encrypted with SFC stream cipher from appbase to end of p11_code.bin (0x33CBA)
    # Let's decrypt SFC area
    sfc_data = bytearray(flash_bytes) # fresh copy
    jl_sfc_cipher(sfc_data, appbase, 0x33CBA - appbase, appbase, chipkey)
    
    # app_area_head header
    a_hcrc, a_hdr = struct.unpack_from('<H30s', sfc_data, appbase)
    a_calc_hcrc = jl_crc16(a_hdr)
    a_edcrc, a_eoff, a_esize, a_eflags, a_eresvd, a_eindex, a_ename = struct.unpack('<HIIBBH16s', a_hdr)
    a_name = nulltermstr(a_ename, 'ascii')
    
    # Calculate app_area_head data CRC
    # The data payload for app_area_head is the inner directory entries and data
    # In build_kindle_click_firmware.py: area_data_size = 0x30C7A (from 0x2020 to 0x32C9A)
    # Let's check a_esize
    print(f"app_area_head: hcrc=0x{a_hcrc:04X} ({'OK' if a_hcrc==a_calc_hcrc else 'FAIL'}), edcrc=0x{a_edcrc:04X}, size={a_esize}, entry_point=0x{a_eoff:08X}")
    area_payload = sfc_data[appbase + 32 : appbase + a_esize]
    print(f"  app_area_head data CRC: 0x{a_edcrc:04X} (calc: 0x{jl_crc16(area_payload):04X}) -> {'OK' if a_edcrc==jl_crc16(area_payload) else 'FAIL'}")

    # Now inside app_area_head, let's list the files:
    cur_off = appbase + 32
    inner_entries = []
    while True:
        ih_crc, ih_hdr = struct.unpack_from('<H30s', sfc_data, cur_off)
        ih_calc = jl_crc16(ih_hdr)
        iedcrc, ieoff, iesize, ieflags, ieresvd, ieindex, iename = struct.unpack('<HIIBBH16s', ih_hdr)
        iname = nulltermstr(iename, 'ascii')
        inner_entries.append({
            'name': iname, 'hcrc': ih_crc, 'calc_hcrc': ih_calc,
            'edcrc': iedcrc, 'eoff': ieoff, 'esize': iesize,
            'flags': ieflags, 'index': ieindex, 'hdr_off': cur_off
        })
        print(f"    Inner Entry '{iname}': hcrc=0x{ih_crc:04X} ({'OK' if ih_crc==ih_calc else 'FAIL'}), edcrc=0x{iedcrc:04X}, off=0x{ieoff:06X}, size={iesize}, flags=0x{ieflags:02X}, idx={ieindex}")
        if ieindex != 0:
            break
        cur_off += 32

    # Next entries after app_area_head block:
    # Notice in JLFSIterator(fw, appbase, 0, key=chipkey, sfc=True), after app_area_head (which has size a_esize),
    # the next entry is at appbase + a_esize!
    next_ent_off = appbase + a_esize
    print(f"Next entry after app_area_head at 0x{next_ent_off:06X}:")
    p11_hcrc, p11_hdr = struct.unpack_from('<H30s', sfc_data, next_ent_off)
    p11_calc_hcrc = jl_crc16(p11_hdr)
    p11_edcrc, p11_eoff, p11_esize, p11_eflags, p11_eresvd, p11_eindex, p11_ename = struct.unpack('<HIIBBH16s', p11_hdr)
    p11_name = nulltermstr(p11_ename, 'ascii')
    print(f"  Entry '{p11_name}': hcrc=0x{p11_hcrc:04X} ({'OK' if p11_hcrc==p11_calc_hcrc else 'FAIL'}), edcrc=0x{p11_edcrc:04X}, off=0x{p11_eoff:06X}, size={p11_esize}, flags=0x{p11_eflags:02X}, idx={p11_eindex}")
    # p11_code.bin data
    p11_data = sfc_data[next_ent_off + 32 : next_ent_off + p11_esize]
    print(f"  '{p11_name}' data CRC: 0x{p11_edcrc:04X} (calc: 0x{jl_crc16(p11_data):04X}) -> {'OK' if p11_edcrc==jl_crc16(p11_data) else 'FAIL'}")

    return {
        'sfc_data': sfc_data,
        'chipkey': chipkey,
        'top_entries': top_entries,
        'inner_entries': inner_entries,
        'p11_entry': (p11_name, p11_hcrc, p11_edcrc, p11_data)
    }

orig_ufw = open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb').read()
mod_ufw = open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw', 'rb').read()

orig_info = inspect_jlfs(orig_ufw[0x400:0x400+212992], "ORIGINAL FLASH.BIN")
mod_info = inspect_jlfs(mod_ufw[0x400:0x400+212992], "MODIFIED FLASH.BIN")
