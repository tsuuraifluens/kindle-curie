with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base = 0x01E00120

print("Occurrences of 0x2A50 (50 2A):")
for i in range(len(app)-1):
    if app[i] == 0x50 and app[i+1] == 0x2A:
        print(f"Offset 0x{i:05X} (VADDR 0x{base+i:08X})")
