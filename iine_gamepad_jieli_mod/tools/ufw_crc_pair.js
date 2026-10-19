const fs = require('fs');
const { KS } = require('./descramble.js');
const base='C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/';
const orig=fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const v15=fs.readFileSync(base+'IINE_Kindle_Click_Mod_v1.5.ufw');
function crc16(data, init=0){ let c=init&0xffff; for(const b of data){ c^=(b<<8); for(let i=0;i<8;i++) c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff; } return c&0xffff; }
function crc16r(data, init=0){ let c=init&0xffff; for(const b of data){ c^=b; for(let i=0;i<8;i++) c=(c&1)?((c>>>1)^0x8408)&0xffff:(c>>>1); } return c&0xffff; }
function prep(b){
  const h=Buffer.alloc(0x2c0);
  for(let i=0;i<0x40;i++) h[i]=b[i]^KS[i&31];
  for(let i=0;i<8;i++) for(let j=0;j<0x50;j++) h[0x40+i*0x50+j]=b[0x40+i*0x50+j]^KS[j&31];
  return h;
}
const A=prep(orig), B=prep(v15);
const wantA_h=A.readUInt16LE(0), wantA_l=A.readUInt16LE(2);
const wantB_h=B.readUInt16LE(0), wantB_l=B.readUInt16LE(2);
console.log('A(orig) hdr=0x'+wantA_h.toString(16)+' list=0x'+wantA_l.toString(16));
console.log('B(v1.5) hdr=0x'+wantB_h.toString(16)+' list=0x'+wantB_l.toString(16));
const algos=[['c16-0',(d)=>crc16(d,0)],['c16-ff',(d)=>crc16(d,0xffff)],['c16r-0',(d)=>crc16r(d,0)],['c16r-ff',(d)=>crc16r(d,0xffff)]];
const hits=[];
for(let a=0;a<=4;a++) for(let b=0x20;b<=0x2c0;b++){
  if(b<=a) continue;
  for(const [nm,f] of algos){
    const ca=f(A.slice(a,b)), cb=f(B.slice(a,b));
    if(ca===wantA_h && cb===wantB_h) hits.push('HDR '+nm+' ['+a+','+b+')');
    if(ca===wantA_l && cb===wantB_l) hits.push('LIST '+nm+' ['+a+','+b+')');
  }
}
console.log(hits.slice(0,40).join('\n')||'no match');
