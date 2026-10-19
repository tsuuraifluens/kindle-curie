const fs = require('fs');
const { KS } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function decAt(off,len,phase=0){ const o=Buffer.alloc(len); for(let i=0;i<len;i++) o[i]=buf[off+i]^KS[(i+phase)&31]; return o; }
for (let i=0;i<8;i++){
  const off = 0x40 + i*0x50;
  const g = decAt(off,0x50,0);
  console.log('=== entry '+i+' @0x'+off.toString(16)+' ===');
  for (let o=0;o<0x50;o+=16) console.log('  +'+o.toString(16).padStart(2,'0'), g.slice(o,o+16).toString('hex'), JSON.stringify(g.slice(o,o+16).toString('latin1').replace(/[^\x20-\x7e]/g,'.')));
}
