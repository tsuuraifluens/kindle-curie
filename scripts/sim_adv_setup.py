# Simulate adv_setup_init with:
# Case A: original 0x21053 = b"mt_IINE\x00"
# Case B: candidate v1.0 0x21053 = bytes([0x71, 0x01, 0x04, 0x43, 0x00, 0x00, 0x00, 0x00])

def sim_adv(mfg_bytes):
    adv = bytearray(31)
    
    # Flags: [0x02, 0x01, 0x06] (3B)
    adv[0] = 2
    adv[1] = 1
    adv[2] = 6
    
    # UUID: [0x03, 0x03, 0x12, 0x18] (4B)
    adv[3] = 3
    adv[4] = 3
    adv[5] = 0x12
    adv[6] = 0x18
    
    # Appearance: [0x03, 0x19, 0xC4, 0x03] (4B)
    adv[7] = 3
    adv[8] = 0x19
    adv[9] = 0xC4
    adv[10] = 0x03
    
    # Now simulate 0x4D36 - 0x4D70:
    # r4 = mfg_bytes
    # strlen(r4)
    strlen_r4 = len(mfg_bytes.split(b'\x00')[0])
    print(f"strlen(mfg_bytes) = {strlen_r4}")
    
    # 0x4D44: r6 = 20
    # 0x4D46: jhi -> 0x4D58
    # 0x4D4A: r6 = r0 + 2
    r6 = strlen_r4 + 2
    # 0x4D4C: r1 = uxtb(r6)
    r1 = r6 & 0xFF
    
    # At 0x4D6C - 0x4D70:
    # memcpy(sp, mfg_bytes, r2=r1-2)
    # So on stack:
    stack_buf = bytearray(mfg_bytes[:strlen_r4])
    
    # At 0x4DD8:
    r2 = r6 & 0xFF
    r7 = r2 + 2 # r7 = strlen + 4
    
    print(f"r7 = {r7}")
    if r7 < 21:
        # 0x4DE4:
        # [adv + 11] = r6 + 1
        adv_len_field = r6 + 1
        adv_type_field = 0xFF
        adv[11] = adv_len_field
        adv[12] = adv_type_field
        # memcpy(adv + 13, stack_buf, r2)
        # What is r2? r2 is uxtb(r6) = strlen + 2!
        print(f"AD Type 0xFF Length Field: 0x{adv_len_field:02X} ({adv_len_field})")
        print(f"Bytes written at adv[11..]:")
        data_copied = stack_buf[:r6] # Wait, stack_buf has only strlen_r4 bytes!
        print(f"stack_buf: {stack_buf.hex(' ')}")
        adv[13:13+len(stack_buf)] = stack_buf
        total_adv_len = 13 + len(stack_buf)
        print(f"Full adv_data ({total_adv_len}B): {adv[:total_adv_len].hex(' ')}")
    else:
        print("Branch to 0x4DFA (skip AD 0xFF in adv_data)")

print("=== Case A: mt_IINE ===")
sim_adv(b"mt_IINE\x00")

print("\n=== Case B: 71 01 04 43 ===")
sim_adv(bytes([0x71, 0x01, 0x04, 0x43, 0x00, 0x00, 0x00, 0x00]))
