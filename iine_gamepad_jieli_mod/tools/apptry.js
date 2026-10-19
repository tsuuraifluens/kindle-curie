const fs = require('fs');
const { doe } = require('./mkey.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const starts = [0x19f10, 0x4b70, 0x2400, 0x10784];
for (const start of starts) {
  console.log('\n=== app candidate @0x' + start.toString(16) + ' ===');
  console.log('raw first32:', buf.slice(start, start+32).toString('hex'));
  for (const [label, key, addr] of [
    ['keyFFFF addr=start', 0xffff, start],
    ['keyFFFF addr=0', 0xffff, 0],
    ['key0000 addr=start', 0x0000, start],
    ['key0000 addr=0', 0x0000, 0],
    ['key3110 addr=start', 0x3110, start],
  ]) {
    const d = doe(key, buf.slice(start, start+64), addr);
    const lz4 = d.slice(0,4).toString('hex') === '04422d18';
    console.log('  ' + label.padEnd(20) + ' ' + d.slice(0,32).toString('hex') + (lz4 ? '  <LZ4!>' : ''));
  }
}
