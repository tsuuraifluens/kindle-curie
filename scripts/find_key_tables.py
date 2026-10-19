import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

# Let's search for Consumer Control VolumeUp (0x00E9) and VolumeDown (0x00EA)
# Or AC Back (0x0224)
# Or PageUp (0x4B) and PageDown (0x4E)

print('Searching for Consumer Usages: 0x00E9, 0x00EA, 0x0224...')
for m in range(len(app_data)-4):
    w1, w2 = struct.unpack_from('<HH', app_data, m)
    if (w1 == 0x00E9 and w2 == 0x00EA) or (w1 == 0x00EA and w2 == 0x00E9):
        print(f'Found E9, EA pair at 0x{m:05X}')
    if (w1 == 0x0224 or w2 == 0x0224) and (w1 in [0x00E9, 0x00EA] or w2 in [0x00E9, 0x00EA]):
        print(f'Found Consumer with 0x0224 at 0x{m:05X}')

print('\nSearching for Keyboard Usages: 0x4B (PageUp) and 0x4E (PageDown)...')
for m in range(len(app_data)-2):
    b1, b2 = app_data[m], app_data[m+1]
    if (b1 == 0x4B and b2 == 0x4E) or (b1 == 0x4E and b2 == 0x4B):
        print(f'Found byte pair 4B, 4E at 0x{m:05X}')

print('\nSearching for Arrow keys: 0x50 (Left), 0x4F (Right), 0x52 (Up), 0x51 (Down)...')
for m in range(len(app_data)-4):
    chunk = app_data[m:m+4]
    if set(chunk) == {0x4F, 0x50, 0x51, 0x52}:
        print(f'Found Arrow key quartet at 0x{m:05X}')
