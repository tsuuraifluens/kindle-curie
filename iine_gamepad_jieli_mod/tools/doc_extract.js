const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/升级步骤.doc');
// Extract UTF-16LE runs containing CJK + ASCII, min run length 2
let out = [];
let cur = '';
for (let i = 0; i + 1 < buf.length; i += 2) {
  const code = buf[i] | (buf[i+1] << 8);
  const isCJK = (code >= 0x4E00 && code <= 0x9FFF) || (code >= 0x3000 && code <= 0x303F) || (code >= 0xFF00 && code <= 0xFFEF);
  const isAscii = (code >= 0x20 && code < 0x7F);
  const isNum = (code >= 0x30 && code <= 0x39);
  if (isCJK || isAscii || isNum || code === 0x0A || code === 0x0D) {
    cur += String.fromCharCode(code);
  } else {
    if (cur.replace(/[ \r\n]/g,'').length >= 2) out.push(cur);
    cur = '';
  }
}
if (cur.length) out.push(cur);
// filter: keep lines with CJK
const cjk = out.filter(s => /[\u4E00-\u9FFF]/.test(s));
console.log('total UTF16 runs with CJK:', cjk.length);
cjk.forEach(s => console.log(s.replace(/\r/g,'')));
