const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const magicOff = buf.lastIndexOf(Buffer.from('JLUFW','ascii'));
console.log('JLUFW magic at offset 0x' + magicOff.toString(16));
console.log('file size 0x' + buf.length.toString(16));
// dump 384 bytes before end, 32 per line, with ascii
const start = buf.length - 256;
for (let off = start; off < buf.length; off += 16) {
  const hex = Array.from(buf.slice(off, off+16)).map(b=>b.toString(16).padStart(2,'0')).join(' ');
  const asc = Array.from(buf.slice(off, off+16)).map(b=> (b>=32&&b<127)?String.fromCharCode(b):'.').join('');
  console.log(off.toString(16).padStart(6,'0'), hex.padEnd(47), asc);
}
