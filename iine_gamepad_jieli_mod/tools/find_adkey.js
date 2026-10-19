const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const hits = [];
for (let i = 0x2400; i + 20 + 12 < 0x7f400; i++) {
  // candidate ad_value[10] as u16, must be monotonic and <=0x3ff
  const v = [];
  let ok = true;
  for (let k = 0; k < 10; k++) {
    const x = buf.readUInt16LE(i + k*2);
    if (x > 0x3ff) { ok = false; break; }
    v.push(x);
  }
  if (!ok) continue;
  let inc = true, dec = true;
  for (let k = 1; k < 10; k++) {
    if (!(v[k] > v[k-1])) inc = false;
    if (!(v[k] < v[k-1])) dec = false;
  }
  if (!inc && !dec) continue;
  // require spread
  if (Math.abs(v[9]-v[0]) < 50) continue;
  // key_value[10] right after (offset +20)
  const kv = Array.from(buf.slice(i+20, i+30));
  hits.push({off:i, v, kv, dir: inc?'inc':'dec'});
}
console.log('adkey-like candidates:', hits.length);
for (const h of hits.slice(0,30)) {
  console.log('@0x'+h.off.toString(16).padStart(6,'0'), h.dir, 'ad=['+h.v.join(',')+']', 'kv=['+h.kv.join(',')+']');
}
