// Independent reimplementation of the JieLi IINE -> Kindle Click patch/repack
// pipeline, based on the documented RE facts (algorithm verified against samples).
const fs = require('fs');

const KS_KEY = 0xffff;
const CHIPKEY = 0x190d;

function encCipher(buf, off, size, key) {
  let k = key & 0xffff;
  for (let i = 0; i < size; i++) {
    buf[off + i] ^= k & 0xff;
    k = (((k << 1) & 0xffff) ^ (k & 0x8000 ? 0x1021 : 0)) & 0xffff;
  }
}

function sfcCipher(buf, off, size, base, key, block = 32) {
  for (let i = 0; i < size; i += block) {
    const n = Math.min(size - i, block);
    encCipher(buf, off + i, n, key ^ (((off + i - base) >>> 2) & 0xffff));
  }
}

function crc16(data, crc = 0) {
  let c = crc & 0xffff;
  for (const b of data) {
    c ^= b << 8;
    for (let i = 0; i < 8; i++) c = (c & 0x8000) ? (((c << 1) ^ 0x1021) & 0xffff) : ((c << 1) & 0xffff);
  }
  return c & 0xffff;
}

function crc32(data, crc = 0x26536734) {
  let c = crc >>> 0;
  for (const b of data) {
    c ^= b;
    for (let i = 0; i < 8; i++) c = (c & 1) ? ((c >>> 1) ^ 0xedb88320) >>> 0 : (c >>> 1);
  }
  return c >>> 0;
}

function build(origPath, outPath, opts = {}) {
  const raw = Buffer.from(fs.readFileSync(origPath));
  const FLASH_BASE = 0x400;
  const dec = Buffer.from(raw.slice(FLASH_BASE, FLASH_BASE + 0x34000));

  // decrypt app area [0x2000:0x33CBA]
  const APPSTART = 0x2000, APPEND = 0x33cba;
  sfcCipher(dec, APPSTART, APPEND - APPSTART, APPSTART, CHIPKEY);

  const appOff = 0x2120, appSize = 198824;
  // sanity: app data CRC should match JLFS entry data CRC
  const appCrcBefore = crc16(dec.slice(appOff, appOff + appSize));
  const storedCrc = dec.readUInt16LE(0x2022);

  // === HARDWARE & FIRMWARE SAFETY GATES (Anti-Brick & Anti-Corruption Protection) ===
  // In JieLi AC6321A / BD19 architecture:
  // - app.bin 0x00000..0x2DEA6 (VMA 0x1E00120..0x1E2DFC6): Executable .text & .rodata
  // - app.bin 0x2DEA8..0x308A8: Flash LMA of RAM .data sections copied to RAM on MCU boot:
  //     * 0x2DEA8..0x2EB08 (3168B) -> RAM 0x0840..0x14A0 (BLE Stack context, GATT handles, RF buffers)
  //     * 0x2EB08..0x30890 (7560B) -> RAM 0x40C0..0x5E48 (System OS & peripheral structures)
  // CRITICAL RULE: Never inject code, descriptors, or write patches to offset >= 0x2DEA8!
  // Doing so corrupts RAM at boot time via memcpy in _start (1e0016c), crashing the BLE stack!
  const RAM_DATA_LMA_START = 0x2dea8;
  const RAM_DATA_LMA_END   = 0x30890;
  const VMA_BASE           = 0x1e00120;
  const TEXT_VMA_END       = VMA_BASE + RAM_DATA_LMA_START; // 0x1E2DFC8

  const patches = (opts.patches || defaultPatches());
  for (const [name, off, bytes] of patches) {
    const patchBuf = Buffer.from(bytes);

    // GATE 1: App boundary check
    if (off < 0 || off + patchBuf.length > appSize) {
      throw new Error(`[SECURITY AUDIT FAIL] Patch "${name}" at offset 0x${off.toString(16)} (length ${patchBuf.length}) exceeds app.bin boundary (0x${appSize.toString(16)})!`);
    }

    // GATE 2: RAM .data Flash Load Image Protection (Anti-BLE Stack Corruption)
    if (off + patchBuf.length > RAM_DATA_LMA_START && !opts.allowDataLoadSegment) {
      throw new Error(
        `[SECURITY AUDIT FAIL] Patch "${name}" at offset 0x${off.toString(16)} (length ${patchBuf.length}) touches RAM .data load image (>= 0x${RAM_DATA_LMA_START.toString(16)})!\n` +
        `This region is copied into runtime RAM during MCU boot (0x0840..0x14A0 & 0x40C0..0x5E48).\n` +
        `Injecting code or patching here corrupts critical RAM variables including the BLE stack (causing instant disconnects).\n` +
        `All executable code and descriptors MUST be placed strictly within .text (< 0x${RAM_DATA_LMA_START.toString(16)}).`
      );
    }

    // GATE 3: Code Jump Target VMA Validation (Anti-Data Execution)
    // Scan patch bytes for 32-bit little-endian pointers in firmware address space (0x01E00000..0x01E40000)
    for (let i = 0; i <= patchBuf.length - 4; i++) {
      const ptr = patchBuf.readUInt32LE(i);
      if (ptr >= TEXT_VMA_END && ptr <= VMA_BASE + RAM_DATA_LMA_END) {
        throw new Error(
          `[SECURITY AUDIT FAIL] Patch "${name}" contains pointer 0x${ptr.toString(16)} at byte offset ${i} targeting RAM .data Flash load image [0x${TEXT_VMA_END.toString(16)}..0x${(VMA_BASE + RAM_DATA_LMA_END).toString(16)})!\n` +
          `Jumping to or executing from the RAM .data load segment is strictly prohibited.`
        );
      }
    }

    patchBuf.copy(dec, appOff + off);
  }

  // JLFS CRC chain
  const appCrc = crc16(dec.slice(appOff, appOff + appSize));
  dec.writeUInt16LE(appCrc, 0x2022);
  const entryHdr = Buffer.from(dec.slice(0x2022, 0x2040));
  const entryHdrCrc = crc16(entryHdr);
  dec.writeUInt16LE(entryHdrCrc, 0x2020);

  const areaData = dec.slice(0x2020, 0x2020 + 0x30c7a);
  const areaCrc = crc16(areaData);
  dec.writeUInt16LE(areaCrc, 0x2002);
  const areaHdr = Buffer.from(dec.slice(0x2002, 0x2020));
  const areaHdrCrc = crc16(areaHdr);
  dec.writeUInt16LE(areaHdrCrc, 0x2000);

  // re-encrypt app area
  sfcCipher(dec, APPSTART, APPEND - APPSTART, APPSTART, CHIPKEY);
  dec.copy(raw, FLASH_BASE); // [0x400,0x34400)
  raw.fill(0xff, FLASH_BASE + APPEND, FLASH_BASE + 0x34000);

  // flash image CRC -> UFW entry 0 +0x04
  const flashCrc = crc16(raw.slice(FLASH_BASE, FLASH_BASE + 212992));
  const e0 = Buffer.from(raw.slice(0x40, 0x90));
  encCipher(e0, 0, e0.length, KS_KEY);
  e0.writeUInt32LE(flashCrc, 4);
  encCipher(e0, 0, e0.length, KS_KEY);
  e0.copy(raw, 0x40);

  // UFW list CRC over RAW entry area
  const hdr = Buffer.from(raw.slice(0, 0x40));
  encCipher(hdr, 0, 0x40, KS_KEY);
  const numents = hdr.readUInt16LE(8);
  const headersize = 0x40 + numents * 0x50;
  const listCrc = crc16(raw.slice(0x40, headersize));
  hdr.writeUInt16LE(listCrc, 2);
  const hdrCrc = crc16(hdr.slice(2, 0x40));
  hdr.writeUInt16LE(hdrCrc, 0);
  encCipher(hdr, 0, 0x40, KS_KEY);
  hdr.copy(raw, 0);

  fs.writeFileSync(outPath, raw);
  return {
    appCrcBefore, storedCrc, appCrc, entryHdrCrc, areaCrc, areaHdrCrc,
    flashCrc, listCrc, hdrCrc, size: raw.length,
    fileChecksum: crc16(raw).toString(16).padStart(4,'0') + '-' + crc32(raw).toString(16).padStart(8,'0'),
  };
}

function defaultPatches() {
  return [
    ['GAP Reader', 0x211c6, Buffer.from('Kindle-PT\0\0\0','latin1')],
    ['GAP Keyboard', 0x211eb, Buffer.from('Kindle-PT\0\0\0\0\0','latin1')],
    ['DIS Manufacturer', 0x22384, Buffer.from('Amazon\0\0\0\0\0\0\0','latin1')],
    ['Rodata PID', 0x22ef0, Buffer.from([0x04,0x43,0x00])],
    ['ADV mov r4', 0x04d36, Buffer.from([0xc4,0xff,0x10,0x30,0xe2,0x01])],
    ['ADV r0=0x71', 0x04d58, Buffer.from([0x10,0x77])],
    ['ADV r0=0x01', 0x04d5e, Buffer.from([0x10,0x70])],
    ['ADV r6=4', 0x04d90, Buffer.from([0x46,0x70])],
    ['Mode redirect', 0x0712e, Buffer.from([0xc6,0xff,0x0b,0x13,0xe2,0x01])],
    ['T1 A PageDown', 0x2289c, Buffer.from([0x02,0x00,0x00,0x4e,0x00,0x00])],
    ['T1 B PageUp', 0x228a2, Buffer.from([0x02,0x00,0x00,0x4b,0x00,0x00])],
    ['T1 X Power', 0x228a8, Buffer.from([0x02,0x00,0x00,0x66,0x00,0x00])],
    ['T1 Y Power', 0x228ae, Buffer.from([0x02,0x00,0x00,0x66,0x00,0x00])],
    ['T1 Up PageUp', 0x228fc, Buffer.from([0x02,0x00,0x00,0x4b,0x00,0x00])],
    ['T1 Down PageDown', 0x22902, Buffer.from([0x02,0x00,0x00,0x4e,0x00,0x00])],
    ['T1 Left BrightDown', 0x22908, Buffer.from([0x02,0x00,0x00,0x70,0x00,0x00])],
    ['T1 Right BrightUp', 0x2290e, Buffer.from([0x02,0x00,0x00,0x6f,0x00,0x00])],
  ];
}

if (require.main === module) {
  const out = process.argv[3] || 'work/our_candidate.ufw';
  const r = build(process.argv[2] || 'analysis/firmware/L1161 L1162.ufw', out);
  console.log(JSON.stringify(r, null, 2));
}

module.exports = { build, crc16, crc32, encCipher, sfcCipher, defaultPatches };
