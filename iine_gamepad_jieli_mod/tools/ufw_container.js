const fs = require('fs');
const { KS } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function dec(off,len){ const o=Buffer.alloc(len); for(let i=0;i<len;i++) o[i]=buf[off+i]^KS[i&31]; return o; }
const h = dec(0,0x40);
console.log('=== container header (descrambled 0..0x40) ===');
console.log(h.toString('hex'));
console.log('u16@0x00=', h.readUInt16LE(0).toString(16), 'u16@0x02=', h.readUInt16LE(2).toString(16));
console.log('u32@0x04=', h.readUInt32LE(4), '=0x'+h.readUInt32LE(4).toString(16));
console.log('u16@0x08=', h.readUInt16LE(8), 'u16@0x0a=', h.readUInt16LE(10), 'u16@0x0c=', h.readUInt16LE(12), 'u16@0x0e=', h.readUInt16LE(14));
console.log('name@0x10=', JSON.stringify(h.slice(0x10,0x20).toString('latin1')));
console.log('\n=== entries: 0x40..0x2A0 as 0x50-byte groups (descrambled) ===');
const e = dec(0x40, 0x50*8);
for (let i=0;i<8;i++){
  const g = e.slice(i*0x50,(i+1)*0x50);
  console.log('entry '+i+':');
  for (let o=0;o<0x50;o+=16){ console.log('  +'+o.toString(16).padStart(2,'0'), g.slice(o,o+16).toString('hex'), JSON.stringify(g.slice(o,o+16).toString('latin1').replace(/[^\x20-\x7e]/g,'.'))); }
}
