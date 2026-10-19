import re, struct

def inspect_updater():
    with open('extracted/updater_unpacked.exe', 'rb') as f:
        data = f.read()

    print(f"=== String Analysis of Unpacked Updater ({len(data)} bytes) ===")

    # Search for Chinese strings related to erase
    search_terms = ['擦除', '整个', 'FLASH', '复位', 'ONLINE', '升级', '失败', '成功', 'uboot', 'app.bin', 'key_mac', 'isd_config']
    for term in search_terms:
        # Check UTF-8, GBK, UTF-16LE
        for enc in ['gbk', 'utf-16le', 'ascii']:
            try:
                b = term.encode(enc)
                matches = [m.start() for m in re.finditer(re.escape(b), data)]
                if matches:
                    print(f"Term '{term}' ({enc}): {len(matches)} occurrences at {[hex(m) for m in matches[:5]]}")
            except:
                pass

if __name__ == '__main__':
    inspect_updater()
