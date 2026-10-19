import serial
import time

ser = serial.Serial("COM3", 115200, timeout=1)
time.sleep(0.1)
ser.write(b"logs\n")
time.sleep(0.8)
out = ser.read_all().decode("utf-8", errors="ignore")
print(out)
ser.close()
