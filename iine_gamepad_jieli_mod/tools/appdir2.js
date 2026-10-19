const fs = require('fs');
const { step32 } = require('./doe32.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function ks(seed,n){ const o=Buffer.alloc(n); let s=seed>>>0; for(let i=0;i<n;i++){o[i]=s&0xff; s=step32(s);} return o; }
function decRange(fileOff, len, vmaStart){
  const out=Buffer.alloc(len);
  for(let i=0;i<len;i+=32){
    const seed=(0x190D ^ ((vmaStart+i)>>>2))>>>0;
    const k=ks(seed,32);
    for(let j=0;j<32&&i+j<len;j++) out[i+j]=buf[fileOff+i+j]^k[j];
  }
  return out;
}
// app_dir_head: UFW 0x2400 (flash 0x2000) -> VMA 0x1E00000
const d = decRange(0x2400, 0x120, 0x1E00000);
console.log('=== app_dir_head (doe32 0x190D, VMA 0x1E00000) ===');
for (let i=0;i<9;i++){
  const g=d.slice(i*0x20,(i+1)*0x20);
  console.log('entry '+i+' @VMA 0x'+(0x1E00000+i*0x20).toString(16));
  console.log('  hex:', g.toString('hex'));
  console.log('  +00 u16='+g.readUInt16LE(0).toString(16)+' +02 u16='+g.readUInt16LE(2).toString(16)+' +04 u32=0x'+g.readUInt32LE(4).toString(16)+' +08 u32=0x'+g.readUInt32LE(8).toString(16)+' +0c u32=0x'+g.readUInt32LE(0xc).toString(16));
  console.log('  name:', JSON.stringify(g.slice(0x10,0x20).toString('latin1').replace(/\0+$/,'')));
}
