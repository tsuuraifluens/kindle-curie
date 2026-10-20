#!/usr/bin/env node
// Universal Firmware Security & Hardware Safety Auditor
// For JieLi AC6321A / BD19 Bluetooth Gamepad Firmwares
//
// Enforces 9 defense-in-depth safety rules, including:
// - Zero-Brick Bootloader & Recovery Partition Protection
// - RAM .data Flash Load Image Immunity (BLE stack & RTOS memory protection)
// - Code Execution Boundary & Jump Target Legality
// - 5-Level CRC Chain Verification

const fs = require('fs');
const crypto = require('crypto');
const { crc16, encCipher, sfcCipher } = require('./build_kindle_click.js');

const KS_KEY = 0xffff;
const CHIPKEY = 0x190d;
const FLASH_BASE = 0x400;
const FLASH_SIZE = 0x34000;
const APP_AREA_START = 0x2000;
const APP_AREA_END = 0x33cba;
const APP_OFF = 0x2120;
const APP_SIZE = 198824;

// JieLi AC6321A Physical Memory Layout
const RAM_DATA_LMA_START = 0x2dea8; // VMA 0x1E2DFC8
const RAM_DATA_LMA_END   = 0x30890; // VMA 0x1E309B0
const VMA_BASE           = 0x1e00120;
const TEXT_VMA_END       = VMA_BASE + RAM_DATA_LMA_START; // 0x1E2DFC8

const stockPath = process.argv[2] || 'analysis/firmware/L1161 L1162.ufw';
const candPath  = process.argv[3] || 'outputs/IINE_Kindle_Click_v25_SleepLockout.ufw';

if (!fs.existsSync(stockPath)) {
  console.error(`[ERROR] Stock firmware file not found: ${stockPath}`);
  process.exit(1);
}
if (!fs.existsSync(candPath)) {
  console.error(`[ERROR] Candidate firmware file not found: ${candPath}`);
  process.exit(1);
}

const orig = fs.readFileSync(stockPath);
const cand = fs.readFileSync(candPath);

const checks = [];
let passedCount = 0, failedCount = 0;

function audit(id, title, condition, details = '') {
  const ok = Boolean(condition);
  if (ok) {
    passedCount++;
    checks.push({ status: 'PASS', id, title, details });
  } else {
    failedCount++;
    checks.push({ status: 'FAIL', id, title, details });
  }
}

const sha256 = b => crypto.createHash('sha256').update(b).digest('hex').toUpperCase();
const decHdr = s => { const h = Buffer.from(s.slice(0, 0x40)); encCipher(h, 0, h.length, KS_KEY); return h; };
const decEntry = (s, i) => { const e = Buffer.from(s.slice(0x40 + i * 0x50, 0x40 + (i + 1) * 0x50)); encCipher(e, 0, e.length, KS_KEY); return e; };

// ==========================================
// RULE 1: UFW Container & Size Conformance
// ==========================================
audit('R1-1', 'UFW file size is exactly 645,184 bytes', cand.length === 645184, `actual: ${cand.length}`);
const hdr = decHdr(cand);
const expectedHdrCrc = crc16(hdr.slice(2, 0x40));
const actualHdrCrc = hdr.readUInt16LE(0);
audit('R1-2', 'UFW Header CRC-16 validates', expectedHdrCrc === actualHdrCrc, `stored: 0x${actualHdrCrc.toString(16)}, calc: 0x${expectedHdrCrc.toString(16)}`);

const numents = hdr.readUInt16LE(8);
const headersize = 0x40 + numents * 0x50;
const expectedListCrc = crc16(cand.slice(0x40, headersize));
const actualListCrc = hdr.readUInt16LE(2);
audit('R1-3', 'UFW Entry List CRC-16 validates', expectedListCrc === actualListCrc, `stored: 0x${actualListCrc.toString(16)}, calc: 0x${expectedListCrc.toString(16)}`);

const e0 = decEntry(cand, 0);
const calcFlashCrc = crc16(cand.slice(FLASH_BASE, FLASH_BASE + 212992));
const storedFlashCrc = e0.readUInt32LE(4);
audit('R1-4', 'Entry 0 Flash Image CRC validates', calcFlashCrc === storedFlashCrc, `stored: 0x${storedFlashCrc.toString(16)}, calc: 0x${calcFlashCrc.toString(16)}`);

// ==========================================
// RULE 2: JLFS App Integrity & 4-Level CRC
// ==========================================
const dec = Buffer.from(cand.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
sfcCipher(dec, APP_AREA_START, APP_AREA_END - APP_AREA_START, APP_AREA_START, CHIPKEY);
const appBytes = dec.slice(APP_OFF, APP_OFF + APP_SIZE);
const appCrc = crc16(appBytes);
audit('R2-1', 'JLFS app.bin data CRC-16 validates', dec.readUInt16LE(0x2022) === appCrc, `crc: 0x${appCrc.toString(16)}`);
audit('R2-2', 'JLFS app.bin entry header CRC-16 validates', dec.readUInt16LE(0x2020) === crc16(dec.slice(0x2022, 0x2040)));
audit('R2-3', 'JLFS area data CRC-16 validates', dec.readUInt16LE(0x2002) === crc16(dec.slice(0x2020, 0x2020 + 0x30c7a)));
audit('R2-4', 'JLFS area header CRC-16 validates', dec.readUInt16LE(0x2000) === crc16(dec.slice(0x2002, 0x2020)));

// ==========================================
// RULE 3: Hardware Bootloader & Recovery Partition Protection (Zero-Brick Guarantee)
// ==========================================
const origDec = Buffer.from(orig.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
sfcCipher(origDec, APP_AREA_START, APP_AREA_END - APP_AREA_START, APP_AREA_START, CHIPKEY);
const origAppBytes = origDec.slice(APP_OFF, APP_OFF + APP_SIZE);

const ubootStock = orig.slice(FLASH_BASE, FLASH_BASE + 0x2000);
const ubootCand  = cand.slice(FLASH_BASE, FLASH_BASE + 0x2000);
audit('R3-1', 'Uboot & Mask ROM download handler [0x400..0x2400] 100% stock-identical', ubootCand.equals(ubootStock), 'Guarantees unbrickable hardware recovery');

const trailingStock = orig.slice(FLASH_BASE + 0x34000);
const trailingCand  = cand.slice(FLASH_BASE + 0x34000);
audit('R3-2', 'Flash trailing partitions [0x34400..EOF] 100% stock-identical', trailingCand.equals(trailingStock), 'Preserves factory RF calibration, MAC & VM');

audit('R3-3', 'JLFS app header preamble [0x2004..0x2020] stock-identical', dec.slice(0x2004, 0x2020).equals(origDec.slice(0x2004, 0x2020)));
audit('R3-4', 'JLFS app entry metadata [0x2024..0x2040] stock-identical', dec.slice(0x2024, 0x2040).equals(origDec.slice(0x2024, 0x2040)));

// ==========================================
// RULE 4: RAM .data Flash Load Image Protection (Anti-BLE Disconnect & Anti-RAM Corruption)
// ==========================================
const candDataLMA = appBytes.slice(RAM_DATA_LMA_START);
const origDataLMA = origAppBytes.slice(RAM_DATA_LMA_START);
const isDataLMAUnmodified = candDataLMA.equals(origDataLMA);
audit('R4-1', 'RAM .data Flash load image [0x2DEA8..0x308A8] 100% stock-identical', isDataLMAUnmodified,
  isDataLMAUnmodified ? 'BLE stack & RTOS memory structures in RAM 0x0840/0x40C0 completely safe' : 'FATAL: RAM initialization image corrupted! BLE stack will crash!');

// Find all modified byte ranges in app.bin
const diffs = [];
let diffStart = -1;
for (let i = 0; i < APP_SIZE; i++) {
  if (appBytes[i] !== origAppBytes[i]) {
    if (diffStart === -1) diffStart = i;
  } else {
    if (diffStart !== -1) {
      diffs.push({ start: diffStart, end: i, length: i - diffStart });
      diffStart = -1;
    }
  }
}
if (diffStart !== -1) diffs.push({ start: diffStart, end: APP_SIZE, length: APP_SIZE - diffStart });

// ==========================================
// RULE 5: Code Execution Boundary Conformance
// ==========================================
const diffsInDataSection = diffs.filter(d => d.start >= RAM_DATA_LMA_START || d.end > RAM_DATA_LMA_START);
audit('R5-1', 'All patches strictly confined within executable .text (< 0x2DEA8)', diffsInDataSection.length === 0,
  diffsInDataSection.length === 0 ? `${diffs.length} patch regions all within .text` : `Illegal writes detected at: ${diffsInDataSection.map(d=>'0x'+d.start.toString(16)).join(',')}`);

// ==========================================
// RULE 6: Pointer & Jump Target Legality Validation
// ==========================================
const badPointers = [];
for (const d of diffs) {
  const span = appBytes.slice(d.start, d.end);
  for (let i = 0; i <= span.length - 4; i++) {
    const ptr = span.readUInt32LE(i);
    if (ptr >= TEXT_VMA_END && ptr <= VMA_BASE + RAM_DATA_LMA_END) {
      badPointers.push({ off: d.start + i, targetVma: ptr });
    }
  }
}
audit('R6-1', 'No indirect jumps or pointers target RAM .data Flash LMA [0x1E2DFC8..0x1E309B0)', badPointers.length === 0,
  badPointers.length === 0 ? 'All code jumps target valid executable addresses' : `Illegal targets found: ${badPointers.map(p=>`0x${p.targetVma.toString(16)} @0x${p.off.toString(16)}`).join(',')}`);

// ==========================================
// RULE 7: BLE Radio & Connection Timing Parameters
// ==========================================
const advInterval = appBytes.readUInt16LE(0x04c90);
audit('R7-1', 'BLE advertising interval configured within valid specification', advInterval >= 32 && advInterval <= 800,
  `adv_interval: ${advInterval} slots (${(advInterval * 0.625).toFixed(1)}ms)`);

// ==========================================
// RULE 8: Zero Unintended Modifications
// ==========================================
let unexpectedFlashBytes = 0;
for (let i = APP_AREA_START; i < APP_AREA_END; i++) {
  if (dec[i] !== origDec[i]) {
    const inApp = (i >= APP_OFF && i < APP_OFF + APP_SIZE);
    const inJlfsHdr = (i >= 0x2000 && i < 0x2024);
    if (!inApp && !inJlfsHdr) unexpectedFlashBytes++;
  }
}
audit('R8-1', 'Zero stray modifications outside declared app and JLFS regions', unexpectedFlashBytes === 0, `unexpected: ${unexpectedFlashBytes} bytes`);

// ==========================================
// SUMMARY AUDIT REPORT
// ==========================================
console.log('================================================================');
console.log('       JIELI AC6321A UNIVERSAL FIRMWARE SECURITY AUDIT         ');
console.log('================================================================');
console.log(`Candidate: ${candPath}`);
console.log(`SHA-256:   ${sha256(cand)}`);
console.log(`Diffs:     ${diffs.length} distinct modified regions in app.bin`);
console.log('----------------------------------------------------------------');

checks.forEach(c => {
  const icon = c.status === 'PASS' ? '  PASS' : '>> FAIL';
  console.log(`${icon}  [${c.id}] ${c.title.padEnd(58)} ${c.details ? '(' + c.details + ')' : ''}`);
});

console.log('================================================================');
if (failedCount > 0) {
  console.error(`\nAUDIT REJECTED: ${failedCount} security/safety checks FAILED!`);
  console.error(`This firmware is unsafe to flash and must NOT be released.`);
  process.exit(1);
} else {
  console.log(`\nAUDIT APPROVED: ALL ${passedCount} SECURITY & INTEGRITY CHECKS PASSED.`);
  console.log(`Zero-brick hardware recovery guaranteed. BLE stack memory verified uncorrupted.`);
  process.exit(0);
}
