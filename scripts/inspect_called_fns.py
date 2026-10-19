import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

def inspect_fn(va, name):
    off = va_to_off(va)
    print(f"\n--- Function {name} at VA 0x{va:08X} (off 0x{off:X}) ---")
    chunk = data[off:off+80]
    print(chunk.hex(' '))
    # Search for strings in the function
    for p in range(off, off+200):
        val = int.from_bytes(data[p:p+4], 'little')
        val_off = va_to_off(val)
        if val_off and 0 <= val_off < len(data) - 4:
            s = data[val_off:val_off+40].split(b'\x00')[0]
            if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                print(f"  Ref 0x{val:08X}: '{s.decode('latin1')}'")

inspect_fn(0x0042A4C0, "fn_0042A4C0")
inspect_fn(0x00438190, "fn_00438190")
inspect_fn(0x00441B10, "fn_00441B10")
inspect_fn(0x0042DC60, "fn_0042DC60")
