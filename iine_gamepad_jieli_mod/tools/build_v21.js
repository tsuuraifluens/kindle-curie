// v21: composite keyboard + Consumer in one Report ID 2 / one collection.
//
// v20 used the separate Consumer Report ID 5 and the Kindle ignored it.
// v21 keeps the v20 type-4 key entries, but:
//   1. relocates the keyboard descriptor to free app flash and replaces the
//      last two key bytes with a 16-bit Consumer usage field;
//   2. redirects the type-4 handler to write usage1 into keyboard key slots
//      5/6 (aggregate +38) instead of the separate Consumer buffer (+28).
//
// The keyboard report builder is unchanged: it already copies all 6 key
// bytes (aggregate +34..39) into the 8-byte report body.
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

if (COMPOSITE_KB.length !== 87) {
  throw new Error(`composite descriptor length ${COMPOSITE_KB.length}, expected 87`);
}

const patches = defaultPatches().filter(
  ([name]) => name !== 'T1 Left BrightDown' && name !== 'T1 Right BrightUp'
);

patches.push(['PnP direct VID=0x0171', 0x44ae, Buffer.from([0x00, 0xfb, 0x71, 0x01])]);
patches.push(['PnP direct PID=0x0443', 0x44c0, Buffer.from([0x00, 0xfb, 0x43, 0x04])]);
patches.push(['PnP helper call NOP', 0x44ce, Buffer.from([0x00, 0x00])]);

// Keep v20 Consumer-type table entries.
patches.push(['T1 Left Consumer 0x0070', 0x22908, Buffer.from([0x04, 0x00, 0x70, 0x00, 0x00, 0x00])]);
patches.push(['T1 Right Consumer 0x006F', 0x2290e, Buffer.from([0x04, 0x00, 0x6f, 0x00, 0x00, 0x00])]);

// Descriptor selector: idx 8 -> new 87-byte composite keyboard descriptor.
patches.push(['KB descriptor length 87', 0xabbe, Buffer.from([0x70, 0x75])]);
patches.push(['KB descriptor pointer 0x1E31A60', 0xabc2, Buffer.from([0xc1, 0xff, 0x60, 0x1a, 0xe3, 0x01])]);

// Consumer handler: usage1 goes to keyboard key slots 5/6 (agg +38).
patches.push(['Consumer usage -> agg+38', 0xc7c6, Buffer.from([0x38, 0x59])]);

// New descriptor lives in the zero-filled app-area tail.
patches.push(['Composite keyboard descriptor @0x31940', 0x31940, COMPOSITE_KB]);

const outPath = process.argv[3] || 'outputs/IINE_Kindle_Click_v21_Composite.ufw';
const r = build(
  process.argv[2] || 'analysis/firmware/L1161 L1162.ufw',
  outPath,
  { patches }
);
console.log(JSON.stringify({
  outPath,
  descriptorLength: COMPOSITE_KB.length,
  patches: patches.map(([n, o, b]) => [n, '0x' + o.toString(16), [...b].map(x => x.toString(16).padStart(2, '0')).join(' ')]),
  result: r,
}, null, 2));
