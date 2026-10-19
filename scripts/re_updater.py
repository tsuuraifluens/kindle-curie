import re, struct

with open('extracted/updater_unpacked.exe', 'rb') as f:
    data = f.read()

# Let's inspect strings around 0x9c6608 and 0x9cd7bd
print("Around 0x9c6608:")
print(data[0x9c6580:0x9c6680])

print("Around 0x9cd7bd:")
print(data[0x9cd750:0x9cd850])

# Search for any format string with %
matches = re.findall(b'%[0-9]*[xXlh\-\.]*[0-9]*[xX]', data)
print("Hex format strings found:", set(matches[:30]))

# Search for -% or %- or - in format strings
matches2 = [m for m in re.findall(b'[\x20-\x7e]{3,}', data) if b'%' in m and (b'X' in m or b'x' in m)]
print("Format strings with % and X/x:", matches2[:30])
