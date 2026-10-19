import re

def find_all_chinese_strings():
    with open('extracted/updater_unpacked.exe', 'rb') as f:
        data = f.read()

    print("=== Scanning all Chinese strings in updater_unpacked.exe ===")
    
    # 1. GBK strings
    print("\n--- GBK strings (length >= 2 Chinese chars) ---")
    gbk_pat = re.compile(rb'(?:[\x81-\xfe][\x40-\xfe]){2,}')
    found_gbk = set()
    for m in gbk_pat.finditer(data):
        raw = m.group()
        try:
            s = raw.decode('gbk')
            if s not in found_gbk and len(s) >= 2:
                found_gbk.add(s)
                if any(k in s for k in ['擦除', '整个', 'FLASH', '复位', '在线', '升级', '设备', '烧录', '成功', '固件', '配置']):
                    print(f"0x{m.start():06X} (GBK): {s}")
        except:
            pass

    # 2. UTF-16LE strings
    print("\n--- UTF-16LE strings ---")
    utf16_pat = re.compile(b'(?:[\x00-\xff]\x00){4,}')
    found_u16 = set()
    for m in utf16_pat.finditer(data):
        raw = m.group()
        try:
            s = raw.decode('utf-16le', errors='ignore').strip()
            if s not in found_u16 and len(s) >= 2:
                if any(k in s for k in ['擦除', '整个', 'FLASH', '复位', '在线', '升级', '设备', '烧录', '成功', '固件', '配置']):
                    found_u16.add(s)
                    print(f"0x{m.start():06X} (UTF-16LE): {s}")
        except:
            pass

    # 3. UTF-8 strings
    print("\n--- UTF-8 strings ---")
    utf8_pat = re.compile(rb'(?:[\xe4-\xe9][\x80-\xbf]{2}){2,}')
    found_u8 = set()
    for m in utf8_pat.finditer(data):
        raw = m.group()
        try:
            s = raw.decode('utf-8')
            if s not in found_u8:
                found_u8.add(s)
                if any(k in s for k in ['擦除', '整个', 'FLASH', '复位', '在线', '升级', '设备', '烧录', '成功', '固件', '配置']):
                    print(f"0x{m.start():06X} (UTF-8): {s}")
        except:
            pass

if __name__ == '__main__':
    find_all_chinese_strings()
