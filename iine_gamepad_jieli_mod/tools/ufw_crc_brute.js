const fs = require('fs');
const { KS } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function crc16(data, init=0){ let c=init&0xffff; for(const b of data){ c^=(b<<8); for(let i=0;i<8;i++) c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff; } return c&0xffff; }
function decKs(off,len,phase=0){ const o=Buffer.alloc(len); for(let i=0;i<len;i++) o[i]=buf[off+i]^KS[(i+phase)&31]; return o; }
const hdr = decKs(0,0x40);
const wantH = hdr.readUInt16LE(0), wantL = hdr.readUInt16LE(2);
console.log('want header CRC=0x'+wantH.toString(16)+' list CRC=0x'+wantL.toString(16));
// brute header ranges
for (const init of [0,0xffff]) {
  for (let a=0;a<=4;a++) for (let b=0x20;b<=0x40;b++) {
    const c=crc16(hdr.slice(a,b),init);
    if (c===wantH) console.log('HEADER match: ['+a+','+b+') init=0x'+init.toString(16));
  }
}
// brute list ranges: descramble 0x40..0x2C0 continuously (phase 0) and per-entry
const cont = decKs(0x40,0x280,0);
let per = Buffer.alloc(0x280);
for(let i=0;i<8;i++){ const g=Buffer.alloc(0x50); for(let j=0;j<0x50;j++) g[j]=buf[0x40+i*0x50+j]^KS[j&31]; g.copy(per,i*0x50); }
for (const [name,data] of [['cont',cont],['per',per]]) {
  for (const init of [0,0xffff]) {
    for (let a=0;a<=0x10;a++) for (let b=0x100;b<=0x280;b+=1) {
      const c=crc16(data.slice(a,b),init);
      if (c===wantL) console.log('LIST match('+name+'): ['+a+','+b+') init=0x'+init.toString(16));
    }
  }
}
console.log('done');
