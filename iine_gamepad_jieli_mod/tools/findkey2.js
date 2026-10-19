const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function scan(label, pred) {
  const hits = [];
  for (let i = 0; i + 6 < buf.length; i++) if (pred(i)) hits.push(i);
  console.log(label + ': ' + hits.length);
  for (const h of hits.slice(0, 25)) {
    const ptr = buf.readUInt32LE(h+2);
    console.log('  @0x' + h.toString(16).padStart(6,'0') + ' num=' + buf[h+1] + ' ptr=0x' + ptr.toString(16));
  }
  return hits;
}
// XIP pointer
scan('enable+num+XIPptr', i => buf[i]===0x01 && buf[i+1]>=1 && buf[i+1]<=24 && buf.readUInt32LE(i+2)>=0x01e00000 && buf.readUInt32LE(i+2)<0x01ea0000);
// RAM pointer (0x00800000-ish)
scan('enable+num+RAMptr', i => buf[i]===0x01 && buf[i+1]>=1 && buf[i+1]<=24 && buf.readUInt32LE(i+2)>=0x00800000 && buf.readUInt32LE(i+2)<0x00840000);
