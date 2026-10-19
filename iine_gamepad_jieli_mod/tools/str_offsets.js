const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
let res = [];
let cur = '', start = 0;
for (let i = 0; i < buf.length; i++) {
  const b = buf[i];
  if (b >= 0x20 && b < 0x7f) { if (!cur) start = i; cur += String.fromCharCode(b); }
  else { if (cur.length >= 6) res.push([start, cur]); cur = ''; }
}
if (cur.length >= 6) res.push([start, cur]);
console.log('total strings>=6:', res.length);
// only in app area 0x2400..0x7F400
const app = res.filter(([o]) => o >= 0x2400 && o < 0x7f400);
console.log('app-area strings:', app.length);
for (const [o, s] of app) {
  console.log('0x' + o.toString(16).padStart(6,'0'), JSON.stringify(s));
}
