import os, sys, time, ctypes, subprocess
from ctypes import wintypes
from PIL import Image

user32 = ctypes.windll.user32
gdi32 = ctypes.windll.gdi32

WM_SETTEXT = 0x000C
WM_COMMAND = 0x0111
WM_LBUTTONDOWN = 0x0201
WM_LBUTTONUP = 0x0202
BM_CLICK = 0x00F5

WNDENUMPROC = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)

def test_load_ufw(ufw_path, output_png):
    abs_ufw_path = os.path.abspath(ufw_path)
    print(f"Testing UFW: {abs_ufw_path}")

    subprocess.call(['taskkill', '/f', '/im', 'L1161*'], stderr=subprocess.DEVNULL, stdout=subprocess.DEVNULL)
    time.sleep(0.5)

    # Launch GUI
    exe_path = os.path.abspath('extracted/L1161兼容阅星瞳/L1161 固件升级程序_1.4.40.exe')
    p = subprocess.Popen([exe_path], cwd=os.path.dirname(exe_path))
    target_hwnd = None
    for _ in range(20):
        time.sleep(0.5)
        def cb(hwnd, lparam):
            nonlocal target_hwnd
            if user32.IsWindowVisible(hwnd):
                cls_buff = ctypes.create_unicode_buffer(256)
                user32.GetClassNameW(hwnd, cls_buff, 256)
                if cls_buff.value == 'Qt5QWindowIcon':
                    target_hwnd = hwnd
            return True
        user32.EnumWindows(WNDENUMPROC(cb), 0)
        if target_hwnd:
            break
    print(f"Target HWND: {target_hwnd}")
    if not target_hwnd:
        p.terminate()
        return False

    # Click Open button at (550, 50)
    lParam = (50 << 16) | 550
    user32.PostMessageW(target_hwnd, WM_LBUTTONDOWN, 1, lParam)
    user32.PostMessageW(target_hwnd, WM_LBUTTONUP, 0, lParam)

    # Find file dialog
    dialog_hwnd = None
    for _ in range(20):
        time.sleep(0.5)
        def cb_dlg(hwnd, lparam):
            nonlocal dialog_hwnd
            if user32.IsWindowVisible(hwnd):
                cls_buff = ctypes.create_unicode_buffer(256)
                user32.GetClassNameW(hwnd, cls_buff, 256)
                if cls_buff.value == '#32770':
                    dialog_hwnd = hwnd
            return True
        user32.EnumWindows(WNDENUMPROC(cb_dlg), 0)
        if dialog_hwnd:
            break
    print(f"Dialog HWND: {dialog_hwnd}")
    if not dialog_hwnd:
        subprocess.call(['taskkill', '/f', '/im', 'L1161*'])
        return False

    # Find edit control (ID 1148 / 0x47C) and Open button (ID 1)
    edit_hwnd = None
    btn_hwnd = None
    def cb_child(hwnd, lparam):
        nonlocal edit_hwnd, btn_hwnd
        cid = user32.GetDlgCtrlID(hwnd)
        cls_buff = ctypes.create_unicode_buffer(256)
        user32.GetClassNameW(hwnd, cls_buff, 256)
        if cid == 0x47C and cls_buff.value == 'Edit':
            edit_hwnd = hwnd
        elif cid == 1 and cls_buff.value == 'Button':
            btn_hwnd = hwnd
        return True
    user32.EnumChildWindows(dialog_hwnd, WNDENUMPROC(cb_child), 0)
    print(f"Edit HWND: {edit_hwnd}, Btn HWND: {btn_hwnd}")

    # Set text in Edit control
    user32.SendMessageW(edit_hwnd, WM_SETTEXT, 0, abs_ufw_path)
    time.sleep(0.5)

    # Click Open button
    user32.SendMessageW(btn_hwnd, BM_CLICK, 0, 0)
    time.sleep(1.5)

    # Capture main window
    rect = wintypes.RECT()
    user32.GetWindowRect(target_hwnd, ctypes.byref(rect))
    w = rect.right - rect.left
    h = rect.bottom - rect.top

    hdcScreen = user32.GetDC(0)
    hdcMem = gdi32.CreateCompatibleDC(hdcScreen)
    hbm = gdi32.CreateCompatibleBitmap(hdcScreen, w, h)
    gdi32.SelectObject(hdcMem, hbm)

    user32.PrintWindow(target_hwnd, hdcMem, 2)

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
    img.save(output_png)
    print(f"Captured screenshot to: {output_png}")

    gdi32.DeleteObject(hbm)
    gdi32.DeleteDC(hdcMem)
    user32.ReleaseDC(0, hdcScreen)

    subprocess.call(['taskkill', '/f', '/im', 'L1161*'])
    return True

if __name__ == '__main__':
    test_load_ufw(sys.argv[1], sys.argv[2])
