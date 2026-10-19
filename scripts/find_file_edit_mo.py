import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

# Find pointer to jl_file_edit meta_data
# stringdata for jl_file_edit starts around 0x9CD7B0
# Let's find QMetaObject of jl_file_edit:
# Search for pointer to 0xDCEDB0
ptrs = []
target_va = 0xDCEDB0
for i in range(len(data) - 4):
    if data[i:i+4] == struct.pack('<I', target_va):
        ptrs.append((i, off_to_va(i)))

print(f"Pointers to 0x{target_va:08X}:")
for p_off, p_va in ptrs:
    print(f"  file 0x{p_off:X} (VA 0x{p_va:08X})")
    # If this is in .rdata or .data, dump 28 bytes
    if p_off >= 0x927A00:
        mo = struct.unpack_from('<7I', data, p_off - 4)
        print(f"    QMetaObject: super=0x{mo[0]:08X}, strdata=0x{mo[1]:08X}, data=0x{mo[2]:08X}, static_metacall=0x{mo[3]:08X}")
