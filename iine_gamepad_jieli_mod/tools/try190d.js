const fs = require('fs');
const { doe32 } = require('./doe32.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const start = 0x2520; // app.bin data
for (const [mode, base] of [['flash', 0x2120], ['xip', 0x1e00000+0x120], ['file', start], ['zero', 0]]) {
  const dec = doe32(0x190d, buf.slice(start, start+0x40), base);
  console.log('key=0x190d addr='+mode.padEnd(6)+' =>', dec.toString('hex'), JSON.stringify(dec.toString('latin1')));
}
