import struct

def decode_thumb_branch(hw1, hw2, pc):
    # hw1, hw2 are 16-bit uints
    # pc is address of instruction + 4
    is_blx = (hw2 & 0x1000) == 0  # bit 12 of second halfword: 1 for BL, 0 for BLX
    
    s = (hw1 >> 10) & 1
    imm10 = hw1 & 0x3ff
    j1 = (hw2 >> 13) & 1
    j2 = (hw2 >> 11) & 1
    imm11 = hw2 & 0x7ff
    
    i1 = ~(j1 ^ s) & 1
    i2 = ~(j2 ^ s) & 1
    
    # sign bit
    sign = -1 if s else 1
    
    # imm32
    if is_blx:
        # bit 0 is forced to 0
        imm25 = (s << 24) | (i1 << 23) | (i2 << 22) | (imm10 << 12) | ((imm11 & ~1) << 1)
        if s:
            imm25 -= (1 << 25)
        # For BLX, target is Align(pc, 4) + imm
        target = (pc & ~3) + imm25
    else:
        imm25 = (s << 24) | (i1 << 23) | (i2 << 22) | (imm10 << 12) | (imm11 << 1)
        if s:
            imm25 -= (1 << 25)
        target = pc + imm25
        
    return is_blx, target

# Test on 0x18f6c
hw1 = 0xf7ef
hw2 = 0xeb2a
pc = 0x18f6c + 4
is_blx, target = decode_thumb_branch(hw1, hw2, pc)
print(f"0x18f6c: is_blx={is_blx}, target={hex(target)}")
