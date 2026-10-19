with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

print("Bytes from 0x22340 to 0x22480:")
chunk = app[0x22340:0x22480]
for i in range(0, len(chunk), 16):
    print(f'{0x22340+i:05X}: ' + ' '.join(f'{b:02X}' for b in chunk[i:i+16]))
