const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 固件升级程序_1.4.40.exe');
// extract GBK-encoded Chinese runs (2-byte pairs in GBK range), min 3 chars
const gbk = new TextDecoder('gbk');
let cur = Buffer.alloc(0);
const out = [];
function flush() {
  if (cur.length >= 6) {
    let s;
    try { s = gbk.decode(cur); } catch (e) { s = ''; }
    if (/[\u4e00-\u9fff]/.test(s)) out.push(s);
  }
  cur = Buffer.alloc(0);
}
for (let i = 0; i < buf.length - 1; i++) {
  const b1 = buf[i], b2 = buf[i+1];
  const isGBK = (b1 >= 0x81 && b1 <= 0xFE) && ((b2 >= 0x40 && b2 <= 0xFE && b2 !== 0x7F));
  if (isGBK) { if (cur.length === 0) cur = Buffer.from([b1,b2]); else cur = Buffer.concat([cur, Buffer.from([b1,b2])]); i++; }
  else flush();
}
flush();
const seen = new Set();
let shown = 0;
for (const s of out) { if (!seen.has(s)) { seen.add(s); console.log(s); if (++shown >= 200) break; } }
