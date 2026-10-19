import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

# Find all occurrences of jl_file_edit metaobject or vtable
# Earlier we found jl_file_edit string at 0x9CD7B0, VA: 0xDCEDB0
# Pointer to jl_file_edit string: ['0x9a904']
# Let's inspect around 0x9a904:
print("Around 0x9a904:")
print(data[0x9A8E0 : 0x9A960].hex(' '))

# Let's check the QMetaObject for jl_file_edit:
# In Qt5 QMetaObject struct:
# { superdata, stringdata, data, static_metacall, ... }
# Let's see what is at 0x9A8E0..0x9A960
for i in range(0x9A8E0, 0x9A960, 4):
    v = struct.unpack_from('<I', data, i)[0]
    print(f"  0x{off_to_va(i):08X}: 0x{v:08X} (va: {va_to_off(v) is not None})")
