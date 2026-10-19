import sys
sys.path.insert(0, 'scripts')
from inspect_calls_strings import scan_calls_and_strings

scan_calls_and_strings(0x05100, 0x05400)
