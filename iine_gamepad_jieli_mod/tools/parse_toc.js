const fs = require('fs');
const { descramble, hexdump } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const dec = descramble(buf, 0, 0x600);
console.log(hexdump(dec.slice(0x400, 0x600), 0x400));
console.log('');
// parse 32-byte entries whose name field is printable
for (let off = 0x400; off < 0x600; off += 0x20) {
  const e = dec.slice(off, off + 0x20);
  const name = e.slice(0x10, 0x20).toString('latin1').replace(/\0+$/, '');
  if (!/^[\x20-\x7e]+$/.test(name)) continue;
  console.log('entry @0x' + off.toString(16));
  console.log('  type/flag : 0x' + e[0].toString(16) + ' 0x' + e[1].toString(16));
  console.log('  crc16     : 0x' + e.readUInt16LE(2).toString(16).padStart(4,'0'));
  console.log('  offset    : 0x' + e.readUInt32LE(4).toString(16));
  console.log('  size      : 0x' + e.readUInt32LE(8).toString(16), '=', e.readUInt32LE(8));
  console.log('  index     : 0x' + e.readUInt32LE(12).toString(16));
  console.log('  name      : ' + JSON.stringify(name));
}
