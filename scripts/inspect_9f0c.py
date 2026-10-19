import sys
sys.path.insert(0, 'scripts')
import struct
from inspect_calls_strings import scan_calls_and_strings

scan_calls_and_strings(0x09E80, 0x0A000)
