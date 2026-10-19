const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
console.log('=== region 0x3ab00-0x3ad80 (config strings) ===');
for (let off = 0x3ab00; off < 0x3ad80; off += 32) {
  const hex = Array.from(buf.slice(off, off+32)).map(b=>b.toString(16).padStart(2,'0')).join(' ');
  const asc = Array.from(buf.slice(off, off+32)).map(b=> (b>=32&&b<127)?String.fromCharCode(b):'.').join('');
  console.log(off.toString(16), hex); console.log('       ', asc);
}
