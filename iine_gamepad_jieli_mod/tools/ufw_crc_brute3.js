const fs = require('fs');
const { KS } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function crc16(data, init=0, poly=0x1021){ let c=init&0xffff; for(const b of data){ c^=(b<<8); for(let i=0;i<8;i++) c=(c&0x8000)?((c<<1)^poly)&0xffff:(c<<1)&0xffff; } return c&0xffff; }
function crc16r(data, init=0, poly=0x8408){ let c=init&0xffff; for(const b of data){ c^=b; for(let i=0;i<8;i++) c=(c&1)?((c>>>1)^poly)&0xffff:(c>>>1); } return c&0xffff; }
function crc32(data){ let c=0xffffffff; for(const b of data){ c^=b; for(let i=0;i<8;i++) c=(c&1)?((c>>>1)^0xedb88320):(c>>>1); } return (c^0xffffffff)>>>0; }
const full = Buffer.alloc(0x2c0);
for(let i=0;i<0x40;i++) full[i]=buf[i]^KS[i&31];
for(let i=0;i<8;i++) for(let j=0;j<0x50;j++) full[0x40+i*0x50+j]=buf[0x40+i*0x50+j]^KS[j&31];
const want32 = buf.readUInt32LE(0); const wantH=buf.readUInt16LE(0), wantL=buf.readUInt16LE(2);
console.log('stored u32@0=0x'+want32.toString(16)+' u16@0=0x'+wantH.toString(16)+' u16@2=0x'+wantL.toString(16));
const res=[];
for(let a=0;a<=0x10;a++) for(let b=0x20;b<=0x2c0;b++){
  const d=full.slice(a,b);
  if(crc16(d,0)===wantH||crc16(d,0)===wantL) res.push('c16-0 ['+a+','+b+')');
  if(crc16(d,0xffff)===wantH||crc16(d,0xffff)===wantL) res.push('c16-ff ['+a+','+b+')');
  if(crc16r(d,0)===wantH||crc16r(d,0)===wantL) res.push('c16r-0 ['+a+','+b+')');
  if(crc16r(d,0xffff)===wantH||crc16r(d,0xffff)===wantL) res.push('c16r-ff ['+a+','+b+')');
  if(crc32(d)===want32) res.push('c32 ['+a+','+b+')');
}
console.log(res.slice(0,50).join('\n')||'no match');
