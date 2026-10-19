// Audit v21c: 67-byte composite keyboard+Consumer, no output report.
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
const candidatePath = process.argv[3] || 'outputs/IINE_Kindle_Click_v21c_Composite.ufw';
const orig = Buffer.from(fs.readFileSync(originalPath));
const cand = Buffer.from(fs.readFileSync(candidatePath));
const failures = [];
const notes = [];

function check(name, ok, detail = '') {
  if (ok) notes.push(`PASS  ${name}${detail ? '  ' + detail : ''}`);
  else failures.push(`FAIL  ${name}${detail ? '  ' + detail : ''}`);
}

const sha256 = b => crypto.createHash('sha256').update(b).digest('hex').toUpperCase();
const decHdr = s => { const h = Buffer.from(s.slice(0, 0x40)); encCipher(h, 0, h.length, KS_KEY); return h; };
const decEntry = (s, i) => { const e = Buffer.from(s.slice(0x40 + i * 0x50, 0x40 + (i + 1) * 0x50)); encCipher(e, 0, e.length, KS_KEY); return e; };

const DESC = Buffer.from([
  0x05, 0x01, 0x09, 0x06, 0xa1, 0x01, 0x85, 0x02,
  0x05, 0x07, 0x19, 0xe0, 0x29, 0xe7, 0x15, 0x00,
  0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
  0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
  0x95, 0x04, 0x75, 0x08, 0x15, 0x00, 0x26, 0xff, 0x00,
  0x05, 0x07, 0x19, 0x00, 0x2a, 0xff, 0x00, 0x81, 0x00,
  0x05, 0x0c, 0x15, 0x00, 0x26, 0xff, 0x03,
  0x19, 0x00, 0x2a, 0xff, 0x03,
  0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
  0xc0,
]);

function expectedPatches() {
  const p = defaultPatches().filter(([n]) => n !== 'T1 Left BrightDown' && n !== 'T1 Right BrightUp');
  p.push(['PnP VID', 0x44ae, Buffer.from([0x00, 0xfb, 0x71, 0x01])]);
  p.push(['PnP PID', 0x44c0, Buffer.from([0x00, 0xfb, 0x43, 0x04])]);
  p.push(['PnP NOP', 0x44ce, Buffer.from([0x00, 0x00])]);
  p.push(['Left Consumer', 0x22908, Buffer.from([0x04, 0x00, 0x70, 0x00, 0x00, 0x00])]);
  p.push(['Right Consumer', 0x2290e, Buffer.from([0x04, 0x00, 0x6f, 0x00, 0x00, 0x00])]);
  p.push(['Descriptor length', 0xabbe, Buffer.from([0x30, 0x74])]);
  p.push(['Descriptor pointer', 0xabc2, Buffer.from([0xc1, 0xff, 0x50, 0x34, 0xe2, 0x01])]);
  p.push(['Consumer store', 0xc7c6, Buffer.from([0x38, 0x59])]);
  p.push(['Descriptor blob', 0x23330, DESC]);
  return p;
}

function parseHid(desc) {
  let inputBits = 0, outputBits = 0, reportId = 0, reportSize = 0, reportCount = 0;
  let usagePage = 0, consumer = false;
  for (let i = 0; i < desc.length;) {
    const p = desc[i++];
    const size = (p & 3) === 3 ? 4 : (p & 3);
    const type = (p >> 2) & 3;
    const tag = (p >> 4) & 15;
    let v = 0;
    for (let j = 0; j < size; j++) v |= desc[i + j] << (8 * j);
    i += size;
    if (type === 1) {
      if (tag === 0) usagePage = v;
      if (tag === 7) reportSize = v;
      if (tag === 9) reportCount = v;
      if (tag === 8) reportId = v;
    } else if (type === 0) {
      if (tag === 8) {
        inputBits += reportSize * reportCount;
        if (usagePage === 0x0c) consumer = true;
      }
      if (tag === 9) outputBits += reportSize * reportCount;
    }
  }
  return { inputBits, outputBits, reportId, consumer };
}

check('UFW size', cand.length === 645184);
check('original size', orig.length === cand.length);
const hdr = decHdr(cand);
const n = hdr.readUInt16LE(8);
check('entry count', n === 8);
check('UFW total length', hdr.readUInt32LE(4) === cand.length);
check('UFW header CRC', crc16(hdr.slice(2, 0x40)) === hdr.readUInt16LE(0));
check('UFW list CRC', crc16(cand.slice(0x40, 0x40 + n * 0x50)) === hdr.readUInt16LE(2));
const e0 = decEntry(cand, 0);
check('entry0 flash CRC', e0.readUInt32LE(4) === crc16(cand.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE)));
check('entry0 off/size', e0.readUInt32LE(8) === 0x400 && e0.readUInt32LE(0xc) === 0x34000);

const cf = Buffer.from(cand.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
const of = Buffer.from(orig.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
const cd = Buffer.from(cf);
const od = Buffer.from(of);
sfcCipher(cd, APP_AREA_START, APP_AREA_END - APP_AREA_START, APP_AREA_START, CHIPKEY);
sfcCipher(od, APP_AREA_START, APP_AREA_END - APP_AREA_START, APP_AREA_START, CHIPKEY);

check('app data CRC', crc16(cd.slice(APP_OFF, APP_OFF + APP_SIZE)) === cd.readUInt16LE(0x2022));
check('JLFS entry hdr CRC', crc16(cd.slice(0x2022, 0x2040)) === cd.readUInt16LE(0x2020));
check('JLFS area CRC', crc16(cd.slice(0x2020, 0x2020 + 0x30c7a)) === cd.readUInt16LE(0x2002));
check('JLFS area hdr CRC', crc16(cd.slice(0x2002, 0x2020)) === cd.readUInt16LE(0x2000));

const patches = expectedPatches();
let bad = [];
for (const [nm, off, bytes] of patches) {
  const got = cd.slice(APP_OFF + off, APP_OFF + off + bytes.length);
  if (!got.equals(bytes)) bad.push(`${nm}@0x${off.toString(16)}`);
}
check('expected patches', bad.length === 0, bad.join(','));
check('descriptor bytes', cd.slice(APP_OFF + 0x23330, APP_OFF + 0x23330 + 67).equals(DESC));
const hid = parseHid(DESC);
check('descriptor Report ID 2', hid.reportId === 2);
check('descriptor input 8B', hid.inputBits === 64);
check('descriptor output 0B', hid.outputBits === 0);
check('descriptor Consumer page', hid.consumer);

const allowed = new Set();
for (let i = 0x2000; i < 0x2024; i++) allowed.add(i);
for (const [, off, bytes] of patches) for (let i = 0; i < bytes.length; i++) allowed.add(APP_OFF + off + i);
let unexpected = [];
for (let i = APP_AREA_START; i < APP_AREA_END; i++) {
  if (cd[i] !== od[i] && !allowed.has(i)) { unexpected.push(`0x${i.toString(16)}`); if (unexpected.length >= 20) break; }
}
check('no unexpected app changes', unexpected.length === 0, unexpected.join(','));

let rawDiff = [];
for (let i = 0; i < FLASH_SIZE; i++) {
  if ((i < APP_AREA_START || i >= APP_AREA_END) && cf[i] !== of[i]) { rawDiff.push(`0x${i.toString(16)}`); if (rawDiff.length >= 20) break; }
}
check('app area outside unchanged', rawDiff.length === 0, rawDiff.join(','));

let nonFF = [];
for (let i = APP_AREA_END; i < FLASH_SIZE; i++) if (cf[i] !== 0xff) nonFF.push(`0x${i.toString(16)}`);
check('tail 0xFF', nonFF.length === 0, nonFF.join(','));

console.log('original SHA-256 :', sha256(orig));
console.log('candidate SHA-256:', sha256(cand));
console.log('');
for (const x of notes) console.log(x);
for (const x of failures) console.log(x);
console.log('');
console.log(failures.length === 0 ? 'AUDIT RESULT: PASS' : `AUDIT RESULT: FAIL (${failures.length})`);
process.exitCode = failures.length === 0 ? 0 : 1;
