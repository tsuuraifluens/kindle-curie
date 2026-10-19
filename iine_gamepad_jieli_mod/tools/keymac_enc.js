const fs = require('fs');
const { KS } = require('./descramble.js');
const { doe, chipCrc16 } = require('./mkey.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const start = 0x7f400, end = 0x80400;
// descramble with KS aligned to absolute offset
const decEnc = Buffer.alloc(end-start);
for (let i=0;i<decEnc.length;i++) decEnc[i] = buf[start+i] ^ KS[(start+i) & 31];
// scan mkey in descrambled key_mac
const hits = [];
for (let off=0; off+32<=decEnc.length; off++) {
  const blob = decEnc.slice(off, off+32);
  if (blob.every(b=>b===0) || blob.every(b=>b===0xff)) continue;
  const seed = blob.readUInt16LE(16);
  const d = doe(seed, blob, 0);
  if (chipCrc16(d.slice(6,32)) === d.readUInt16LE(4)) hits.push({off, seed, d});
}
console.log('mkey hits in descrambled key_mac:', hits.length);
for (const h of hits) console.log('  @keymac+0x'+h.off.toString(16), 'seed=0x'+h.seed.toString(16), h.d.toString('hex'));
console.log('\nfirst 0x60 descrambled:');
console.log(decEnc.slice(0,0x60).toString('hex'));
console.log(JSON.stringify(decEnc.slice(0,0x60).toString('latin1')));
