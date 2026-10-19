import struct

with open('extracted/updater_unpacked.exe', 'rb') as f:
    data = f.read()

# stringdata table starts at VA 0xDC7768 (file offset 0x9C6168)
# In Qt5 QByteArrayData is { int ref, int size, uint alloc, int offset }
# offset is relative to the start of the QByteArrayData struct itself!
stringdata_va = 0x00DC7768
stringdata_off = 0x927A00 + (stringdata_va - 0xD29000)

def get_string(idx):
    # Each QByteArrayData is 16 bytes on 32-bit:
    # int ref (-1), int size, uint alloc, int offset
    entry_off = stringdata_off + idx * 16
    ref, size, alloc, str_off = struct.unpack_from('<iiii', data, entry_off)
    # The actual string bytes are at: entry_va + str_off
    # Which in file offset is: entry_off + str_off
    str_bytes = data[entry_off + str_off : entry_off + str_off + size]
    return str_bytes.decode('latin1', errors='replace')

print("MainWindow String table:")
for i in range(40):
    try:
        s = get_string(i)
        print(f"  String {i}: {s}")
    except Exception as e:
        break

# Meta data array at 0x9C5D50
meta_off = 0x9C5D50
# Revision, className, classInfoCount, classInfoData, methodCount, methodData...
rev, cls_name, ci_cnt, ci_data, meth_cnt, meth_data = struct.unpack_from('<6I', data, meta_off)
print(f"rev={rev}, meth_cnt={meth_cnt}, meth_data_idx={meth_data}")

# Each method has 5 uints: name, argc, parameters, tag, flags
methods_off = meta_off + meth_data * 4
for m in range(meth_cnt):
    name_idx, argc, params, tag, flags = struct.unpack_from('<5I', data, methods_off + m * 20)
    name = get_string(name_idx)
    print(f"  Method {m}: name={name} (idx {name_idx}), argc={argc}, flags=0x{flags:X}")
