import os, re

files = [os.path.join("binaries", f) for f in os.listdir("binaries") if os.path.isfile(os.path.join("binaries", f))]

print("Searching for com.lab126.powerd properties and methods...")
for fpath in files:
    with open(fpath, "rb") as f:
        data = f.read()
    matches = re.findall(b'com\.lab126\.powerd[a-zA-Z0-9_\.]*', data)
    if matches:
        print(f"=== {fpath} ===")
        for m in set(matches):
            print(f"  {m.decode(errors='ignore')}")
            
    # Also search for "powerd" property names
    props = re.findall(b'powerd\.[a-zA-Z0-9_\.]*', data)
    if props:
        print(f"  powerd.*: {set(p.decode(errors='ignore') for p in props)}")
