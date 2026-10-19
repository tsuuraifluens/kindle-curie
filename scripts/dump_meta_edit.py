import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start = va_to_off(0x0049B550)
print(data[start : start + 300].hex(' '))
