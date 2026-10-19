#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kindle BLE HID Page Turner Desktop & Web Remote Control Application
Supports:
1. Windows Desktop GUI (Tkinter) with dark theme, large touch buttons, keyboard shortcuts.
2. Global Window Hotkeys:
   - Next Page: Space / Right / PageDown / D / Down
   - Prev Page: Left / PageUp / A / Up
   - Brightness: '+' or '=' (Up), '-' or '_' (Down)
   - Screen Sleep: 'S' (Dual-Key Chord: PageUp 0x4B + PageDown 0x4E)
   - Power: 'P' (Usage 0x66)
3. Always-on-top floating mode for easy reading.
4. Real-time ESP32-S3 telemetry: connection status, peer Kindle MAC, battery, notify counters.
5. Built-in Local & Mobile Web Remote Server (http://localhost:8088).
"""

import sys
import os
import time
import socket
import threading
import queue
import json
from http.server import HTTPServer, BaseHTTPRequestHandler
import urllib.parse
import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext
import serial
import serial.tools.list_ports

DEFAULT_PORT = "COM3"
DEFAULT_BAUD = 115200
HTTP_PORT = 8088

# Global state shared between GUI, Serial worker, and Web server
class AppState:
    def __init__(self):
        self.connected_serial = False
        self.ble_connected = False
        self.peer_mac = "未连接"
        self.battery_level = 95
        self.total_tx_ok = 0
        self.last_action = "就绪"
        self.uptime_ms = 0
        self.cmd_queue = queue.Queue()
        self.log_queue = queue.Queue()
        self.serial_instance = None
        self.ser_lock = threading.Lock()
        self.local_ip = "127.0.0.1"

state = AppState()

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

state.local_ip = get_local_ip()

# ==========================================
# Serial Worker Thread
# ==========================================
class SerialWorker(threading.Thread):
    def __init__(self, port, baud, app_state):
        super().__init__(daemon=True)
        self.port = port
        self.baud = baud
        self.state = app_state
        self.running = True

    def run(self):
        self.state.log_queue.put(f"[SYS] 正在连接串口 {self.port} @ {self.baud} baud...")
        try:
            ser = serial.Serial()
            ser.port = self.port
            ser.baudrate = self.baud
            ser.timeout = 0.5
            ser.dtr = False
            ser.rts = False
            ser.open()
            self.state.serial_instance = ser
            self.state.connected_serial = True
            self.state.log_queue.put(f"[SYS] 串口 {self.port} 连接成功！")
        except Exception as e:
            self.state.connected_serial = False
            self.state.log_queue.put(f"[ERR] 无法打开串口 {self.port}: {e}")
            return

        # Query initial status
        ser.write(b"status\r\n")

        last_poll = time.time()

        while self.running and ser.is_open:
            # 1. Process pending commands
            while not self.state.cmd_queue.empty():
                try:
                    cmd = self.state.cmd_queue.get_nowait()
                    with self.state.ser_lock:
                        ser.write((cmd + "\r\n").encode("utf-8"))
                    self.state.log_queue.put(f"[CMD] 发送指令 -> {cmd}")
                except Exception as e:
                    self.state.log_queue.put(f"[ERR] 发送失败: {e}")

            # 2. Read incoming serial data
            try:
                line = ser.readline().decode("utf-8", errors="replace").strip()
                if line:
                    self.state.log_queue.put(line)
                    self._parse_line(line)
            except Exception:
                pass

            # 3. Periodic status polling (every 4 seconds)
            if time.time() - last_poll > 4.0:
                last_poll = time.time()
                try:
                    with self.state.ser_lock:
                        ser.write(b"status\r\n")
                except Exception:
                    pass

            time.sleep(0.02)

        try:
            ser.close()
        except Exception:
            pass
        self.state.connected_serial = False
        self.state.log_queue.put("[SYS] 串口已关闭。")

    def _parse_line(self, line):
        if "Connection State" in line and "CONNECTED" in line:
            self.state.ble_connected = True
        elif "Connection State" in line and "DISCONNECTED" in line:
            self.state.ble_connected = False
            self.state.peer_mac = "未连接"
        elif "Peer BLE Address" in line:
            parts = line.split(":")
            if len(parts) >= 2:
                self.state.peer_mac = line.split(":", 1)[1].split("(")[0].strip()
        elif "Battery Level (0x2A19):" in line:
            try:
                pct_str = line.split(":")[1].replace("%", "").strip()
                self.state.battery_level = int(pct_str)
            except Exception:
                pass
        elif "GATT Notify Frames TX" in line:
            try:
                ok_part = line.split(":")[1].split("OK")[0].strip()
                self.state.total_tx_ok = int(ok_part)
            except Exception:
                pass
        elif "Key PRESS sent: NEXT" in line:
            self.state.last_action = "下一页 (NEXT)"
        elif "Key PRESS sent: PREV" in line:
            self.state.last_action = "上一页 (PREV)"
        elif "BRIGHT_UP" in line:
            self.state.last_action = "调亮背光 (BRIGHT_UP)"
        elif "BRIGHT_DOWN" in line:
            self.state.last_action = "调暗背光 (BRIGHT_DOWN)"
        elif "SLEEP" in line or "sleep" in line:
            self.state.last_action = "息屏休眠 (SLEEP 0x66)"
        elif "POWER" in line or "power" in line:
            self.state.last_action = "电源键 (POWER 0x66)"

    def stop(self):
        self.running = False


# ==========================================
# Web Remote Control Server (Port 8088)
# ==========================================
HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Kindle 翻页与亮度遥控器 v1.0.0</title>
<style>
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    margin: 0; padding: 16px;
    background: #12121e; color: #e2e8f0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    min-height: 100vh; user-select: none;
  }
  .card {
    background: #1e1e2f; border-radius: 20px; padding: 22px;
    width: 100%; max-width: 440px; box-shadow: 0 10px 30px rgba(0,0,0,0.5);
    display: flex; flex-direction: column; gap: 16px; text-align: center;
  }
  h1 { font-size: 22px; margin: 0; color: #38bdf8; letter-spacing: 0.5px; }
  .badge {
    display: inline-block; padding: 6px 14px; border-radius: 20px;
    font-size: 13px; font-weight: 600;
  }
  .badge-online { background: #064e3b; color: #6ee7b7; border: 1px solid #059669; }
  .badge-offline { background: #451a03; color: #fcd34d; border: 1px solid #d97706; }
  .btn-container { display: flex; flex-direction: column; gap: 12px; }
  .btn-turn {
    background: linear-gradient(135deg, #2563eb, #1d4ed8);
    border: none; border-radius: 16px; color: white;
    font-size: 24px; font-weight: 700; padding: 24px 16px;
    cursor: pointer; box-shadow: 0 6px 16px rgba(37,99,235,0.4);
    transition: transform 0.08s, background 0.15s;
    display: flex; align-items: center; justify-content: center; gap: 10px;
  }
  .btn-turn:active { transform: scale(0.96); background: #1e40af; }
  .btn-prev { background: linear-gradient(135deg, #475569, #334155); box-shadow: 0 6px 16px rgba(51,65,85,0.4); }
  .btn-prev:active { background: #1e293b; }
  .btn-next { background: linear-gradient(135deg, #059669, #047857); box-shadow: 0 6px 16px rgba(5,150,105,0.4); }
  .btn-next:active { background: #065f46; }
  
  .quick-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 4px; }
  .btn-quick {
    padding: 14px 8px; border-radius: 12px; border: none; font-size: 14px; font-weight: 600;
    color: #f8fafc; cursor: pointer; transition: transform 0.08s;
    display: flex; align-items: center; justify-content: center; gap: 6px;
  }
  .btn-quick:active { transform: scale(0.95); }
  .btn-bright { background: linear-gradient(135deg, #64748b, #475569); box-shadow: 0 4px 10px rgba(0,0,0,0.3); }
  .btn-sleep { background: linear-gradient(135deg, #7c3aed, #6d28d9); box-shadow: 0 4px 10px rgba(124,58,237,0.4); }
  .btn-power { background: linear-gradient(135deg, #dc2626, #b91c1c); box-shadow: 0 4px 10px rgba(220,38,38,0.4); }

  .footer-stats {
    font-size: 12px; color: #94a3b8; display: flex; justify-content: space-around;
    padding-top: 10px; border-top: 1px solid #334155;
  }
  .stat-val { font-weight: bold; color: #f8fafc; font-size: 14px; }
  .tip { font-size: 11px; color: #64748b; line-height: 1.5; }
</style>
</head>
<body>
<div class="card">
  <h1>Kindle 翻页与亮度遥控器 <span style="font-size:14px;color:#94a3b8;">v1.0.0</span></h1>
  <div id="statusBadge" class="badge badge-offline">正在检测连接...</div>
  
  <div class="btn-container">
    <button class="btn-turn btn-prev" onclick="sendAction('prev')">
      <span>◀ 上一页</span>
    </button>
    <button class="btn-turn btn-next" onclick="sendAction('next')">
      <span>下一页 ▶</span>
    </button>
  </div>

  <div class="quick-row">
    <button class="btn-quick btn-bright" onclick="sendAction('bright_down')">
      <span>🔅 调暗前光</span>
    </button>
    <button class="btn-quick btn-bright" onclick="sendAction('bright_up')">
      <span>☀ 调亮前光</span>
    </button>
  </div>

  <div class="quick-row" style="grid-template-columns: 1fr;">
    <button class="btn-quick btn-sleep" onclick="sendAction('sleep')">
      <span>💤 息屏休眠 (Sleep 0x66)</span>
    </button>
  </div>

  <div class="footer-stats">
    <div><div>Kindle 设备</div><div class="stat-val" id="peerMac">-</div></div>
    <div><div>已发帧数</div><div class="stat-val" id="txCount">0</div></div>
    <div><div>模拟电量</div><div class="stat-val" id="batLevel">95%</div></div>
  </div>
  <div class="tip">
    快捷键：空格/→ 翻下页，← 翻上页，+/- 调亮度，S 息屏休眠（注：休眠后唤醒墨水屏需按机身物理按键或打开磁吸皮套）
  </div>
</div>

<script>
async function sendAction(action) {
  if (navigator.vibrate) navigator.vibrate(40);
  try {
    await fetch('/api/' + action, { method: 'POST' });
    updateStatus();
  } catch(e) { console.error(e); }
}

async function updateStatus() {
  try {
    const res = await fetch('/api/status');
    const data = await res.json();
    const badge = document.getElementById('statusBadge');
    if (data.ble_connected) {
      badge.className = 'badge badge-online';
      badge.textContent = '● 已连接 Kindle (' + data.peer_mac + ')';
    } else {
      badge.className = 'badge badge-offline';
      badge.textContent = '● 正在广播等待 Kindle 连接...';
    }
    document.getElementById('peerMac').textContent = data.peer_mac;
    document.getElementById('txCount').textContent = data.total_tx_ok;
    document.getElementById('batLevel').textContent = data.battery_level + '%';
  } catch(e) {}
}

document.addEventListener('keydown', (e) => {
  if (e.key === 'ArrowRight' || e.key === 'PageDown' || e.key === ' ' || e.key === 'd' || e.key === 'ArrowDown') {
    e.preventDefault(); sendAction('next');
  } else if (e.key === 'ArrowLeft' || e.key === 'PageUp' || e.key === 'a' || e.key === 'ArrowUp') {
    e.preventDefault(); sendAction('prev');
  } else if (e.key === '+' || e.key === '=') {
    e.preventDefault(); sendAction('bright_up');
  } else if (e.key === '-' || e.key === '_') {
    e.preventDefault(); sendAction('bright_down');
  } else if (e.key === 's' || e.key === 'S') {
    e.preventDefault(); sendAction('sleep');
  } else if (e.key === 'p' || e.key === 'P') {
    e.preventDefault(); sendAction('power');
  }
});

setInterval(updateStatus, 2000);
updateStatus();
</script>
</body>
</html>
"""

class RemoteHTTPHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # Suppress default noisy console access log

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/" or parsed.path == "/index.html":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(HTML_TEMPLATE.encode("utf-8"))
        elif parsed.path == "/api/status":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            data = {
                "serial_connected": state.connected_serial,
                "ble_connected": state.ble_connected,
                "peer_mac": state.peer_mac,
                "battery_level": state.battery_level,
                "total_tx_ok": state.total_tx_ok,
                "last_action": state.last_action
            }
            self.wfile.write(json.dumps(data).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/api/next":
            state.cmd_queue.put("next")
            self._send_json_ok()
        elif parsed.path == "/api/prev":
            state.cmd_queue.put("prev")
            self._send_json_ok()
        elif parsed.path == "/api/bright_up":
            state.cmd_queue.put("bright_up")
            self._send_json_ok()
        elif parsed.path == "/api/bright_down":
            state.cmd_queue.put("bright_down")
            self._send_json_ok()
        elif parsed.path == "/api/sleep":
            state.cmd_queue.put("sleep")
            self._send_json_ok()
        elif parsed.path == "/api/power":
            state.cmd_queue.put("power")
            self._send_json_ok()
        elif parsed.path.startswith("/api/battery"):
            qs = urllib.parse.parse_qs(parsed.query)
            lvl = qs.get("val", ["95"])[0]
            state.cmd_queue.put(f"battery {lvl}")
            self._send_json_ok()
        else:
            self.send_response(404)
            self.end_headers()

    def _send_json_ok(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(b'{"status":"ok"}')


def start_http_server():
    try:
        class ReusableHTTPServer(HTTPServer):
            allow_reuse_address = True
        server = ReusableHTTPServer(('0.0.0.0', HTTP_PORT), RemoteHTTPHandler)
        server.serve_forever()
    except Exception as e:
        state.log_queue.put(f"[WARN] Web 远程服务器启动失败: {e}")


# ==========================================
# Tkinter Desktop GUI
# ==========================================
class KindleRemoteGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Kindle BLE 翻页与亮度遥控器 (ESP32-S3 v1.0.0)")
        self.root.geometry("620x820")
        self.root.minsize(540, 720)
        self.root.configure(bg="#1E1E2E")

        # Serial worker handle
        self.worker = None

        self._apply_theme()
        self._build_ui()
        self._bind_shortcuts()

        # Start background Web server
        self.http_thread = threading.Thread(target=start_http_server, daemon=True)
        self.http_thread.start()

        # Auto-connect serial on startup
        self.connect_serial(DEFAULT_PORT)

        # Polling loop for UI refresh and logs
        self.root.after(100, self._ui_update_loop)

    def _apply_theme(self):
        self.colors = {
            "bg": "#1E1E2E",
            "card": "#252538",
            "card_border": "#313244",
            "text": "#CDD6F4",
            "subtext": "#A6ADC8",
            "accent_blue": "#89B4FA",
            "accent_green": "#A6E3A1",
            "accent_red": "#F38BA8",
            "accent_yellow": "#F9E2AF",
            "accent_purple": "#CBA6F7",
            "btn_prev": "#3E445B",
            "btn_prev_active": "#2E344A",
            "btn_next": "#1E66F5",
            "btn_next_active": "#184BB3",
            "btn_bright": "#45475A",
            "btn_sleep": "#7B2CBF",
            "btn_power": "#E63946"
        }

    def _build_ui(self):
        # 1. Top Header & Connection Bar
        top_frame = tk.Frame(self.root, bg=self.colors["card"], padx=16, pady=10, highlightthickness=1, highlightbackground=self.colors["card_border"])
        top_frame.pack(fill="x", padx=16, pady=(14, 6))

        title_lbl = tk.Label(top_frame, text="Kindle BLE 翻页遥控器", font=("Segoe UI", 15, "bold"), fg=self.colors["accent_blue"], bg=self.colors["card"])
        title_lbl.pack(side="left")

        self.always_on_top_var = tk.BooleanVar(value=True)
        self.root.attributes("-topmost", True)
        top_check = tk.Checkbutton(top_frame, text="窗口置顶", variable=self.always_on_top_var,
                                   command=self._toggle_topmost, bg=self.colors["card"], fg=self.colors["text"],
                                   selectcolor=self.colors["bg"], activebackground=self.colors["card"], activeforeground=self.colors["text"])
        top_check.pack(side="right", padx=(8, 0))

        # Port selection
        ports = [p.device for p in serial.tools.list_ports.comports()]
        if DEFAULT_PORT not in ports and ports:
            ports.insert(0, DEFAULT_PORT)
        elif not ports:
            ports = [DEFAULT_PORT]

        self.port_var = tk.StringVar(value=DEFAULT_PORT)
        self.port_combo = ttk.Combobox(top_frame, textvariable=self.port_var, values=ports, width=8, state="readonly")
        self.port_combo.pack(side="right", padx=6)

        self.btn_reconn = tk.Button(top_frame, text="重新连接", font=("Segoe UI", 9), bg=self.colors["card_border"], fg=self.colors["text"],
                                    relief="flat", padx=8, pady=2, command=self._reconnect_serial)
        self.btn_reconn.pack(side="right")

        # 2. Status Badge Card
        status_card = tk.Frame(self.root, bg=self.colors["card"], padx=16, pady=8, highlightthickness=1, highlightbackground=self.colors["card_border"])
        status_card.pack(fill="x", padx=16, pady=4)

        self.badge_lbl = tk.Label(status_card, text="● 检测状态中...", font=("Segoe UI", 11, "bold"), fg=self.colors["accent_yellow"], bg=self.colors["card"])
        self.badge_lbl.pack(side="left")

        self.peer_lbl = tk.Label(status_card, text="Kindle MAC: 未连接", font=("Segoe UI", 9), fg=self.colors["subtext"], bg=self.colors["card"])
        self.peer_lbl.pack(side="right")

        # 3. Main Remote Buttons Area (Next & Prev)
        btn_frame = tk.Frame(self.root, bg=self.colors["bg"], padx=8, pady=6)
        btn_frame.pack(fill="x", padx=16)

        # Huge NEXT Page Button (Primary)
        self.btn_next = tk.Button(
            btn_frame,
            text="下一页 ▶\n[ 空格 / → / PageDown / D / ↓ ]",
            font=("Segoe UI", 15, "bold"),
            bg=self.colors["btn_next"],
            fg="#FFFFFF",
            activebackground=self.colors["btn_next_active"],
            activeforeground="#FFFFFF",
            relief="flat",
            cursor="hand2",
            padx=20,
            pady=18,
            command=self.send_next
        )
        self.btn_next.pack(fill="x", pady=4)

        # Large PREV Page Button (Secondary)
        self.btn_prev = tk.Button(
            btn_frame,
            text="◀ 上一页\n[ ← / PageUp / A / ↑ ]",
            font=("Segoe UI", 12, "bold"),
            bg=self.colors["btn_prev"],
            fg=self.colors["text"],
            activebackground=self.colors["btn_prev_active"],
            activeforeground="#FFFFFF",
            relief="flat",
            cursor="hand2",
            padx=20,
            pady=12,
            command=self.send_prev
        )
        self.btn_prev.pack(fill="x", pady=4)

        # 4. Brightness & Screen Sleep Controls Card
        extra_frame = tk.Frame(self.root, bg=self.colors["card"], padx=14, pady=8, highlightthickness=1, highlightbackground=self.colors["card_border"])
        extra_frame.pack(fill="x", padx=16, pady=4)

        # Row 1: Brightness Controls
        row_b = tk.Frame(extra_frame, bg=self.colors["card"])
        row_b.pack(fill="x", pady=2)

        self.btn_bdown = tk.Button(
            row_b,
            text="🔅 调暗前光 [-]",
            font=("Segoe UI", 10, "bold"),
            bg=self.colors["btn_bright"],
            fg=self.colors["text"],
            relief="flat",
            padx=12,
            pady=8,
            command=self.send_bright_down
        )
        self.btn_bdown.pack(side="left", expand=True, fill="x", padx=(0, 4))

        self.btn_bup = tk.Button(
            row_b,
            text="☀ 调亮前光 [+]",
            font=("Segoe UI", 10, "bold"),
            bg=self.colors["btn_bright"],
            fg=self.colors["text"],
            relief="flat",
            padx=12,
            pady=8,
            command=self.send_bright_up
        )
        self.btn_bup.pack(side="right", expand=True, fill="x", padx=(4, 0))

        # Row 2: Screen Sleep Controls
        row_s = tk.Frame(extra_frame, bg=self.colors["card"])
        row_s.pack(fill="x", pady=(6, 2))

        self.btn_sleep = tk.Button(
            row_s,
            text="💤 息屏休眠 (0x66) [S]",
            font=("Segoe UI", 10, "bold"),
            bg=self.colors["btn_sleep"],
            fg="#FFFFFF",
            relief="flat",
            padx=12,
            pady=8,
            command=self.send_sleep
        )
        self.btn_sleep.pack(fill="x", expand=True)

        # 5. Telemetry Metrics Card
        metrics_frame = tk.Frame(self.root, bg=self.colors["card"], padx=14, pady=8, highlightthickness=1, highlightbackground=self.colors["card_border"])
        metrics_frame.pack(fill="x", padx=16, pady=4)

        self.metric_tx = tk.Label(metrics_frame, text="已发送报文: 0", font=("Segoe UI", 9, "bold"), fg=self.colors["accent_green"], bg=self.colors["card"])
        self.metric_tx.grid(row=0, column=0, sticky="w", padx=8, pady=2)

        self.metric_bat = tk.Label(metrics_frame, text="模拟电量: 95%", font=("Segoe UI", 9, "bold"), fg=self.colors["text"], bg=self.colors["card"])
        self.metric_bat.grid(row=0, column=1, sticky="w", padx=8, pady=2)

        # Battery preset buttons
        bat_btn_frame = tk.Frame(metrics_frame, bg=self.colors["card"])
        bat_btn_frame.grid(row=0, column=2, sticky="e", padx=8)
        tk.Label(bat_btn_frame, text="电量:", font=("Segoe UI", 9), fg=self.colors["subtext"], bg=self.colors["card"]).pack(side="left", padx=2)
        for pct in (100, 80, 50, 20):
            b = tk.Button(bat_btn_frame, text=f"{pct}%", font=("Segoe UI", 8), bg=self.colors["card_border"], fg=self.colors["text"],
                          relief="flat", padx=3, pady=1, command=lambda p=pct: self.set_battery(p))
            b.pack(side="left", padx=1)

        # 6. Mobile Remote Web Address Bar
        web_frame = tk.Frame(self.root, bg=self.colors["card"], padx=14, pady=6, highlightthickness=1, highlightbackground=self.colors["card_border"])
        web_frame.pack(fill="x", padx=16, pady=4)

        web_icon = tk.Label(web_frame, text="📱", font=("Segoe UI", 11), bg=self.colors["card"])
        web_icon.pack(side="left", padx=(0, 4))

        web_url = f"http://{state.local_ip}:{HTTP_PORT}"
        web_text = f"手机遥控: {web_url}  (同局域网浏览器)"
        self.web_lbl = tk.Label(web_frame, text=web_text, font=("Segoe UI", 9), fg=self.colors["accent_blue"], bg=self.colors["card"])
        self.web_lbl.pack(side="left")

        # 7. Serial Logs Terminal (collapsible/scrollable)
        log_header = tk.Frame(self.root, bg=self.colors["bg"])
        log_header.pack(fill="x", padx=18, pady=(4, 2))
        tk.Label(log_header, text="底层串口通信日志", font=("Segoe UI", 9, "bold"), fg=self.colors["subtext"], bg=self.colors["bg"]).pack(side="left")
        
        btn_clr = tk.Button(log_header, text="清空", font=("Segoe UI", 8), bg=self.colors["card"], fg=self.colors["subtext"],
                            relief="flat", padx=6, pady=0, command=self._clear_logs)
        btn_clr.pack(side="right")

        self.log_box = scrolledtext.ScrolledText(
            self.root,
            height=6,
            bg="#181825",
            fg="#A6ADC8",
            insertbackground="#CDD6F4",
            font=("Consolas", 9),
            relief="flat",
            highlightthickness=1,
            highlightbackground=self.colors["card_border"]
        )
        self.log_box.pack(fill="both", expand=True, padx=16, pady=(0, 12))

    def _bind_shortcuts(self):
        # Keyboard shortcuts
        for key in ("<Right>", "<Next>", "<space>", "<d>", "<D>", "<Down>"):
            self.root.bind(key, lambda e: self.send_next())
        for key in ("<Left>", "<Prior>", "<a>", "<A>", "<Up>"):
            self.root.bind(key, lambda e: self.send_prev())
        for key in ("<plus>", "<equal>", "<KP_Add>"):
            self.root.bind(key, lambda e: self.send_bright_up())
        for key in ("<minus>", "<underscore>", "<KP_Subtract>"):
            self.root.bind(key, lambda e: self.send_bright_down())
        for key in ("<s>", "<S>"):
            self.root.bind(key, lambda e: self.send_sleep())
        for key in ("<p>", "<P>"):
            self.root.bind(key, lambda e: self.send_power())

    def _toggle_topmost(self):
        self.root.attributes("-topmost", self.always_on_top_var.get())

    def send_next(self):
        self._flash_button(self.btn_next, self.colors["btn_next_active"], self.colors["btn_next"])
        state.cmd_queue.put("next")

    def send_prev(self):
        self._flash_button(self.btn_prev, self.colors["btn_prev_active"], self.colors["btn_prev"])
        state.cmd_queue.put("prev")

    def send_bright_up(self):
        self._flash_button(self.btn_bup, "#585B70", self.colors["btn_bright"])
        state.cmd_queue.put("bright_up")

    def send_bright_down(self):
        self._flash_button(self.btn_bdown, "#585B70", self.colors["btn_bright"])
        state.cmd_queue.put("bright_down")

    def send_sleep(self):
        self._flash_button(self.btn_sleep, "#9D4EDD", self.colors["btn_sleep"])
        state.cmd_queue.put("sleep")

    def send_power(self):
        self._flash_button(self.btn_sleep, "#9D4EDD", self.colors["btn_sleep"])
        state.cmd_queue.put("power")

    def set_battery(self, pct):
        state.cmd_queue.put(f"battery {pct}")

    def _flash_button(self, btn, active_color, normal_color):
        btn.configure(bg=active_color)
        self.root.after(100, lambda: btn.configure(bg=normal_color))

    def _clear_logs(self):
        self.log_box.delete("1.0", tk.END)

    def connect_serial(self, port):
        if self.worker and self.worker.is_alive():
            self.worker.stop()
            time.sleep(0.1)
        self.worker = SerialWorker(port, DEFAULT_BAUD, state)
        self.worker.start()

    def _reconnect_serial(self):
        target_port = self.port_var.get().strip()
        self.connect_serial(target_port)

    def _ui_update_loop(self):
        # Update connection badge
        if not state.connected_serial:
            self.badge_lbl.configure(text="● 串口未连接", fg=self.colors["accent_red"])
            self.peer_lbl.configure(text=f"端口: {self.port_var.get()} (离线)")
        elif state.ble_connected:
            self.badge_lbl.configure(text=f"● 已连接 Kindle 翻页中", fg=self.colors["accent_green"])
            self.peer_lbl.configure(text=f"Kindle: {state.peer_mac}")
        else:
            self.badge_lbl.configure(text="● 正在广播等待 Kindle 连接...", fg=self.colors["accent_yellow"])
            self.peer_lbl.configure(text="Kindle: 未连接")

        # Update telemetry
        self.metric_tx.configure(text=f"已发送报文: {state.total_tx_ok}")
        self.metric_bat.configure(text=f"模拟电量: {state.battery_level}%")

        # Flush log queue
        while not state.log_queue.empty():
            try:
                line = state.log_queue.get_nowait()
                self.log_box.insert(tk.END, line + "\n")
                self.log_box.see(tk.END)
            except Exception:
                break

        self.root.after(120, self._ui_update_loop)


def main():
    root = tk.Tk()
    app = KindleRemoteGUI(root)
    root.mainloop()

if __name__ == "__main__":
    main()
