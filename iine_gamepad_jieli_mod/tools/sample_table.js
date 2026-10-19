const fs = require('fs');
const { KS } = require('./descramble.js');
const base='C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/';
const names=['IINE_bisect_Z_nopatch','IINE_bisect_A_dataonly','IINE_bisect_B_noredirect','IINE_bisect_I','IINE_bisect_K','IINE_bisect_M','IINE_bisect_N','IINE_bisect_P','IINE_bisect_R','IINE_Kindle_Click_Mod_v1.0','IINE_Kindle_Click_Mod_v1.2','IINE_Kindle_Click_Mod_v1.5','IINE_Kindle_Click_Mod_v1.7'];
function hdr(b){const o=Buffer.alloc(0x40);for(let i=0;i<0x40;i++)o[i]=b[i]^KS[i&31];return o;}
function crc16(d){let c=0;for(const b of d){c^=(b<<8);for(let i=0;i<8;i++)c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff;}return c&0xffff;}
console.log('name'.padEnd(34),'hdrCRC','listCRC','entry0CRC','flashCRC');
for(const n of names){
  const p=base+n+'.ufw';
  if(!fs.existsSync(p)){console.log(n,'MISSING');continue;}
  const b=fs.readFileSync(p); const h=hdr(b);
  // entry0 +0x04 (descramble per entry)
  const g=Buffer.alloc(0x50); for(let j=0;j<0x50;j++) g[j]=b[0x40+j]^KS[j&31];
  const e0=g.readUInt32LE(4);
  const flash=b.slice(0x400,0x34400);
  console.log(n.padEnd(34), '0x'+h.readUInt16LE(0).toString(16).padStart(4,'0'), '0x'+h.readUInt16LE(2).toString(16).padStart(4,'0'), '0x'+e0.toString(16).padStart(8,'0'), '0x'+crc16(flash).toString(16));
}
