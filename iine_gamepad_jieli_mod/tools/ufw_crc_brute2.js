const fs = require('fs');
const { KS } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function crc16(data, init=0){ let c=init&0xffff; for(const b of data){ c^=(b<<8); for(let i=0;i<8;i++) c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff; } return c&0xffff; }
function decKsCont(off,len){ const o=Buffer.alloc(len); for(let i=0;i<len;i++) o[i]=buf[off+i]^KS[i&31]; return o; }
// build full descrambled container 0..0x2C0: header continuous, entries per-entry reset
const full = Buffer.alloc(0x2c0);
decKsCont(0,0x40).copy(full,0);
for(let i=0;i<8;i++){ for(let j=0;j<0x50;j++) full[0x40+i*0x50+j]=buf[0x40+i*0x50+j]^KS[j&31]; }
const wantH=0xb9c1, wantL=0x41c9;
const found=[];
for (const init of [0,0xffff]) {
  for (let a=0;a<=0x10;a++) for (let b=0x20;b<=0x2c0;b++) {
    const c=crc16(full.slice(a,b),init);
    if (c===wantH) found.push('H ['+a+','+b+') init=0x'+init.toString(16));
    if (c===wantL) found.push('L ['+a+','+b+') init=0x'+init.toString(16));
  }
}
console.log(found.slice(0,40).join('\n') || 'no match');
// also try zeroing crc fields
const z = Buffer.from(full); z[0]=0; z[1]=0;
for (const init of [0,0xffff]) { for (let b=0x20;b<=0x2c0;b++){ const c=crc16(z.slice(0,b),init); if(c===wantH) console.log('H(zerofield) [0,'+b+') init=0x'+init.toString(16)); } }
const z2 = Buffer.from(full); z2[2]=0; z2[3]=0;
for (const init of [0,0xffff]) { for (let b=0x40;b<=0x2c0;b++){ const c=crc16(z2.slice(0,b),init); if(c===wantL) console.log('L(zerofield) [0,'+b+') init=0x'+init.toString(16)); } }
console.log('done');
