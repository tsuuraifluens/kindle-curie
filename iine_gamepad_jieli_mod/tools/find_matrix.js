const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const hits = [];
for (let i = 0x2400; i + 10 < 0x7f400; i++) {
  const p1 = buf.readUInt32LE(i);
  const p2 = buf.readUInt32LE(i+4);
  const r = buf[i+8], c = buf[i+9];
  const inXip = (x) => x >= 0x01e00000 && x < 0x01ea0000;
  if (inXip(p1) && inXip(p2) && r >= 1 && r <= 8 && c >= 1 && c <= 20) {
    hits.push({off:i, p1, p2, r, c});
  }
}
console.log('matrix_key_param candidates:', hits.length);
for (const h of hits.slice(0,30)) {
  const fo1 = h.p1 - 0x01e00000, fo2 = h.p2 - 0x01e00000;
  console.log('@0x'+h.off.toString(16).padStart(6,'0'), 'row_ptr=0x'+h.p1.toString(16)+'(f0x'+fo1.toString(16)+')', 'col_ptr=0x'+h.p2.toString(16)+'(f0x'+fo2.toString(16)+')', 'rows='+h.r, 'cols='+h.c);
  // dump pointed arrays (up to 8 u32 each)
  try {
    const rows = []; for (let k=0;k<Math.min(h.r,8);k++) rows.push(buf.readUInt32LE(fo1+k*4));
    const cols = []; for (let k=0;k<Math.min(h.c,20);k++) cols.push(buf.readUInt32LE(fo2+k*4));
    console.log('    rows=['+rows.join(',')+'] cols=['+cols.join(',')+']');
  } catch(e) {}
}
