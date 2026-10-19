const fs = require('fs');
const { KS } = require('./descramble.js');
const base='C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/';
const orig=fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const v15=fs.readFileSync(base+'IINE_Kindle_Click_Mod_v1.5.ufw');
function c16(d,init,poly){let c=init&0xffff;for(const b of d){c^=(b<<8);for(let i=0;i<8;i++)c=(c&0x8000)?((c<<1)^poly)&0xffff:(c<<1)&0xffff;}return c&0xffff;}
function c16r(d,init,poly){let c=init&0xffff;for(const b of d){c^=b;for(let i=0;i<8;i++)c=(c&1)?((c>>>1)^poly)&0xffff:(c>>>1);}return c&0xffff;}
function sum8(d){let s=0;for(const b of d)s=(s+b)&0xff;return s;}
function sum16(d){let s=0;for(const b of d)s=(s+b)&0xffff;return s;}
function xor8(d){let s=0;for(const b of d)s^=b;return s;}
function prep(b){const h=Buffer.alloc(0x2c0);for(let i=0;i<0x40;i++)h[i]=b[i]^KS[i&31];for(let i=0;i<8;i++)for(let j=0;j<0x50;j++)h[0x40+i*0x50+j]=b[0x40+i*0x50+j]^KS[j&31];return h;}
const DA=prep(orig), DB=prep(v15);
const RA=orig.slice(0,0x2c0), RB=v15.slice(0,0x2c0);
const wAh=DA.readUInt16LE(0), wAl=DA.readUInt16LE(2), wBh=DB.readUInt16LE(0), wBl=DB.readUInt16LE(2);
const algos=[
 ['c16-0-1021',d=>c16(d,0,0x1021)],['c16-ff-1021',d=>c16(d,0xffff,0x1021)],
 ['c16-0-8005',d=>c16(d,0,0x8005)],['c16-ff-8005',d=>c16(d,0xffff,0x8005)],
 ['c16r-0-8408',d=>c16r(d,0,0x8408)],['c16r-ff-8408',d=>c16r(d,0xffff,0x8408)],
 ['c16r-0-a001',d=>c16r(d,0,0xa001)],['c16r-ff-a001',d=>c16r(d,0xffff,0xa001)],
 ['sum8',d=>sum8(d)],['sum16',d=>sum16(d)],['xor8',d=>xor8(d)],
];
const hits=[];
for(const [dn,A,B] of [['dec',DA,DB],['raw',RA,RB]]) {
  for(let a=0;a<=6;a++) for(let b=0x20;b<=0x2c0;b++){
    if(b<=a) continue;
    for(const [nm,f] of algos){
      const ca=f(A.slice(a,b)), cb=f(B.slice(a,b));
      if(ca===wAh&&cb===wBh) hits.push(dn+' HDR '+nm+' ['+a+','+b+')');
      if(ca===wAl&&cb===wBl) hits.push(dn+' LIST '+nm+' ['+a+','+b+')');
    }
  }
}
console.log(hits.slice(0,50).join('\n')||'no match');
