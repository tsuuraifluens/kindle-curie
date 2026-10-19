const fs = require('fs');
const { descramble } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const N = 0x2400;
const dec = descramble(buf, 0, N);
// find ascii strings >=4 in decrypted region
let runs = [];
let cur = [], start = 0;
for (let i = 0; i < dec.length; i++) {
  const b = dec[i];
  if (b >= 0x20 && b < 0x7f) { if (cur.length === 0) start = i; cur.push(String.fromCharCode(b)); }
  else { if (cur.length >= 4) runs.push([start, cur.join('')]); cur = []; }
}
if (cur.length >= 4) runs.push([start, cur.join('')]);
console.log('ASCII runs >=4 in decrypted 0x0-0x2400:');
for (const [off, s] of runs) console.log('0x' + off.toString(16).padStart(4,'0'), JSON.stringify(s));
