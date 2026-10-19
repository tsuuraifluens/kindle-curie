import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004BCA80
end_va = 0x004BDA50
start_off = va_to_off(start_va)
end_off = va_to_off(end_va)

# Look for all jumps to error exit (around 0x004BDA2E or similar)
# Let's inspect 0x004BDA00 to 0x004BDA50 to see what the error exit is
off_err = va_to_off(0x004BDA20)
print("Code around error exit (0x004BDA20..0x004BDA50):")
print(data[off_err : off_err + 48].hex(' '))
