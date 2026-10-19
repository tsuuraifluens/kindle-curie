const fs = require('fs');
const { doe32 } = require('./doe32.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const fileStart = 0x2520;      // app.bin data in .ufw
const size = 0x308A8;          // 198824 bytes per ADDRESS_MAP
const vmaStart = 0x1E00120;
const dec = doe32(0x190D, buf.slice(fileStart, fileStart + size), vmaStart);
fs.writeFileSync('work/app_dec.bin', dec);
console.log('decrypted', dec.length, 'bytes -> work/app_dec.bin');
// extract ascii strings
let res = [], cur = '', st = 0;
for (let i=0;i<dec.length;i++){
  const b=dec[i];
  if (b>=0x20&&b<0x7f){ if(!cur) st=i; cur+=String.fromCharCode(b); }
  else { if(cur.length>=5) res.push([st,cur]); cur=''; }
}
if (cur.length>=5) res.push([st,cur]);
console.log('strings>=5:', res.length);
for (const [o,s] of res.slice(0,80)) console.log('0x'+(vmaStart+o).toString(16), JSON.stringify(s));
