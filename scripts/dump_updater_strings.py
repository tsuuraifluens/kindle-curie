import re

def dump_string_context():
    with open('extracted/updater_unpacked.exe', 'rb') as f:
        data = f.read()

    areas = [
        ("UI Dialog Strings (0x943700 - 0x943B00)", 0x943700, 0x943B00),
        ("Uboot & Download Engine (0x9C9C00 - 0x9CB500)", 0x9C9C00, 0x9CB500),
        ("Online Strings (0x929C00 - 0x92A000)", 0x929C00, 0x92A000),
        ("Online Strings (0x96F000 - 0x96F200)", 0x96F000, 0x96F200),
    ]

    for title, start, end in areas:
        print("="*80)
        print(title)
        print("="*80)
        chunk = data[start:end]
        for m in re.finditer(b'[\x20-\x7e\x80-\xff]{4,}', chunk):
            s_raw = m.group()
            try:
                s_gbk = s_raw.decode('gbk')
                print(f"0x{start + m.start():06X}: {s_gbk}")
            except:
                try:
                    s_ascii = s_raw.decode('latin1')
                    print(f"0x{start + m.start():06X} (latin1): {s_ascii}")
                except:
                    pass

if __name__ == '__main__':
    dump_string_context()
