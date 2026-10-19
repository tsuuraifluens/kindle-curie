with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app_orig = f.read()

print("orig app.bin size:", len(app_orig))
for s in [b'CPU-', b'V1.0.0', b'CPU-@', b'V1.0.0-']:
    idx = app_orig.find(s)
    print(f"  {s}: index = {idx}")
    if idx != -1:
        print(f"    context: {app_orig[idx:idx+64]}")
