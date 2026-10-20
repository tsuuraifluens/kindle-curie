// Audit v25: v24 + Anti-Ghosting Lockout Cooldown on Sleep (A+B Chord)
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

const orig = Buffer.from(fs.readFileSync(process.argv[2] || 'analysis/firmware/L1161 L1162.ufw'));
const cand = Buffer.from(fs.readFileSync(process.argv[3] || 'outputs/IINE_Kindle_Click_v25_SleepLockout.ufw'));
const failures = [];
const notes = [];
const check = (n, ok, d = '') => ok ? notes.push(`PASS  ${n}${d ? '  ' + d : ''}`) : failures.push(`FAIL  ${n}${d ? '  ' + d : ''}`);
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
  0x95, 0x03, 0x75, 0x01, 0x05, 0x08,
  0x25, 0x01, 0x19, 0x01, 0x29, 0x03, 0x91, 0x02,
  0x95, 0x05, 0x75, 0x01, 0x91, 0x01,
  0xc0,
]);

// Chord latch routine at VMA 0x1E2272A (app.bin 0x2260A, 38 bytes)
// Eliminates asynchronous finger lift-off ghosting without any RAM writes or OS timer dependencies:
// - Uses the firmware's native previous key state variable [0x2B40] (maintained by the OS at r14 + 400).
// - When A+B is pressed (r0 & 3 == 3): outputs Virtual Key 2 (Power, r0 = 4). [0x2B40] becomes 4.
// - When fingers lift off asynchronously (e.g. A lifts, B still touching):
//   Because [0x2B40] == 4 and (r0 & 3) != 0, it holds r0 = 4!
//   Firmware sees current key == prev key (4 == 4), so NO PageDown or PageUp is ever emitted!
// - When BOTH keys reach 0: drops r0 = 0, sending clean release and resetting [0x2B40] = 0.
// - Single A / single B presses have [0x2B40] != 4, so they pass through with 0ms latency.
const CHORD_LATCH = Buffer.from([
  0x7d, 0x01,                         // [--sp] = {rets, r13-r4}
  0x14, 0x08,                         // r4 = r1 (save output buffer pointer)
  0x22, 0xfd, 0x03, 0x09,             // r2 = r0 & 3 (isolate A and B keys)
  0x32, 0xe0, 0x06, 0x00,             // if (r2 == 3) goto set_chord (+12 -> 0x18)
  0x03, 0xfb, 0x40, 0x2b,             // r3 = 0x2B40 (native firmware prev_key variable)
  0x03, 0x46,                         // r3 = [r3 + 0]
  0x43, 0xe0, 0x02, 0x10,             // if (r3 != 4) goto body (+4 -> 0x1A)
  0x01, 0x24,                         // if (r2 == 0) goto body (+2 -> 0x1A)
  // set_chord (0x18):
  0x40, 0x70,                         // r0 = 4 (Virtual Key 2: Consumer Power)
  // body (0x1A):
  0x0b, 0x08,                         // r11 = r0
  0x01, 0x70,                         // r1 = 0
  0xc3, 0xff, 0x20, 0xc8, 0xe0, 0x01, // r3 = 0x1E0C820
  0x93, 0x00,                         // goto r3
]);

if (CHORD_LATCH.length !== 38) throw new Error('CHORD_LATCH length error');

function expectedPatches() {
  const p = defaultPatches().filter(([n]) =>
    n !== 'T1 Left BrightDown' && n !== 'T1 Right BrightUp' &&
    n !== 'T1 A PageDown' && n !== 'T1 B PageUp' &&
    n !== 'T1 X Power' && n !== 'T1 Y Power' &&
    n !== 'T1 Up PageUp' && n !== 'T1 Down PageDown'
  );
  p.push(['PnP VID', 0x44ae, Buffer.from([0x00, 0xfb, 0x71, 0x01])]);
  p.push(['PnP PID', 0x44c0, Buffer.from([0x00, 0xfb, 0x43, 0x04])]);
  p.push(['PnP NOP', 0x44ce, Buffer.from([0x00, 0x00])]);
  p.push(['A PageUp', 0x2289c, Buffer.from([0x02, 0x00, 0x00, 0x4b, 0x00, 0x00])]);
  p.push(['B PageDown', 0x228a2, Buffer.from([0x02, 0x00, 0x00, 0x4e, 0x00, 0x00])]);
  p.push(['Virtual Power', 0x228a8, Buffer.from([0x04, 0x00, 0x30, 0x00, 0x00, 0x00])]);
  p.push(['Y disabled', 0x228ae, Buffer.from([0x00, 0x00, 0x00, 0x00, 0x00, 0x00])]);
  p.push(['Up PageDown', 0x228fc, Buffer.from([0x02, 0x00, 0x00, 0x4e, 0x00, 0x00])]);
  p.push(['Down PageUp', 0x22902, Buffer.from([0x02, 0x00, 0x00, 0x4b, 0x00, 0x00])]);
  p.push(['Left Bright-', 0x22908, Buffer.from([0x04, 0x00, 0x70, 0x00, 0x00, 0x00])]);
  p.push(['Right Bright+', 0x2290e, Buffer.from([0x04, 0x00, 0x6f, 0x00, 0x00, 0x00])]);
  p.push(['KB len 87', 0xabbe, Buffer.from([0x70, 0x75])]);
  p.push(['KB ptr', 0xabc2, Buffer.from([0xc1, 0xff, 0x50, 0x34, 0xe2, 0x01])]);
  p.push(['Consumer +38', 0xc7c6, Buffer.from([0x38, 0x59])]);
  p.push(['Descriptor', 0x23330, DESC]);
  p.push(['Chord lockout routine @0x2260A', 0x2260a, CHORD_LATCH]);
  p.push(['Aggregate redirect to 0x1E2272A', 0xc6f8, Buffer.from([0xc2, 0xff, 0x2a, 0x27, 0xe2, 0x01, 0x92, 0x00])]);
  p.push(['ADV interval 20ms', 0x04c90, Buffer.from([0x20, 0x00])]);
  p.push(['ADV interval log 20ms', 0x04ca0, Buffer.from([0x20, 0x00])]);
  p.push(['ADV bypass DIRECT_ADV', 0x04cd8, Buffer.from([0x00])]);
  return p;
}

function parseHid(desc) {
  let inputBits = 0, outputBits = 0, reportId = 0, reportSize = 0, reportCount = 0;
  let usagePage = 0, consumer = false;
  for (let i = 0; i < desc.length;) {
    const p = desc[i++];
    const size = (p & 3) === 3 ? 4 : (p & 3);
    const type = (p >> 2) & 3, tag = (p >> 4) & 15;
    let v = 0;
    for (let j = 0; j < size; j++) v |= desc[i + j] << (8 * j);
    i += size;
    if (type === 1) {
      if (tag === 0) usagePage = v;
      if (tag === 7) reportSize = v;
      if (tag === 9) reportCount = v;
      if (tag === 8) reportId = v;
    } else if (type === 0) {
      if (tag === 8) { inputBits += reportSize * reportCount; if (usagePage === 0x0c) consumer = true; }
      if (tag === 9) outputBits += reportSize * reportCount;
    }
  }
  return { inputBits, outputBits, reportId, consumer };
}

check('UFW size', cand.length === 645184);
const hdr = decHdr(cand);
check('UFW header CRC', crc16(hdr.slice(2, 0x40)) === hdr.readUInt16LE(0));
const numents = hdr.readUInt16LE(8);
const headersize = 0x40 + numents * 0x50;
check('UFW list CRC', crc16(cand.slice(0x40, headersize)) === hdr.readUInt16LE(2));

const e0 = decEntry(cand, 0);
const flashCrc = crc16(cand.slice(FLASH_BASE, FLASH_BASE + 212992));
check('Flash CRC in entry 0', e0.readUInt32LE(4) === flashCrc);

// JLFS checks
const dec = Buffer.from(cand.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
sfcCipher(dec, APP_AREA_START, APP_AREA_END - APP_AREA_START, APP_AREA_START, CHIPKEY);
const appBytes = dec.slice(APP_OFF, APP_OFF + APP_SIZE);
const appCrc = crc16(appBytes);
check('app.bin JLFS data CRC', dec.readUInt16LE(0x2022) === appCrc);
check('app.bin entry header CRC', dec.readUInt16LE(0x2020) === crc16(dec.slice(0x2022, 0x2040)));
check('JLFS area data CRC', dec.readUInt16LE(0x2002) === crc16(dec.slice(0x2020, 0x2020 + 0x30c7a)));
check('JLFS area header CRC', dec.readUInt16LE(0x2000) === crc16(dec.slice(0x2002, 0x2020)));

// Byte-for-byte checks of unmodified regions
const origDec = Buffer.from(orig.slice(FLASH_BASE, FLASH_BASE + FLASH_SIZE));
sfcCipher(origDec, APP_AREA_START, APP_AREA_END - APP_AREA_START, APP_AREA_START, CHIPKEY);
check('Uboot partition identical (0x400..0x2400)', cand.slice(FLASH_BASE, FLASH_BASE + 0x2000).equals(orig.slice(FLASH_BASE, FLASH_BASE + 0x2000)));
check('JLFS app header preamble (0x2004..0x2020)', dec.slice(0x2004, 0x2020).equals(origDec.slice(0x2004, 0x2020)));
check('JLFS app entry metadata (0x2024..0x2040)', dec.slice(0x2024, 0x2040).equals(origDec.slice(0x2024, 0x2040)));
check('Flash trailing partitions (0x34400..EOF)', cand.slice(FLASH_BASE + 0x34000).equals(orig.slice(FLASH_BASE + 0x34000)));

// RAM .data flash load image (0x2DEA8 onwards) MUST be 100% stock identical (BLE stack safety!)
const origDataImage = origDec.slice(APP_OFF + 0x2dea8, APP_OFF + APP_SIZE);
const candDataImage = appBytes.slice(0x2dea8);
check('RAM .data flash load image (0x2DEA8..0x308A8) 100% stock identical (BLE stack safety)', candDataImage.equals(origDataImage));

// Descriptor parsing
const descInApp = appBytes.slice(0x23330, 0x23330 + 87);
check('Descriptor bytes match', descInApp.equals(DESC));
const hid = parseHid(descInApp);
check('HID report size 64 bits (8 bytes)', hid.inputBits === 64);
check('HID Report ID 2', hid.reportId === 2);
check('HID Consumer usage present in KB report', hid.consumer === true);

// Fast connection specifics
const advIntervalVal = appBytes.readUInt16LE(0x04c90);
check('adv interval 20ms (32)', advIntervalVal === 32);
const directBypassVal = appBytes.readUInt8(0x04cd8);
check('direct adv bypass byte (0x00)', directBypassVal === 0x00);

// Chord latch routine check @0x2260A
const chordInApp = appBytes.slice(0x2260a, 0x2260a + CHORD_LATCH.length);
check('Chord latch routine @0x2260A matches', chordInApp.equals(CHORD_LATCH));

// Patches verification
const exp = expectedPatches();
let allPatchesMatch = true;
for (const [name, off, bytes] of exp) {
  const actual = appBytes.slice(off, off + bytes.length);
  if (!actual.equals(bytes)) {
    allPatchesMatch = false;
    failures.push(`FAIL Patch mismatch: ${name} @0x${off.toString(16)} expected ${bytes.toString('hex')} actual ${actual.toString('hex')}`);
  }
}
check('All 23 patches match exactly', allPatchesMatch);

// Disallowed changes: verify all diffs are strictly inside expected patch regions
const allowed = new Set();
for (let i = 0x2000; i < 0x2024; i++) allowed.add(i);
for (const [, off, bytes] of exp) {
  for (let i = 0; i < bytes.length; i++) allowed.add(APP_OFF + off + i);
}

let unexpected = [];
for (let i = APP_AREA_START; i < APP_AREA_END; i++) {
  if (dec[i] !== origDec[i] && !allowed.has(i)) {
    unexpected.push(`0x${i.toString(16)}`);
    if (unexpected.length >= 20) break;
  }
}
check('Zero unexpected modifications in flash', unexpected.length === 0, unexpected.join(','));

console.log('=== V25 AUDIT RESULTS ===');
notes.forEach(n => console.log('  ' + n));
if (failures.length) {
  console.log('\nFAILURES:');
  failures.forEach(f => console.log('  ' + f));
  process.exit(1);
} else {
  console.log(`\nALL ${notes.length} CHECKS PASSED.`);
  console.log('SHA-256:', sha256(cand));
}
