// v25: Based on v24 (Fast BLE Reconnect + Custom Keymap + Composite Descriptor)
// + Anti-Ghosting Lockout Cooldown on Sleep (A+B Chord):
//
// Root cause of misoperation upon A+B sleep:
// 1. When user releases fingers from A+B chord, human fingers rarely lift at the exact microsecond.
//    One button releases ~50-150ms before the other, leaving a single key pressed momentarily.
//    Under standard logic, the remaining key fires an accidental PageUp/PageDown.
// 2. Kindle e-ink display refresh and suspend transition takes ~1.5-2.0 seconds.
//    Any input event received during this window can wake Kindle back up or turn pages.
//
// Root cause of disconnect bug in initial v25 attempt:
// - In JieLi AC6321A, executable .text ends at 0x2DEA6 (VMA 0x1E2DFC6).
// - Offset 0x2DEA8 onwards is the Flash load image of RAM .data copied to RAM 0x0840 on boot.
// - Injecting code at 0x2E100 corrupted the BLE stack connection structures in RAM 0x0A98!
// - Fix: Place the lockout state machine strictly within .text inside the verified 70-byte
//   unused keyboard descriptor slot at 0x2260A (VMA 0x1E2272A), exactly where v24 trampoline lived.
// - RAM .data image (0x2DEA8 onwards) remains 100% byte-for-byte identical to stock firmware.
//
// Solution (v25 State Machine, 64 bytes @0x2260A):
// - When A+B is first pressed simultaneously (r0 & 3 == 3):
//   1. Trigger Virtual Key 2 (Consumer Power 0x0030) on rising edge.
//   2. Set lockout deadline = now + 2500ms (from hardware ms timer @0x2ADC).
// - During the 2.5s lockout window:
//   - All input is completely suppressed (r0 = 0).
//   - The Power key is immediately released on frame 1 (clean short tap).
//   - Any asynchronous finger release gap is swallowed into 0.
//   - Any accidental touches during Kindle screen-off are ignored.
// - Key-up release latch:
//   - Even after 2.5s, input remains suppressed until both A and B are fully released (r0 & 3 == 0).
//   - Normal reading (A, B, D-pad) operates with 0ms latency and 0 delay once unlocked.

const fs = require('fs');
const { build, defaultPatches } = require('./build_kindle_click.js');

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

if (COMPOSITE_KB.length !== 87) throw new Error('descriptor length');

// Chord lockout state machine routine at VMA 0x1E2272A (app.bin 0x2260A, 64 bytes)
const CHORD_LOCKOUT_64 = Buffer.from([
  0x7d, 0x01,                         // [--sp] = {rets, r13-r4}
  0x14, 0x08,                         // r4 = r1 (save output buffer pointer)
  0x22, 0xfd, 0x03, 0x09,             // r2 = r0 & 3 (isolate A and B keys)
  0x05, 0xfb, 0x94, 0x2b,             // r5 = 0x2B94 (RAM BSS lockout deadline)
  0x03, 0xfb, 0xdc, 0x2a,             // r3 = 0x2ADC (hardware millisecond timer)
  0x06, 0x4a,                         // r6 = [r5 + 0] (read lockout deadline)
  0x07, 0x2c,                         // if (r6 == 0) goto check_chord (+14 -> 0x22)
  0x01, 0x46,                         // r1 = [r3 + 0] (now)
  0xa1, 0xb3,                         // r1 = r1 - r6 (now - deadline)
  0x01, 0xe0, 0x0b, 0xb0,             // ifs (r1 < 0) goto suppress (+22 -> 0x32)
  0x0a, 0x34,                         // if (r2 != 0) goto suppress (+20 -> 0x32, release latch)
  0x01, 0x70,                         // r1 = 0
  0x09, 0x4a,                         // [r5 + 0] = r1 (expired & released: clear lockout)
  // check_chord (0x22):
  0x32, 0xe0, 0x07, 0x10,             // if (r2 != 3) goto body (+14 -> 0x34)
  0x01, 0x46,                         // r1 = [r3 + 0] (now)
  0x11, 0xfc, 0xc4, 0x09,             // r1 = r1 + 2500 (set 2500ms deadline)
  0x09, 0x4a,                         // [r5 + 0] = r1 (store deadline)
  0x40, 0x70,                         // r0 = 4 (Virtual Key 2: Consumer Power)
  0x01, 0x18,                         // goto body (+2 -> 0x34)
  // suppress (0x32):
  0x00, 0x70,                         // r0 = 0 (suppress input)
  // body (0x34):
  0x0b, 0x08,                         // r11 = r0
  0x01, 0x70,                         // r1 = 0
  0xc3, 0xff, 0x20, 0xc8, 0xe0, 0x01, // r3 = 0x1E0C820
  0x93, 0x00,                         // goto r3
]);

if (CHORD_LOCKOUT_64.length !== 64) throw new Error('CHORD_LOCKOUT_64 length error');

const patches = defaultPatches().filter(
  ([n]) => n !== 'T1 Left BrightDown' &&
           n !== 'T1 Right BrightUp' &&
           n !== 'T1 A PageDown' &&
           n !== 'T1 B PageUp' &&
           n !== 'T1 X Power' &&
           n !== 'T1 Y Power' &&
           n !== 'T1 Up PageUp' &&
           n !== 'T1 Down PageDown'
);

// PnP ID
patches.push(['PnP VID', 0x44ae, Buffer.from([0x00, 0xfb, 0x71, 0x01])]);
patches.push(['PnP PID', 0x44c0, Buffer.from([0x00, 0xfb, 0x43, 0x04])]);
patches.push(['PnP NOP', 0x44ce, Buffer.from([0x00, 0x00])]);

// Key map
patches.push(['A -> PageUp', 0x2289c, Buffer.from([0x02, 0x00, 0x00, 0x4b, 0x00, 0x00])]);
patches.push(['B -> PageDown', 0x228a2, Buffer.from([0x02, 0x00, 0x00, 0x4e, 0x00, 0x00])]);
patches.push(['Virtual chord key -> Consumer Power', 0x228a8, Buffer.from([0x04, 0x00, 0x30, 0x00, 0x00, 0x00])]);
patches.push(['Y disabled', 0x228ae, Buffer.from([0x00, 0x00, 0x00, 0x00, 0x00, 0x00])]);
patches.push(['D-pad up -> PageDown', 0x228fc, Buffer.from([0x02, 0x00, 0x00, 0x4e, 0x00, 0x00])]);
patches.push(['D-pad down -> PageUp', 0x22902, Buffer.from([0x02, 0x00, 0x00, 0x4b, 0x00, 0x00])]);
patches.push(['D-pad left -> Brightness Down', 0x22908, Buffer.from([0x04, 0x00, 0x70, 0x00, 0x00, 0x00])]);
patches.push(['D-pad right -> Brightness Up', 0x2290e, Buffer.from([0x04, 0x00, 0x6f, 0x00, 0x00, 0x00])]);

// v21b working descriptor
patches.push(['KB descriptor length 87', 0xabbe, Buffer.from([0x70, 0x75])]);
patches.push(['KB descriptor pointer 0x1E23450', 0xabc2, Buffer.from([0xc1, 0xff, 0x50, 0x34, 0xe2, 0x01])]);
patches.push(['Consumer usage -> agg+38', 0xc7c6, Buffer.from([0x38, 0x59])]);
patches.push(['Composite descriptor @0x23330', 0x23330, COMPOSITE_KB]);

// v25 Chord Lockout Routine @0x2260A (VMA 0x1E2272A, within .text unused kb descriptor slot)
patches.push(['Chord lockout routine @0x2260A', 0x2260a, CHORD_LOCKOUT_64]);
patches.push(['Aggregate builder -> chord lockout at 0x1E2272A', 0xc6f8, Buffer.from([0xc2, 0xff, 0x2a, 0x27, 0xe2, 0x01, 0x92, 0x00])]);

// --- v24 Fast BLE Reconnection Optimizations ---
// 1. Fast Advertising Interval: 800 (500ms) -> 32 (20ms)
patches.push(['ADV interval 20ms', 0x04c90, Buffer.from([0x20, 0x00])]);
// 2. Debug log parameter: 800 -> 32 for printf
patches.push(['ADV interval log 20ms', 0x04ca0, Buffer.from([0x20, 0x00])]);
// 3. Bypass DIRECT_ADV and immediately enter connectable undirected ADV_IND
patches.push(['ADV bypass DIRECT_ADV to ADV_IND', 0x04cd8, Buffer.from([0x00])]);

const outPath = process.argv[3] || 'outputs/IINE_Kindle_Click_v25_SleepLockout.ufw';
const r = build(
  process.argv[2] || 'analysis/firmware/L1161 L1162.ufw',
  outPath,
  { patches }
);
console.log(JSON.stringify({ outPath, descriptorLength: COMPOSITE_KB.length, chordLockoutLength: CHORD_LOCKOUT_64.length, result: r }, null, 2));
