import sys, re
sys.path.insert(0, 'scripts')
from re_meta import data

patterns = [
    b'JL_PACKAGE',
    b'jl_format',
    b'jl_fw',
    b'jl_key',
    b'cd03',
    b'tail.bin',
    b'script.ver',
    b'blimit.bin',
    b'ota.bin',
    b'flash2.bin',
]

for p in patterns:
    matches = [m.start() for m in re.finditer(re.escape(p), data)]
    print(f"Pattern {p.decode()}: {len(matches)} occurrences")
    for m in matches[:10]:
        s = data[m:m+60].split(b'\x00')[0]
        print(f"  0x{m:X}: {s.decode('latin1', errors='replace')}")
