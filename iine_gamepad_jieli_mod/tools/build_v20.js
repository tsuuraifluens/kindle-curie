// v20: v19 (PnP direct) + Consumer-page brightness entries.
//
// Key-map entry type 4 is the Consumer type used by Table3 (default entry
// 04 00 CD 00 00 00 = Consumer 0x00CD).  Brightness must be Consumer
// 0x006F/0x0070, not Keyboard 0x6F/0x70.
const fs = require('fs');
const { build, defaultPatches } = require('./build_kindle_click.js');

const patches = defaultPatches().filter(
  ([name]) => name !== 'T1 Left BrightDown' && name !== 'T1 Right BrightUp'
);

patches.push(['PnP direct VID=0x0171', 0x44ae, Buffer.from([0x00, 0xfb, 0x71, 0x01])]);
patches.push(['PnP direct PID=0x0443', 0x44c0, Buffer.from([0x00, 0xfb, 0x43, 0x04])]);
patches.push(['PnP helper call NOP', 0x44ce, Buffer.from([0x00, 0x00])]);

// D-pad left/right -> Consumer brightness down/up.
patches.push(['T1 Left Consumer 0x0070', 0x22908, Buffer.from([0x04, 0x00, 0x70, 0x00, 0x00, 0x00])]);
patches.push(['T1 Right Consumer 0x006F', 0x2290e, Buffer.from([0x04, 0x00, 0x6f, 0x00, 0x00, 0x00])]);

const outPath = process.argv[3] || 'outputs/IINE_Kindle_Click_v20_Consumer.ufw';
const r = build(
  process.argv[2] || 'analysis/firmware/L1161 L1162.ufw',
  outPath,
  { patches }
);
console.log(JSON.stringify({ outPath, patches: patches.map(([n, o, b]) => [n, '0x' + o.toString(16), [...b].map(x => x.toString(16).padStart(2, '0')).join(' ')]), result: r }, null, 2));
