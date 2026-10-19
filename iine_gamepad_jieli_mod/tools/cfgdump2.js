const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
console.log('=== region 0x1600-0x1B00 (binary isd_config) ===');
for (let off = 0x1600; off < 0x1B00; off += 32) {
  const hex = Array.from(buf.slice(off, off+32)).map(b=>b.toString(16).padStart(2,'0')).join(' ');
  const asc = Array.from(buf.slice(off, off+32)).map(b=> (b>=32&&b<127)?String.fromCharCode(b):'.').join('');
  console.log(off.toString(16).padStart(6,'0'), hex.padEnd(95), asc);
}
