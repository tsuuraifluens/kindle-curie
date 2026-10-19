const fs = require('fs');
const { KS } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function crc16(data, init=0){ let c=init&0xffff; for(const b of data){ c^=(b<<8); for(let i=0;i<8;i++) c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff; } return c&0xffff; }
function crc16r(data, init=0){ let c=init&0xffff; for(const b of data){ c^=b; for(let i=0;i<8;i++) c=(c&1)?((c>>>1)^0x8408)&0xffff:(c>>>1); } return c&0xffff; }
function crc32(data){ let c=0xffffffff; for(const b of data){ c^=b; for(let i=0;i<8;i++) c=(c&1)?((c>>>1)^0xedb88320):(c>>>1); } return (c^0xffffffff)>>>0; }
const full = Buffer.alloc(0x2c0);
for(let i=0;i<0x40;i++) full[i]=buf[i]^KS[i&31];
for(let i=0;i<8;i++) for(let j=0;j<0x50;j++) full[0x40+i*0x50+j]=buf[0x40+i*0x50+j]^KS[j&31];
const want32=full.readUInt32LE(0), wantH=full.readUInt16LE(0), wantL=full.readUInt16LE(2);
console.log('descrambled: u32@0=0x'+want32.toString(16)+' u16@0=0x'+wantH.toString(16)+' u16@2=0x'+wantL.toString(16));
const res=[];
for(let a=0;a<=0x10;a++) for(let b=0x20;b<=0x2c0;b++){
  const d=full.slice(a,b);
  for(const [nm,c] of [['c16-0',crc16(d,0)],['c16-ff',crc16(d,0xffff)],['c16r-0',crc16r(d,0)],['c16r-ff',crc16r(d,0xffff)],['c32',crc32(d)]]) {
    if(c===wantH) res.push(nm+'=H ['+a+','+b+')');
    if(c===wantL) res.push(nm+'=L ['+a+','+b+')');
    if(c===want32) res.push(nm+'=U32 ['+a+','+b+')');
  }
}
console.log(res.slice(0,60).join('\n')||'no match');
