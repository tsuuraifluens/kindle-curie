import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.cipher import jl_enc_cipher
from jltech.utils import nulltermstr

class JLFSEntry:
    def __init__(self, buff, off=0, database=0, data_after_header=False):
        hcrc, hdr = struct.unpack_from('<H30s', buff, off)
        edcrc, eoff, esize, eflags, eresvd, eindex, ename = struct.unpack('<HIIBBH16s', hdr)
        self.hdr_off  = off
        self.data_crc = edcrc
        self.offset   = eoff
        self.size     = esize
        self.flags    = eflags
        self.resvd    = eresvd
        self.index    = eindex
        self.raw_name = ename
        self.data_offset = (self.hdr_off + 32) if data_after_header else (self.offset + database)
        self.data_size = (self.hdr_off + self.size - self.data_offset) if data_after_header else self.size
        self.name = nulltermstr(ename, 'ascii')

    def __str__(self):
        return f'<JLFS Entry @0x{self.hdr_off:06X} - CRC:0x{self.data_crc:04X} data@0x{self.data_offset:06X} (sz:0x{self.data_size:06X}) - flags:0x{self.flags:02X} index:{self.index} name:"{self.name}">'

with open('extracted/ufw_unpacked/decrypted.bin', 'rb') as f:
    dec = f.read()

print("App Area Entries in decrypted.bin:")
# In decrypted.bin, app area begins at 0x2000
# The first entry is app_dir_head
off = 0x2000
while off < 0x34000:
    ent = JLFSEntry(dec, off, 0x2000, data_after_header=True)
    print(ent)
    if ent.name == 'app_dir_head':
        sub_off = ent.hdr_off + 32
        while sub_off < ent.data_offset: # wait, sub entries are inside app_dir_head
            break
    off += ent.size
    if ent.index != 0 or ent.size == 0:
        break
print(f"End of app entries at 0x{off:06X} (dec[0x{off:X}]: {dec[off:off+16].hex(' ')})")
