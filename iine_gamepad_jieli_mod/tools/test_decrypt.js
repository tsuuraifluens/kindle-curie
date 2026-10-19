const fs = require('fs');
const { encBlock, decryptRegion, strings } = require('./app_decrypt.js');
const { KS } = require('./descramble.js');
// 1) validate CRC16 stream == derived KS
const ks = encBlock(0xffff);
console.log('encBlock(0xFFFF) == KS :', ks.equals(KS));
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const regions = [
  ['app_head', 0x2400, 0x100],
  ['ble_ota', 0x24a0, 0x2000],
  ['edr_ota2', 0xe0b4, 0x2000],
  ['ble_app_ota', 0x17840, 0x4000],
  ['uart_user', 0x27fc8, 0x1000],
  ['usb_hid_ota', 0x2bcac, 0x1000],
  ['tail', 0x34700, 0x2000],
];
for (const [name, start, len] of regions) {
  const variants = [
    ['keyFFFF/offset', 0xffff, (o)=>o],
    ['keyFFFF/xip',    0xffff, (o)=>0x01e00000+o],
    ['key0000/offset', 0x0000, (o)=>o],
    ['noseed',         0xffff, ()=>0],
  ];
  console.log('\n=== ' + name + ' @0x' + start.toString(16) + ' ===');
  for (const [label, key, addrOf] of variants) {
    const dec = decryptRegion(buf, start, len, key, addrOf);
    const ss = strings(dec, 8);
    console.log('  ' + label.padEnd(16) + ' strings>=8: ' + String(ss.length).padStart(3) + '  first: ' + (ss[0] ? JSON.stringify(ss[0][1]) : '-'));
  }
}
