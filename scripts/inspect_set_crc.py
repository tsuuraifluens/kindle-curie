import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

va = 0x00493E3B
off = va_to_off(va)
print(f"Code at VA 0x{va:08X} (off 0x{off:X}):")
print(data[off : off + 100].hex(' '))
