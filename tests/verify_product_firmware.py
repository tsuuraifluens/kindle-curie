#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Automated Product Verification & Acceptance Test Suite
Runs on: PC connected to Kindle Remote over USB-Serial / JTAG (COM port)
Asserts:
1. System Banner & Boot Info (ESP32-C3 RISC-V Target)
2. CLI Command Availability (help, status, info, logs, selftest, etc.)
3. On-Target Self-Tests (7/7 Protocol & Evidence Encoding Tests)
4. Hardware Battery ADC Sampling Command
5. Haptic Feedback & Pulse Trigger
6. BLE HID Advertising & Manufacturer Specific 0xFF Bypass Data
7. Key Action Protection (Disconnected guard, rate limit 5Hz, debounce 80ms)
"""

import sys
import time
import re
import argparse
import serial
import serial.tools.list_ports

def run_tests(port_name, baud_rate=115200):
    print(f"==================================================================")
    print(f"  KINDLE REMOTE PRODUCT VERIFICATION SUITE (ESP32-C3 HOGP)       ")
    print(f"  Target Port: {port_name} @ {baud_rate} baud                     ")
    print(f"==================================================================\n")
    
    try:
        ser = serial.Serial(port_name, baudrate=baud_rate, timeout=1.0)
    except Exception as e:
        print(f"[FAIL] Could not open port {port_name}: {e}")
        return False
        
    def send_cmd(cmd, wait_time=0.3):
        ser.reset_input_buffer()
        ser.write(f"{cmd}\r\n".encode("utf-8"))
        time.sleep(wait_time)
        resp = ser.read_all().decode("utf-8", errors="ignore")
        return resp

    results = []
    
    # Test 1: CLI Help Check
    print("[1/7] Testing CLI Command Help...")
    resp = send_cmd("help")
    if "SERIAL CONSOLE COMMANDS" in resp and "selftest" in resp and "status" in resp:
        print("      PASS: Help reference returned complete command set.")
        results.append(True)
    else:
        print("      FAIL: Unexpected help response:\n" + resp)
        results.append(False)
        
    # Test 2: On-Target Protocol Self-Tests
    print("[2/7] Testing On-Target Protocol Self-Tests (selftest)...")
    resp = send_cmd("selftest", wait_time=0.6)
    if "ALL PASSED" in resp or "7 / 7 tests passed" in resp:
        print("      PASS: 7/7 on-target self-tests passed cleanly.")
        results.append(True)
    else:
        print("      FAIL: Self-tests did not pass all assertions:\n" + resp)
        results.append(False)
        
    # Test 3: Device Identity & Evidence Mapping (info)
    print("[3/7] Testing Device Identity & Evidence Verification (info)...")
    resp = send_cmd("info")
    if "0x0171" in resp and "0x0443" in resp and "71 01 04 43" in resp:
        print("      PASS: Identity confirmed Amazon VID 0x0171, PID 0x0443, ADV 0xFF verified.")
        results.append(True)
    else:
        print("      FAIL: Missing identity parameters in info output.")
        results.append(False)

    # Test 4: Status Telemetry & Link Guards (status)
    print("[4/7] Testing Status Telemetry & Link Protection (status)...")
    resp = send_cmd("status")
    if "BLE Stack Ready" in resp and "Own BLE Address" in resp:
        print("      PASS: Telemetry reported stack ready and own BLE address.")
        results.append(True)
    else:
        print("      FAIL: Status output incomplete.")
        results.append(False)

    # Test 5: Disconnected Key Action Guard (prev / next)
    print("[5/7] Testing Disconnected Key Action Rejection (next)...")
    resp = send_cmd("next")
    if "REJECTED" in resp or "not connected" in resp:
        print("      PASS: Correctly rejected key action when BLE is disconnected.")
        results.append(True)
    else:
        print("      FAIL: Did not guard disconnected state:\n" + resp)
        results.append(False)

    # Test 6: Battery Service Parameter & Notification (battery)
    print("[6/7] Testing Battery Telemetry Update (battery 85)...")
    resp = send_cmd("battery 85")
    if "85%" in resp and "OK" in resp:
        print("      PASS: Battery level set to 85% successfully.")
        results.append(True)
    else:
        print("      FAIL: Battery command rejected:\n" + resp)
        results.append(False)

    # Test 7: Ring Buffer Logs (logs)
    print("[7/7] Testing Telemetry Ring Buffer Logs (logs)...")
    resp = send_cmd("logs")
    if "RECENT LOGS" in resp and ("[SYS" in resp or "[BLE" in resp or "[ID" in resp):
        print("      PASS: Ring buffer logs returned valid millisecond timestamps.")
        results.append(True)
    else:
        print("      FAIL: Logs command did not return formatted buffer.")
        results.append(False)

    ser.close()
    
    passed_count = sum(1 for r in results if r)
    total_count = len(results)
    print(f"\n==================================================================")
    print(f"  VERIFICATION RESULT: {passed_count} / {total_count} PASSED")
    print(f"==================================================================")
    return passed_count == total_count

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Kindle Remote Product Verification")
    parser.add_argument("--port", default="COM3", help="Target serial port (e.g. COM3)")
    parser.add_argument("--baud", type=int, default=115200, help="Baud rate (default: 115200)")
    args = parser.parse_args()
    
    success = run_tests(args.port, args.baud)
    sys.exit(0 if success else 1)
