// Offline integrity/safety audit for v21 composite keyboard+Consumer.
const fs = require('fs');
const crypto = require('crypto');
const { crc16, encCipher, sfcCipher, defaultPatches } = require('./build_kindle_click.js');

const KS_KEY = 0xffff;
const CHIPKEY = 0x190d;
const FLASH_BASE = 0x400;
const FLASH_SIZE = 0x34000;
const APP_AREA_START = 0x2000;
const APP_AREA_END = 0x33cba;
const APP_OFF = 0x2120;
const APP_SIZE = 198824;

const originalPath = process.argv[2] || 'analysis/firmware/L1161 L1162.ufw';
const candidatePath = process.argv[3] || 'outputs/IINE_Kindle_Click_v21_Composite.ufw';

const orig = Buffer.from(fs.readFileSync(originalPath));
const cand = Buffer.from(fs.readFileSync(candidatePath));
const failures = [];
const notes = [];

function check(name, ok, detail = '') {
  if (ok) notes.push(`PASS  ${name}${detail ? '  ' + detail : ''}`);
  else failures.push(`FAIL  ${name}${detail ? '  ' + detail : ''}`);
}

function sha256(buf) {
  return crypto.createHash('sha256').update(buf).digest('hex').toUpperCase();
}

function descrambleHeader(src) {
  const h = Buffer.from(src.slice(0, 0x40));
  encCipher(h, 0, h.length, KS_KEY);
  return h;
}

function descrambleEntry(src, index) {
  const e = Buffer.from(src.slice(0x40 + index * 0x50, 0x40 + (index + 1) * 0x50));
  encCipher(e, 0, e.length, KS_KEY);
  return e;
}

const COMPOSITE_KB = Buffer.from([
  0x05, 0x01, 0x09, 0x06, 0xa1, 0x01, 0x85, 0x02,
  0x05, 0x07, 0x19, 0xe0, 0x29, 0xe7, 0x15, 0x00,
  0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
  0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
  0x95, 0x04, 0x75, 0x08, 0x15, 0x00, 0x26, 0xff, 0x00,
  0x05, 0x07, 0x19, 0x00, 0x2a, 0xff, 0x00, 0x81, 0x00,
  0x05, 0x0c, 0x15, 0x00, 0x26, 0xff, 0x03,
  0x19, 0x00, 0x2a, 0xff, 0x03,
  0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
  0x95, 0x03, 0x75, 0x01, 0x05, 0x08,
  0x25, 0x01, 0x19, 0x01, 0x29, 0x03, 0x91, 0x02,
  0x95, 0x05, 0x75, 0x01, 0x91, 0x01,
  0xc0,
]);

function expectedPatches() {
  const patches = defaultPatches().filter(
    ([name]) => name !== 'T1 Left BrightDown' && name !== 'T1 Right BrightUp'
  );
  patches.push(['PnP direct VID=0x0171', 0x44ae, Buffer.from([0x00, 0xfb, 0x71, 0x01])]);
  patches.push(['PnP direct PID=0x0443', 0x44c0, Buffer.from([0x00, 0xfb, 0x43, 0x04])]);
  patches.push(['PnP helper call NOP', 0x44ce, Buffer.from([0x00, 0x00])]);
  patches.push(['T1 Left Consumer 0x0070', 0x22908, Buffer.from([0x04, 0x00, 0x70, 0x00, 0x00, 0x00])]);
  patches.push(['T1 Right Consumer 0x006F', 0x2290e, Buffer.from([0x04, 0x00, 0x6f, 0x00, 0x00, 0x00])]);
  patches.push(['KB descriptor length 87', 0xabbe, Buffer.from([0x70, 0x75])]);
  patches.push(['KB descriptor pointer 0x1E31A60', 0xabc2, Buffer.from([0xc1, 0xff, 0x60, 0x1a, 0xe3, 0x01])]);
  patches.push(['Consumer usage -> agg+38', 0xc7c6, Buffer.from([0x38, 0x59])]);
  patches.push(['Composite keyboard descriptor @0x31940', 0x31940, COMPOSITE_KB]);
  return patches;
}

function parseHidDescriptor(desc) {
  let inputBits = 0;
  let outputBits = 0;
  let reportId = 0;
  let reportSize = 0;
  let reportCount = 0;
  let usagePage = 0;
  let consumerPage = false;
  for (let i = 0; i < desc.length;) {
    const prefix = desc[i++];
    const size = (prefix & 3) === 3 ? 4 : (prefix & 3);
    const type = (prefix >> 2) & 3;
    const tag = (prefix >> 4) & 15;
    let value = 0;
    for (let j = 0; j < size; j++) value |= desc[i + j] << (8 * j);
    i += size;
    if (type === 1) {
      if (tag === 0) usagePage = value;
      if (tag === 7) reportSize = value;
      if (tag === 9) reportCount = value;
      if (tag === 8) reportId = value;
    } else if (type === 0) {
      if (tag === 8) {
        inputBits += reportSize * reportCount;
        if (usagePage === 0x0c) consumerPage = true;
      }
      if (tag === 9) outputBits += reportSize * reportCount;
    }
  }
  return { inputBits, outputBits, reportId, consumerPage };
}

// ---- container ----
check('UFW size', cand.length === 645184, `${cand.length}`);
check('original size', orig.length === cand.length, `${orig.length}`);

const hdr = descrambleHeader(cand);
const numents = hdr.readUInt16LE(8);
check('entry count', numents === 8, `${numents}`);
check('UFW total length field', hdr.readUInt32LE(4) === cand.length, `0x${hdr.readUInt32LE(4).toString(16)}`);
check('chip name', hdr.slice(0x10, 0x20).toString('latin1').replace(/\0.*$/, '') === 'AC632N', hdr.slice(0x10, 0x20).toString('latin1'));
check('UFW header CRC', crc16(hdr.slice(2, 0x40)) === hdr.readUInt16LE(0), `stored 0x${hdr.readUInt16LE(0).toString(16)}`);

const listEnd = 0x40 + numents * 0x50;
check('UFW entry-list CRC', crc16(cand.slice(0x40, listEnd)) === hdr.readUInt16LE(2), `stored 0x${hdr.readUInt16LE(2).toString(16)}`);

const e0 = descrambleEntry(cand, 0);
const flashCrc = crc16(cand.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
check('entry0 flash CRC', e0.readUInt32LE(4) === flashCrc, `stored 0x${e0.readUInt32LE(4).toString(16)}, calculated 0x${flashCrc.toString(16)}`);
check('entry0 offset/size', e0.readUInt32LE(8) === 0x400 && e0.readUInt32LE(0x0c) === 0x34000, `off=0x${e0.readUInt32LE(8).toString(16)} size=0x${e0.readUInt32LE(0x0c).toString(16)}`);

// ---- decrypted flash image and JLFS ----
const candFlash = Buffer.from(cand.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
const origFlash = Buffer.from(orig.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
const candDec = Buffer.from(candFlash);
const origDec = Buffer.from(origFlash);
sfcCipher(candDec, APP_AREA_START, APP_AREA_END - APP_AREA_START, APP_AREA_START, CHIPKEY);
sfcCipher(origDec, APP_AREA_START, APP_AREA_END - APP_AREA_START, APP_AREA_START, CHIPKEY);

check('app data CRC', crc16(candDec.slice(APP_OFF, APP_OFF + APP_SIZE)) === candDec.readUInt16LE(0x2022), `stored 0x${candDec.readUInt16LE(0x2022).toString(16)}`);
check('JLFS entry header CRC', crc16(candDec.slice(0x2022, 0x2040)) === candDec.readUInt16LE(0x2020), `stored 0x${candDec.readUInt16LE(0x2020).toString(16)}`);
check('JLFS area CRC', crc16(candDec.slice(0x2020, 0x2020 + 0x30c7a)) === candDec.readUInt16LE(0x2002), `stored 0x${candDec.readUInt16LE(0x2002).toString(16)}`);
check('JLFS area header CRC', crc16(candDec.slice(0x2002, 0x2020)) === candDec.readUInt16LE(0x2000), `stored 0x${candDec.readUInt16LE(0x2000).toString(16)}`);

// ---- expected patch set ----
const patches = expectedPatches();
let patchMismatches = [];
for (const [name, off, bytes] of patches) {
  const actual = candDec.slice(APP_OFF + off, APP_OFF + off + bytes.length);
  if (!actual.equals(bytes)) {
    patchMismatches.push(`${name}@0x${off.toString(16)} got ${actual.toString('hex')} want ${bytes.toString('hex')}`);
  }
}
check('expected patch bytes', patchMismatches.length === 0, patchMismatches.join('; '));

// ---- descriptor structure ----
const descAt = APP_OFF + 0x31940;
const actualDesc = candDec.slice(descAt, descAt + COMPOSITE_KB.length);
check('composite descriptor bytes', actualDesc.equals(COMPOSITE_KB), `len=${COMPOSITE_KB.length}`);
const parsed = parseHidDescriptor(actualDesc);
check('descriptor report ID 2', parsed.reportId === 2, `${parsed.reportId}`);
check('descriptor input report = 8 bytes', parsed.inputBits === 64, `${parsed.inputBits} bits`);
check('descriptor output report = 1 byte', parsed.outputBits === 8, `${parsed.outputBits} bits`);
check('descriptor contains Consumer page', parsed.consumerPage);

// Difference set in decrypted app data: patches + JLFS header CRC fields.
const allowed = new Set();
for (let i = 0x2000; i < 0x2024; i++) allowed.add(i);
for (const [, off, bytes] of patches) {
  for (let i = 0; i < bytes.length; i++) allowed.add(APP_OFF + off + i);
}
let unexpected = [];
for (let i = APP_AREA_START; i < APP_AREA_END; i++) {
  if (candDec[i] !== origDec[i] && !allowed.has(i)) {
    unexpected.push(`0x${i.toString(16)}:${origDec[i].toString(16)}->${candDec[i].toString(16)}`);
    if (unexpected.length >= 20) break;
  }
}
check('no unexpected app changes', unexpected.length === 0, unexpected.join(' '));

// ---- unchanged boot/config regions ----
let rawDiffs = [];
for (let i = 0; i < FLASH_SIZE; i++) {
  const inApp = i >= APP_AREA_START && i < APP_AREA_END;
  if (!inApp && candFlash[i] !== origFlash[i]) {
    rawDiffs.push(`0x${i.toString(16)}`);
    if (rawDiffs.length >= 20) break;
  }
}
check('boot/uboot/config/p11 unchanged', rawDiffs.length === 0, rawDiffs.join(' '));

let nonFF = [];
for (let i = APP_AREA_END; i < FLASH_SIZE; i++) {
  if (candFlash[i] !== 0xff) {
    nonFF.push(`0x${i.toString(16)}`);
    if (nonFF.length >= 10) break;
  }
}
check('tail padding is 0xFF', nonFF.length === 0, nonFF.join(' '));

let ufwDiffs = [];
for (let i = 0; i < 0x90; i++) {
  const isCrcField = (i < 4) || (i >= 0x42 && i < 0x46);
  if (!isCrcField && cand[i] !== orig[i]) ufwDiffs.push(`0x${i.toString(16)}`);
}
check('UFW container only CRC fields changed', ufwDiffs.length === 0, ufwDiffs.join(' '));

console.log('original SHA-256 :', sha256(orig));
console.log('candidate SHA-256:', sha256(cand));
console.log('');
for (const n of notes) console.log(n);
for (const f of failures) console.log(f);
console.log('');
console.log(failures.length === 0 ? 'AUDIT RESULT: PASS' : `AUDIT RESULT: FAIL (${failures.length})`);
process.exitCode = failures.length === 0 ? 0 : 1;
