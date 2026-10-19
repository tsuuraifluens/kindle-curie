const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const start = 0x49b00, end = 0x5a288;
const hits = [];
for (let i = start; i + 40 < end; i++) {
  if (buf[i] !== 1) continue;          // enable = ENABLE_THIS_MOUDLE
  const pin = buf[i+1], ext = buf[i+2];
  if (pin > 63 || ext > 1) continue;
  const ch = buf.readUInt32LE(i+4);
  if (ch > 0x20 && ch < 0x100) continue; // allow small channel or PMU bits
  if (ch > 0x10F) continue;
  const vals = [];
  let ok = true;
  for (let k=0;k<10;k++){ const v=buf.readUInt16LE(i+8+k*2); if(v>0x3ff){ok=false;break;} vals.push(v); }
  if (!ok) continue;
  let inc=true,dec=true;
  for(let k=1;k<10;k++){ if(!(vals[k]>vals[k-1]))inc=false; if(!(vals[k]<vals[k-1]))dec=false; }
  if(!inc&&!dec) continue;
  if (Math.abs(vals[9]-vals[0]) < 30) continue;
  hits.push({off:i, pin, ext, ch, vals});
}
console.log('adkey_data candidates:', hits.length);
for (const h of hits) {
  console.log('@file 0x'+h.off.toString(16), 'pin='+h.pin, 'ext='+h.ext, 'ch=0x'+h.ch.toString(16), 'vals=['+h.vals.join(',')+']');
  console.log('   key_value[10]:', Array.from(buf.slice(h.off+28, h.off+38)).join(','));
}
