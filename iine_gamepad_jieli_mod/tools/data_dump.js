const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const start = 0x49b00 + 0xE224; // .data LMA
console.log('=== .data @file 0x' + start.toString(16) + ' (XIP 0x1E0E224) ===');
for (let o=start;o<start+0x200;o+=16){
  const c=buf.slice(o,o+16);
  const hex=Array.from(c).map(b=>b.toString(16).padStart(2,'0')).join(' ');
  const asc=Array.from(c).map(b=>(b>=32&&b<127)?String.fromCharCode(b):'.').join('');
  console.log(o.toString(16).padStart(6,'0')+'  '+hex.padEnd(47)+'  '+asc);
}
