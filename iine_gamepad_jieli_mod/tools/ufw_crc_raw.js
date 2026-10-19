const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const raw = buf.slice(0,0x2c0);
function crc16(data, init=0){ let c=init&0xffff; for(const b of data){ c^=(b<<8); for(let i=0;i<8;i++) c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff; } return c&0xffff; }
function crc16r(data, init=0){ let c=init&0xffff; for(const b of data){ c^=b; for(let i=0;i<8;i++) c=(c&1)?((c>>>1)^0x8408)&0xffff:(c>>>1); } return c&0xffff; }
function crc32(data){ let c=0xffffffff; for(const b of data){ c^=b; for(let i=0;i<8;i++) c=(c&1)?((c>>>1)^0xedb88320):(c>>>1); } return (c^0xffffffff)>>>0; }
const res=[];
for(let a=0;a<=0x10;a++) for(let b=0x20;b<=0x2c0;b++){
  const d=raw.slice(a,b);
  for(const [nm,c] of [['c16-0',crc16(d,0)],['c16-ff',crc16(d,0xffff)],['c16r-0',crc16r(d,0)],['c16r-ff',crc16r(d,0xffff)],['c32',crc32(d)]]) {
    if(c===0xb9c1) res.push(nm+'=H(raw) ['+a+','+b+')');
    if(c===0x41c9) res.push(nm+'=L(raw) ['+a+','+b+')');
    if(c===0x41c9b9c1) res.push(nm+'=U32(raw) ['+a+','+b+')');
  }
}
console.log(res.slice(0,40).join('\n')||'no raw match');
