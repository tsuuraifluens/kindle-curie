const fs = require('fs');
const { step32 } = require('./doe32.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const fileStart = 0x2520, size = 0x308A8, vmaStart = 0x1E00120;
function ks(seed, n){ const o=Buffer.alloc(n); let s=seed>>>0; for(let i=0;i<n;i++){o[i]=s&0xff; s=step32(s);} return o; }
const dec = Buffer.alloc(size);
for (let i=0;i<size;i+=32) {
  const blockVma = vmaStart + i;
  const seed = (0x190D ^ (blockVma >>> 2)) >>> 0;
  const k = ks(seed, 32);
  for (let j=0;j<32 && i+j<size;j++) dec[i+j] = buf[fileStart+i+j] ^ k[j];
}
fs.writeFileSync('work/app_dec32.bin', dec);
console.log('per-32-block decrypted -> work/app_dec32.bin');
function at(vma, len){ const off=vma-vmaStart; return dec.slice(off, off+len); }
for (const [vma,len,label] of [[0x1E212E6,12,'IINE_reader'],[0x1E212F2,25,'Xbox Controller'],[0x1E224DC,10,'PNP_ID'],[0x1E224A4,13,'DIS Mfg'],[0x1E2292C,58,'HID Reader Desc'],[0x1E00120,56,'entry']]) {
  const b = at(vma,len);
  console.log(label.padEnd(18), '0x'+vma.toString(16), b.toString('hex'), JSON.stringify(b.toString('latin1')));
}
