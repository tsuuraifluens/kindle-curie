import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

stringdata_va = 0x00DCEBB0
stringdata_off = va_to_off(stringdata_va)

def get_str(idx):
    entry_off = stringdata_off + idx * 16
    ref, size, alloc, str_off = struct.unpack_from('<iiii', data, entry_off)
    return data[entry_off + str_off : entry_off + str_off + size].decode('latin1', errors='replace')

print("Class name:", get_str(0))
meta_off = va_to_off(0x00DCEF68)
rev, cls_name, ci_cnt, ci_data, meth_cnt, meth_data = struct.unpack_from('<6I', data, meta_off)
print(f"rev={rev}, meth_cnt={meth_cnt}, meth_data_idx={meth_data}")

methods_off = meta_off + meth_data * 4
for m in range(meth_cnt):
    name_idx, argc, params, tag, flags = struct.unpack_from('<5I', data, methods_off + m * 20)
    name = get_str(name_idx)
    print(f"  Method {m}: name={name} (idx {name_idx}), argc={argc}, flags=0x{flags:X}")
