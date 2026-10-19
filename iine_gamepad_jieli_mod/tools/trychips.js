const fs = require('fs');
const { doe } = require('./mkey.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const keys = [0x16ef, 0x68af, 0x6b7c, 0xffff, 0x0000];
const starts = [0x2400, 0x2520, 0x19f10, 0x4b70, 0x10784];
for (const start of starts) {
  console.log('\n=== @0x' + start.toString(16) + ' raw: ' + buf.slice(start, start+16).toString('hex'));
  for (const key of keys) {
    for (const [am, addr] of [['start', start], ['zero', 0]]) {
      const d = doe(key, buf.slice(start, start+64), addr);
      const s = d.toString('latin1');
      const printable = [...d].filter(b=>b>=0x20&&b<0x7f).length;
      const lz4 = d[0]===0x04 && d[1]===0x22 && d[2]===0x4d && d[3]===0x18;
      console.log('  key=0x'+key.toString(16).padStart(4,'0')+' addr='+am.padEnd(5)+' print='+String(printable).padStart(2)+'  '+d.slice(0,24).toString('hex')+(lz4?'  <LZ4!>':''));
    }
  }
}
