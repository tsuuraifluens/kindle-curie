const fs = require('fs');
// usage: node strings.js <file> [minLen] [--utf16]
const file = process.argv[2];
const minLen = parseInt(process.argv[3] || '5', 10);
const utf16 = process.argv.includes('--utf16');
const buf = fs.readFileSync(file);
function extract(bytes, minLen) {
  const out = [];
  let cur = [];
  for (let i = 0; i < bytes.length; i++) {
    const b = bytes[i];
    if (b >= 0x20 && b < 0x7f) { cur.push(String.fromCharCode(b)); }
    else { if (cur.length >= minLen) out.push(cur.join('')); cur = []; }
  }
  if (cur.length >= minLen) out.push(cur.join(''));
  return out;
}
let lines;
if (utf16) {
  // UTF-16LE
  lines = [];
  let cur = [];
  for (let i = 0; i + 1 < buf.length; i += 2) {
    const code = buf[i] | (buf[i+1] << 8);
    if (code >= 0x20 && code < 0x7f) { cur.push(String.fromCharCode(code)); }
    else { if (cur.length >= minLen) lines.push(cur.join('')); cur = []; }
  }
} else {
  lines = extract(buf, minLen);
}
// dedupe preserving order
const seen = new Set();
for (const l of lines) { if (!seen.has(l)) { seen.add(l); console.log(l); } }
