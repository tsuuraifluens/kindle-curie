with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

keywords = [b'pair', b'bond', b'smp', b'encrypt', b'connect', b'disconnect', b'passkey', b'security', b'ltk', b'hogp', b'gap']
print("Debug strings matching keywords in app.bin:")

pos = 0
found = set()
for kw in keywords:
    p = 0
    while True:
        p = app.lower().find(kw, p)
        if p == -1: break
        # find string start and end
        start = p
        while start > 0 and app[start-1] >= 0x20 and app[start-1] <= 0x7E:
            start -= 1
        end = p
        while end < len(app) and app[end] >= 0x20 and app[end] <= 0x7E:
            end += 1
        s = app[start:end]
        if len(s) >= 4 and s not in found:
            found.add(s)
            print(f"0x{start:05X}: {repr(s.decode('latin1', errors='ignore'))}")
        p += 1
