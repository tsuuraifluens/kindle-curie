// Decode the BR17-style obfuscated chipkey (32 bytes) and try decrypting the
// app image with seed = key ^ (addr >> 2).
const fs = require('fs');
const { decryptRegion, strings } = require('./app_decrypt.js');

function decodeChipkey(b) {
  if (b.length < 32) return null;
  let sum = 0;
  for (let i = 0; i < 16; i++) sum = (sum + b[i]) & 0xff;
  if (sum < 0x10) sum = 0x55;
  if (sum >= 0xe0) sum = 0xaa;
  let key = 0;
  for (let i = 0; i < 16; i++) {
    const v = (b[i] ^ b[31 - i]) & 0xff;
    const bit = v < sum ? 1 : 0; // signed subtraction: negative -> 1
    key = ((key << 1) | bit) & 0xffff;
  }
  return { key, sum };
}

if (require.main === module) {
  const file = process.argv[2];
  const buf = fs.readFileSync(file);
  const keys = [];
  for (const off of [0x7f400, 0x7f420, 0x7f440, 0x7f460, 0x7f480]) {
    if (off + 32 > buf.length) continue;
    const r = decodeChipkey(buf.slice(off, off + 32));
    if (r) {
      keys.push({ off, ...r });
      console.log('key_mac @0x' + off.toString(16) + ' -> chipkey=0x' + r.key.toString(16).padStart(4, '0') + ' (sum=0x' + r.sum.toString(16) + ')');
    }
  }
  // try app decryption candidates
  const appStart = parseInt(process.argv[3] || '0x19f10', 16);
  const appLen = parseInt(process.argv[4] || '0x4000', 16);
  console.log('\napp candidate @0x' + appStart.toString(16));
  for (const k of keys) {
    for (const [label, addrOf] of [['offset', (o) => o], ['xip', (o) => 0x01e00000 + o]]) {
      const dec = decryptRegion(buf, appStart, appLen, k.key, addrOf);
      const ss = strings(dec, 8);
      console.log('  key=0x' + k.key.toString(16) + ' addr=' + label + ' strings>=8: ' + ss.length + (ss[0] ? '  first=' + JSON.stringify(ss[0][1]) : ''));
      if (ss.length > 8) {
        for (const [o, s] of ss.slice(0, 15)) console.log('     0x' + (appStart + o).toString(16), JSON.stringify(s));
      }
    }
  }
}

module.exports = { decodeChipkey };
