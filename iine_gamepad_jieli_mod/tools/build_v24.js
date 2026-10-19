// v24: Based on v23 (A+B chord sleep + custom keymap + composite descriptor)
// + Fast Kindle BLE Reconnection Optimization:
//
// Root cause of ~1 minute connection delay on boot:
// 1. Factory firmware set advertising interval to 800 (500ms).
//    Kindle's background BLE scan window is only 30ms every 1280ms (~2.3% duty cycle).
//    A 500ms advertising interval aliased heavily with Kindle's 1.28s scan cycle,
//    taking ~50-60 seconds on average before an advertisement landed inside Kindle's 30ms window.
//    Fix: Change adv_interval from 800 (500ms) to 32 (20ms, the SIG BLE min for ADV_IND).
//    With 20ms interval (< 30ms window), EVERY scan window of Kindle receives at least one packet!
// 2. On boot/reconnect with pair_flag set, factory firmware attempted ADV_DIRECT_IND.
//    Kindle ignores directed advertising because it lacks the Amazon Manufacturer Specific Data (05 FF 71 01 04 43).
//    The controller had to wait for directed advertising to time out before falling back to ADV_IND.
//    Fix: Bypass DIRECT_ADV and immediately enter ADV_IND with 20ms interval.

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

// Far trampoline at VMA 0x1E2272A (app.bin 0x2260A), the old keyboard descriptor slot.
const TRAMP = Buffer.from([
  0x7d, 0x01,
  0x14, 0x08,
  0x22, 0xfd, 0x03, 0x09,
  0x32, 0xe0, 0x01, 0x10,
  0x40, 0x70,
  0x0b, 0x08,
  0x01, 0x70,
  0xc3, 0xff, 0x20, 0xc8, 0xe0, 0x01,
  0x93, 0x00,
]);

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

// Chord trampoline and aggregate builder entry redirect
patches.push(['Chord trampoline @0x2260A (old KB descriptor)', 0x2260a, TRAMP]);
patches.push(['Aggregate builder -> far trampoline', 0xc6f8, Buffer.from([0xc2, 0xff, 0x2a, 0x27, 0xe2, 0x01, 0x92, 0x00])]);

// --- v24 Connection Speed Optimizations ---
// 1. Fast Advertising Interval: 800 (500ms) -> 32 (20ms)
//    0x04c90 in app.bin: changes `mov r0, #800` to `mov r0, #32`
patches.push(['ADV interval 20ms', 0x04c90, Buffer.from([0x20, 0x00])]);

// 2. Debug log parameter: 800 -> 32 for printf
//    0x04ca0 in app.bin: changes `mov r1, #800` to `mov r1, #32`
patches.push(['ADV interval log 20ms', 0x04ca0, Buffer.from([0x20, 0x00])]);

// 3. Bypass DIRECT_ADV and immediately enter connectable undirected ADV_IND
//    0x04cd8 in app.bin: changes `jmz r1, #3, +58` to `jmz r1, #0, +58` (always true)
patches.push(['ADV bypass DIRECT_ADV to ADV_IND', 0x04cd8, Buffer.from([0x00])]);

const outPath = process.argv[3] || 'outputs/IINE_Kindle_Click_v24_FastConnect.ufw';
const r = build(
  process.argv[2] || 'analysis/firmware/L1161 L1162.ufw',
  outPath,
  { patches }
);
console.log(JSON.stringify({ outPath, descriptorLength: COMPOSITE_KB.length, trampolineLength: TRAMP.length, result: r }, null, 2));
