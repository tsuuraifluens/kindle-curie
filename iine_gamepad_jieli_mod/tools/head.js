const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
console.log('=== first 512 bytes ===');
for (let off = 0; off < 512; off += 16) {
  const hex = Array.from(buf.slice(off, off+16)).map(b=>b.toString(16).padStart(2,'0')).join(' ');
  const asc = Array.from(buf.slice(off, off+16)).map(b=> (b>=32&&b<127)?String.fromCharCode(b):'.').join('');
  console.log(off.toString(16).padStart(6,'0'), hex.padEnd(47), asc);
}
console.log('=== first 64 u32 LE ===');
for (let i=0;i<64;i++) {
  const v = buf.readUInt32LE(i*4);
  console.log((i*4).toString(16).padStart(4,'0'), '0x'+v.toString(16).padStart(8,'0'), v);
}
