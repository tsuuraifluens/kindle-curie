import struct, sys, os
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher
from jltech.crc import jl_crc16

def inspect(name, path):
    with open(path, 'rb') as f:
        data = bytearray(f.read())
    print('=== ' + name + ' ===')
    print('Path:', path)
    print('Size:', len(data))
    
    # decrypt header
    hdr = bytearray(data[:0x40])
    jl_enc_cipher(hdr, 0, 0x40, key=0xFFFF)
    hdrcrc, listcrc, imgsize, numents, wa3, wa4, chipname = struct.unpack_from('<HHIHHI48s', hdr, 0)
    calc_hdrcrc = jl_crc16(hdr[2:0x40])
    headersize = 0x40 + numents * 0x50
    calc_listcrc = jl_crc16(data[0x40:headersize])

    print(f'HdrCRC: 0x{hdrcrc:04X} (calc: 0x{calc_hdrcrc:04X}), ListCRC: 0x{listcrc:04X} (calc: 0x{calc_listcrc:04X}), ImgSize: 0x{imgsize:X}, NumEnts: {numents}, wa3: 0x{wa3:X}, wa4: 0x{wa4:X}')
    print(f'Chip: {chipname.split(b"\x00")[0]}')
    
    # check entries
    entry_blob = bytearray(data[0x40:headersize])
    for i in range(numents):
        off = i * 0x50
        jl_enc_cipher(entry_blob, off, 0x50, key=0xFFFF)
        etype, eidx, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename = struct.unpack_from('<HHHHIII44s16s', entry_blob, off)
        name_str = ename.split(b"\x00")[0].decode('latin1', errors='replace')
        
        # calculate actual crc of entry payload
        chunk = data[eoffset : eoffset + esize]
        calc_crc = jl_crc16(chunk) if esize > 0 else 0
        match = 'OK' if calc_crc == edcrc else f'MISMATCH (calc=0x{calc_crc:04X})'
        print(f'  [{i}] {name_str:<16} Type=0x{etype:04X} Idx={eidx} Off=0x{eoffset:06X} Size=0x{esize:06X} Size2=0x{esize2:06X} CRC=0x{edcrc:04X} ({match})')

inspect('ORIGINAL', 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw')
inspect('MODIFIED', 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw')
