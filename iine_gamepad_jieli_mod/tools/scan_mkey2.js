const fs = require('fs');
const { doe, chipCrc16 } = require('./mkey.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const start = 0x7f400, end = 0x80400;
const hits = [];
for (let off = start; off + 32 <= end; off++) {
  const blob = buf.slice(off, off+32);
  // skip all-zero / all-0xFF
  if (blob.every(b=>b===0) || blob.every(b=>b===0xff)) continue;
  const seed = blob.readUInt16LE(16);
  const dec = doe(seed, blob, 0);
  if (chipCrc16(dec.slice(6,32)) === dec.readUInt16LE(4)) {
    hits.push({off, seed, dec});
  }
}
console.log('key_mac region mkey hits:', hits.length);
for (const h of hits) {
  console.log('@0x'+h.off.toString(16), 'seed=0x'+h.seed.toString(16), 'dec='+h.dec.toString('hex'));
}
// also dump key_mac first 0x200 for inspection
console.log('\n=== key_mac first 0x100 ===');
for (let o=start;o<start+0x100;o+=16){
  const c=buf.slice(o,o+16); const hex=Array.from(c).map(b=>b.toString(16).padStart(2,'0')).join(' ');
  console.log(o.toString(16)+'  '+hex);
}
