import subprocess, time, ctypes
from ctypes import wintypes

user32 = ctypes.windll.user32
gdi32 = ctypes.windll.gdi32

p = subprocess.Popen(['extracted/L1161兼容阅星瞳/L1161 固件升级程序_1.4.40.exe'])
time.sleep(1.5)

main_hwnd = None
def cb(hwnd, extra):
    global main_hwnd
    lp = ctypes.c_ulong()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(lp))
    if lp.value == p.pid and user32.IsWindowVisible(hwnd):
        length = user32.GetWindowTextLengthW(hwnd)
        buff = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buff, length + 1)
        if '1.4.40' in buff.value:
            main_hwnd = hwnd
    return True

user32.EnumWindows(ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_int, ctypes.c_int)(cb), 0)
print('Main HWND:', main_hwnd)

# Get window rect
rect = wintypes.RECT()
user32.GetWindowRect(main_hwnd, ctypes.byref(rect))
w = rect.right - rect.left
h = rect.bottom - rect.top
print(f'Window size: {w}x{h}')

# Capture window DC to bitmap
hdcScreen = user32.GetDC(0)
hdcMem = gdi32.CreateCompatibleDC(hdcScreen)
hbm = gdi32.CreateCompatibleBitmap(hdcScreen, w, h)
gdi32.SelectObject(hdcMem, hbm)

# PrintWindow
user32.PrintWindow(main_hwnd, hdcMem, 2) # PW_RENDERFULLCONTENT = 2

# Save bitmap using PIL if available, or write BMP
try:
    from PIL import Image
    import io
    # Extract BMP bits
    class BITMAPINFOHEADER(ctypes.Structure):
        _fields_ = [
            ('biSize', wintypes.DWORD),
            ('biWidth', wintypes.LONG),
            ('biHeight', wintypes.LONG),
            ('biPlanes', wintypes.WORD),
            ('biBitCount', wintypes.WORD),
            ('biCompression', wintypes.DWORD),
            ('biSizeImage', wintypes.DWORD),
            ('biXPelsPerMeter', wintypes.LONG),
            ('biYPelsPerMeter', wintypes.LONG),
            ('biClrUsed', wintypes.DWORD),
            ('biClrImportant', wintypes.DWORD),
        ]
    bih = BITMAPINFOHEADER()
    bih.biSize = ctypes.sizeof(BITMAPINFOHEADER)
    bih.biWidth = w
    bih.biHeight = -h
    bih.biPlanes = 1
    bih.biBitCount = 32
    bih.biCompression = 0
    buf = ctypes.create_string_buffer(w * h * 4)
    gdi32.GetDIBits(hdcMem, hbm, 0, h, buf, ctypes.byref(bih), 0)
    img = Image.frombuffer('RGBA', (w, h), buf, 'raw', 'BGRA', 0, 1)
    img.save('scripts/gui_capture_test.png')
    print('Saved to scripts/gui_capture_test.png')
except Exception as e:
    print('Failed to save with PIL:', e)

gdi32.DeleteObject(hbm)
gdi32.DeleteDC(hdcMem)
user32.ReleaseDC(0, hdcScreen)
p.terminate()
