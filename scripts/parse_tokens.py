import struct
import re

# Let's inspect q32s_ins_*.sinc for instruction patterns
def parse_tokens(word):
    group = (word >> 13) & 7
    ins0012 = word & 0x1FFF
    ins0312 = (word >> 3) & 0x3FF
    ins0412 = (word >> 4) & 0x1FF
    ins0512 = (word >> 5) & 0xFF
    ins0612 = (word >> 6) & 0x7F
    ins0812 = (word >> 8) & 0x1F
    ins0912 = (word >> 9) & 0xF
    ins1112 = (word >> 11) & 3
    ins1212 = (word >> 12) & 1
    regA = word & 0xF
    regB = (word >> 4) & 0xF
    regAl = word & 7
    regBl = (word >> 3) & 7
    regCl = (word >> 6) & 7
    regDl = (word >> 9) & 7
    return {
        'group': group,
        'ins0412': ins0412,
        'regA': regA,
        'regB': regB,
        'regAl': regAl,
        'regBl': regBl,
        'regCl': regCl,
        'regDl': regDl,
    }

words = [0x0582, 0x9427, 0xE157, 0x3002, 0x94D7, 0x180B, 0x9C10, 0xF910, 0xB062, 0x7FF0, 0xF910, 0xB063, 0xFCB0, 0x0064, 0xA001]
for w in words:
    toks = parse_tokens(w)
    print(f'0x{w:04X}: group={toks["group"]} ins0412=0x{toks["ins0412"]:03X} rA={toks["regA"]} rB={toks["regB"]} rAl={toks["regAl"]} rBl={toks["regBl"]} rCl={toks["regCl"]} rDl={toks["regDl"]}')
