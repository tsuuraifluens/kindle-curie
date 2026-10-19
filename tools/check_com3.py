import serial
import time

try:
    ser = serial.Serial("COM3", 115200, timeout=1)
    time.sleep(0.1)
    ser.write(b"status\n")
    time.sleep(0.5)
    out = ser.read_all().decode("utf-8", errors="ignore")
    print("--- ESP32 COM3 Response ---")
    print(out)
    ser.close()
except Exception as e:
    print(f"Serial error: {e}")
