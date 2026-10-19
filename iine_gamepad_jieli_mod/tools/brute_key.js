const fs = require('fs');
const { step32 } = require('./doe32.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const prologue = Buffer.from([0x05,0x08,0x16,0x08,0x27,0x08]);
function ksBytes(seed, n) {
  const out = Buffer.alloc(n); let s = seed >>> 0;
  for (let i=0;i<n;i++){ out[i]=s&0xff; s=step32(s); }
  return out;
}
const starts = [0x2520, 0x2400, 0x49b00, 0x49c20, 0x34780, 0x348a0];
for (const start of starts) {
  for (const [mode, addrBase] of [['flash', start], ['xip', 0x1e00000 + start], ['zero', 0]]) {
    const data = buf.slice(start, start+6);
    let found = [];
    for (let key=0; key<=0xffff; key++) {
      const seed = (key ^ (addrBase >>> 2)) >>> 0;
      const ks = ksBytes(seed, 6);
      let ok = true;
      for (let i=0;i<6;i++) if ((data[i]^ks[i]) !== prologue[i]) { ok=false; break; }
      if (ok) found.push(key);
    }
    if (found.length) {
      console.log('MATCH start=0x'+start.toString(16)+' addr='+mode+' keys='+found.map(k=>'0x'+k.toString(16)).join(','));
    }
  }
}
console.log('done');
