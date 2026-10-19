const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
// 1) search plaintext product strings (case insensitive)
const pats = ['IINE','iine','L116','L115','L117','reader','Reader','game','Game','pad','Pad','JOY','joy','keyboard','Keyboard','mouse','Mouse','HID','hid','GAME','GAT','BLE','ble','adv','ADV','name','Name','pro','Pro','MINI','mini'];
for (const p of pats) {
  const idx = buf.indexOf(Buffer.from(p,'ascii'));
  // find all occurrences
  let n=0, i=0; while((i=buf.indexOf(Buffer.from(p,'ascii'),i))>=0){n++;i++; if(n>3)break;}
  if(n>0) console.log(p, 'occurrences(>=)', n);
}
// 2) XOR transforms and count printable
function countPrintable(b) { let n=0; for(const x of b){ if((x>=0x20&&x<0x7f)) n++; } return n; }
for (const key of [0xFF,0x55,0xAA,0x1F,0x71,0x4C,0x4A,0x4B]) {
  const t = Buffer.alloc(Math.min(buf.length, 4096));
  for (let i=0;i<t.length;i++) t[i]=buf[i]^key;
  console.log('XOR 0x'+key.toString(16), 'printable in first 4K:', countPrintable(t));
}
