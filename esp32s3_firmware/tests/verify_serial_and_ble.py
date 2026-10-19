import sys
import time
import re
import serial

SERIAL_PORT = "COM3"
BAUD_RATE   = 115200
TIMEOUT_SEC = 3.0

def send_cmd(ser, cmd, wait_sec=0.4):
    ser.reset_input_buffer()
    ser.write((cmd + "\r\n").encode("utf-8"))
    time.sleep(wait_sec)
    resp = ser.read_all().decode("utf-8", errors="replace")
    return resp

def main():
    print(f"[*] Opening serial port {SERIAL_PORT} @ {BAUD_RATE} baud...")
    try:
        ser = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=TIMEOUT_SEC)
    except Exception as e:
        print(f"[!] Failed to open {SERIAL_PORT}: {e}")
        sys.exit(1)

    time.sleep(2.0)
    boot_out = ser.read_all().decode("utf-8", errors="replace")
    if boot_out.strip():
        print("\n--- [Boot / Initial Serial Output] ---")
        print(boot_out.strip())

    tests_run = 0
    tests_passed = 0

    def assert_test(name, condition, details=""):
        nonlocal tests_run, tests_passed
        tests_run += 1
        if condition:
            print(f"  [PASS] {name}")
            tests_passed += 1
            return True
        else:
            print(f"  [FAIL] {name}: {details}")
            return False

    print("\n--- [Test 1] Interactive Help Command ---")
    resp = send_cmd(ser, "help", wait_sec=0.5)
    assert_test("help lists core commands", "SERIAL CONSOLE COMMANDS" in resp and "status" in resp and "prev" in resp and "next" in resp, resp)

    print("\n--- [Test 2] On-Target Self-Test Suite ---")
    resp = send_cmd(ser, "selftest", wait_sec=0.8)
    print(resp.strip())
    assert_test("selftest passes all 8 unit tests", "Result: 8 / 8 tests passed (ALL PASSED)" in resp, resp)

    print("\n--- [Test 3] Device Identity & Reverse-Engineering Evidence ---")
    resp = send_cmd(ser, "info", wait_sec=0.6)
    print(resp.strip())
    assert_test("info shows VID 0x0171 (Lab126/Amazon)", "0x0171 (Lab126/Amazon)" in resp, resp)
    assert_test("info shows PID 0x0443 (Kindle Page Turner)", "0x0443 (Kindle Page Turner)" in resp, resp)
    assert_test("info shows PnP ID 02 71 01 43 04 00 01", "02 71 01 43 04 00 01" in resp, resp)
    assert_test("info shows AD Type 0xFF Manuf Data 71 01 04 43", "71 01 04 43" in resp, resp)
    assert_test("info shows Firmware Rev 1.0.0 (EVID-09)", "1.0.0" in resp and "EVID-09" in resp, resp)

    print("\n--- [Test 4] Status & GATT Tracking Counters ---")
    resp = send_cmd(ser, "status", wait_sec=0.6)
    print(resp.strip())
    assert_test("status displays BLE Stack Ready", "BLE Stack Ready       : YES" in resp, resp)
    assert_test("status displays Advertising ACTIVE", "BLE Advertising       : ACTIVE" in resp, resp)
    assert_test("status displays Advertised Name", 'Advertised Name       : "Kindle Remote"' in resp, resp)
    assert_test("status displays Peer GATT Access section", "Peer GATT Access Verification" in resp, resp)

    print("\n--- [Test 5] Page Turn Action Protection When Disconnected ---")
    resp_prev = send_cmd(ser, "prev")
    assert_test("prev command rejected safely when BLE disconnected", "REJECTED" in resp_prev, resp_prev)
    resp_next = send_cmd(ser, "next")
    assert_test("next command rejected safely when BLE disconnected", "REJECTED" in resp_next, resp_next)
    resp_key = send_cmd(ser, "key 0x4e 40")
    assert_test("key command rejected safely when BLE disconnected", "REJECTED" in resp_key, resp_key)

    print("\n--- [Test 6] Battery Level Configuration ---")
    resp = send_cmd(ser, "battery 80")
    assert_test("battery command updates to 80%", "CMD battery 80%: OK" in resp, resp)
    resp = send_cmd(ser, "battery 95")
    assert_test("battery command restores to 95%", "CMD battery 95%: OK" in resp, resp)

    print("\n--- [Test 7] BLE Advertising Start, Stop & Adv Mode Control ---")
    resp_stop = send_cmd(ser, "ble_stop")
    assert_test("ble_stop stops advertising", "CMD ble_stop (disconnect=0): OK" in resp_stop, resp_stop)
    resp_start = send_cmd(ser, "ble_start")
    assert_test("ble_start restarts advertising", "CMD ble_start: OK" in resp_start, resp_start)
    resp_adv = send_cmd(ser, "adv_mode 0")
    assert_test("adv_mode 0 configures AD 0xFF + Scan Rsp AD 0x10", "CMD adv_mode: Set to 0" in resp_adv, resp_adv)

    print("\n--- [Test 8] Bond Status, GPIO Status & Accidental Clear Protection ---")
    resp = send_cmd(ser, "bond_status")
    assert_test("bond_status inspects NVS storage", "BLE BOND STATUS" in resp, resp)
    resp_unsafe = send_cmd(ser, "bond_clear")
    assert_test("bond_clear without confirm is blocked", "requires 'confirm'" in resp_unsafe or "bond_clear confirm" in resp_unsafe, resp_unsafe)
    resp_gpio = send_cmd(ser, "gpio")
    assert_test("gpio command displays button configuration", "GPIO BUTTON STATUS" in resp_gpio, resp_gpio)

    print("\n--- [Test 9] Diagnostics Event Log Ring Buffer ---")
    resp = send_cmd(ser, "logs", wait_sec=0.6)
    print(resp.strip())
    assert_test("logs command returns recent events", "RECENT LOGS" in resp and "SYS" in resp, resp)

    print("\n--- [Test 10] Robustness: Unknown Command & Buffer Overflow Protection ---")
    resp_unknown = send_cmd(ser, "nonexistent_command_xyz")
    assert_test("unknown command handled without crash", "Unknown command" in resp_unknown, resp_unknown)

    oversized_cmd = "a" * 150
    resp_overflow = send_cmd(ser, oversized_cmd, wait_sec=0.5)
    assert_test("oversized line (>128 chars) discarded safely", "exceeded max length" in resp_overflow or "kind-curie>" in resp_overflow, resp_overflow)

    ser.close()
    print(f"\n=======================================================")
    print(f"Final Verification Summary: {tests_passed} / {tests_run} tests passed")
    print(f"=======================================================")
    if tests_passed == tests_run:
        sys.exit(0)
    else:
        sys.exit(1)

if __name__ == "__main__":
    main()
