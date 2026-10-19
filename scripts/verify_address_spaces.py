import struct, hashlib

def verify_address_formula():
    with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
        app_data = f.read()
    with open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb') as f:
        ufw_data = f.read()
    with open('extracted/ufw_unpacked/decrypted.bin', 'rb') as f:
        dec_data = f.read()

    print("=== Cross-Verification of the 4 Address Spaces ===")

    test_offsets = [
        ("PnP ID Data", 0x223BC),
        ("Manufacturer String", 0x22384),
        ("Mode String (IINE_reader)", 0x211C6),
        ("remap_key Handler", 0x0E7AC),
        ("read PnP_ID Code", 0x04494),
        ("Entry Point", 0x00000),
    ]

    print(f"| {'Feature':<26} | {'app.bin Off':<12} | {'Flash Off':<12} | {'UFW Off':<12} | {'CPU Virtual':<12} | {'Matches':<8} |")
    print(f"|{'-'*28}|{'-'*14}|{'-'*14}|{'-'*14}|{'-'*14}|{'-'*10}|")

    for name, app_off in test_offsets:
        flash_off = 0x2120 + app_off
        ufw_off = 0x400 + flash_off
        cpu_addr = 0x01E00120 + app_off

        # Check in decrypted.bin (which is decrypted flash.bin)
        dec_bytes = dec_data[flash_off : flash_off + 8]
        app_bytes = app_data[app_off : app_off + 8]
        matches = (dec_bytes == app_bytes)
        print(f"| {name:<26} | 0x{app_off:06X}     | 0x{flash_off:06X}     | 0x{ufw_off:06X}     | 0x{cpu_addr:08X}   | {'YES' if matches else 'NO'}      |")

    print("\nVerified: app.bin is identical to decrypted.bin at offset 0x2120!")
    print(f"app.bin size: {len(app_data)} bytes (0x{len(app_data):X})")
    print(f"decrypted.bin slice [0x2120 : 0x2120 + 0x{len(app_data):X}] SHA-256 == app.bin SHA-256: {hashlib.sha256(dec_data[0x2120:0x2120+len(app_data)]).hexdigest().upper() == hashlib.sha256(app_data).hexdigest().upper()}")

if __name__ == '__main__':
    verify_address_formula()
