import subprocess
import time
import win32gui
import win32process
import win32api
import os

p_full = os.path.abspath('extracted/L1161兼容阅星瞳/L1161 固件升级程序_1.4.40.exe')
short_p = win32api.GetShortPathName(p_full)

p = subprocess.Popen([short_p], cwd=os.path.dirname(short_p))
time.sleep(3)

# Find all windows that contain "固件" or "升级" or "L1161" or Qt
found = []
def enum_windows_cb(hwnd, extra):
    if win32gui.IsWindowVisible(hwnd):
        title = win32gui.GetWindowText(hwnd)
        if any(k in title for k in ['固件', '升级', 'L1161', 'JL', '杰理', '1.4']):
            _, pid = win32process.GetWindowThreadProcessId(hwnd)
            found.append((hwnd, title, pid))
    return True

win32gui.EnumWindows(enum_windows_cb, None)
print("Found windows:", found)

if found:
    import win32con
    # Close it gracefully
    win32gui.PostMessage(found[0][0], win32con.WM_CLOSE, 0, 0)
    time.sleep(1)

try:
    p.terminate()
except:
    pass
