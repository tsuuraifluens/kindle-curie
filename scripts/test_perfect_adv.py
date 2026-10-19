def test_adv():
    adv = bytearray(31)
    
    # 1. Flags: [0x02, 0x01, 0x06]
    adv[0] = 2
    adv[1] = 1
    adv[2] = 6
    
    # 2. Service UUID: [0x03, 0x03, 0x12, 0x18]
    adv[3] = 3
    adv[4] = 3
    adv[5] = 0x12
    adv[6] = 0x18
    
    # 3. Appearance: [0x03, 0x19, 0xC4, 0x03]
    adv[7] = 3
    adv[8] = 0x19
    adv[9] = 0xC4
    adv[10] = 0x03
    
    # 4. Manufacturer data:
    # r4 points to [0x04, 0x43, 0x00]
    mfg_payload = bytes([0x04, 0x43])
    strlen_r4 = len(mfg_payload)
    assert strlen_r4 == 2
    
    r6 = strlen_r4 + 2 # 4
    r1 = r6 # 4
    
    # Stack setup:
    # 0x4D58: sp[0] = 0x71
    # 0x4D5E: sp[1] = 0x01
    # 0x4D70: memcpy(sp + 2, r4, len=2)
    sp = bytearray(16)
    sp[0] = 0x71
    sp[1] = 0x01
    sp[2:4] = mfg_payload
    
    # At 0x4DD8:
    r2 = r6 # 4
    r7 = r2 + 2 # 6
    assert r7 < 21
    
    # At 0x4DE4:
    adv[11] = r6 + 1 # 5 (0x05)
    adv[12] = 0xFF
    adv[13:17] = sp[:4] # [0x71, 0x01, 0x04, 0x43]
    
    # At 0x4DFA:
    r5 = r7 + 11 # 17
    
    # Name copying:
    name = b"Kindle Remote" # 13 bytes
    copy_len = len(name) # 13
    adv[r5] = copy_len + 1 # 14 (0x0E)
    adv[r5 + 1] = 0x09 # Complete Local Name
    adv[r5 + 2 : r5 + 2 + copy_len] = name
    
    total_len = r5 + 2 + copy_len
    print(f"Total adv_data length: {total_len} bytes")
    assert total_len == 31
    print("adv_data hex:")
    print(" ".join(f"{b:02X}" for b in adv))
    
    # Verify elements:
    assert adv[0:3] == bytes([0x02, 0x01, 0x06]) # Flags
    assert adv[3:7] == bytes([0x03, 0x03, 0x12, 0x18]) # UUID 0x1812
    assert adv[7:11] == bytes([0x03, 0x19, 0xC4, 0x03]) # Appearance
    assert adv[11:17] == bytes([0x05, 0xFF, 0x71, 0x01, 0x04, 0x43]) # Lab126 VID/PID!
    assert adv[17:31] == bytes([0x0E, 0x09]) + b"Kindle Remote" # Complete Local Name!
    print("ALL ASSERTIONS PASSED! 100% PERFECT 31-BYTE BLE ADVERTISING PACKET!")

test_adv()
