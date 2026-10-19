""" CRC calculation routines in pure Python (no external crcmod dependency) """

__all__ = [
    'jl_crc16',
    'jl_crc32'
]

def jl_crc16(data: bytes, crc: int = 0x0000) -> int:
    """CRC-16-CCITT (poly=0x1021, init=0x0000, rev=False)"""
    for byte in data:
        crc ^= (byte << 8)
        for _ in range(8):
            if crc & 0x8000:
                crc = ((crc << 1) ^ 0x1021) & 0xFFFF
            else:
                crc = (crc << 1) & 0xFFFF
    return crc

def jl_crc32(data: bytes, crc: int = 0x26536734) -> int:
    """CRC-32 (poly=0xEDB88320 reflected, init=0x26536734, rev=True)"""
    # Standard reflected CRC32 table
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xEDB88320
            else:
                crc = crc >> 1
    return crc & 0xFFFFFFFF
