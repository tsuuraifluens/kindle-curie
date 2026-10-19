const fs = require('fs');
const { doe, chipCrc16 } = require('./mkey.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const hits = [];
for (let off = 0; off + 32 <= buf.length; off++) {
  const seed = buf.readUInt16LE(off + 16);
  const dec = doe(seed, buf.slice(off, off + 32), 0);
  const crc = chipCrc16(dec.slice(6, 32));
  if (crc === dec.readUInt16LE(4)) {
    hits.push({ off, seed, dec });
  }
}
console.log('mkey CRC matches:', hits.length);
for (const h of hits) {
  console.log('@0x' + h.off.toString(16).padStart(6,'0'), 'seed=0x' + h.seed.toString(16), 'dec=' + h.dec.toString('hex'));
  console.log('    ascii:', JSON.stringify(h.dec.toString('latin1')));
}
