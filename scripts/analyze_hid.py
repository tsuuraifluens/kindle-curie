import sys

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    data = f.read()

print(f'app.bin size: {len(data)} bytes')

# Dump descriptor area
start = 0x22400
end = 0x22900
for i in range(start, end, 32):
    row = data[i:i+32]
    print(f'0x{i:05X}: {row.hex(" ")}')
