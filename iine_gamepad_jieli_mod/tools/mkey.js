// Reproduce ROM _sdk_mkey_lock: de-obfuscate a 32-byte key blob with doe()
// and verify chip_crc16(blob+6, 26) == u16(blob+4).
const fs = require('fs');

function crcStep(state) {
  const msb = state & 0x8000;
  state = (state << 1) & 0xffff;
  if (msb) state ^= 0x1021;
  return state & 0xffff;
}

// doe(key, data, addr): keystream[i] = LFSR^i(seed)&0xff, seed = key ^ (addr>>>2)
function doe(key, data, addr) {
  let s = (key ^ (addr >>> 2)) & 0xffff;
  const out = Buffer.alloc(data.length);
  for (let i = 0; i < data.length; i++) {
    out[i] = data[i] ^ (s & 0xff);
    s = crcStep(s);
  }
  return out;
}

function chipCrc16(data) {
  let s = 0;
  for (const b of data) {
    s ^= (b << 8);
    for (let i = 0; i < 8; i++) s = (s & 0x8000) ? ((s << 1) ^ 0x1021) & 0xffff : (s << 1) & 0xffff;
  }
  return s & 0xffff;
}

function tryMkey(buf, off) {
  const blob = buf.slice(off, off + 32);
  const seed = blob.readUInt16LE(16);
  const dec = doe(seed, blob, 0);
  const crc = chipCrc16(dec.slice(6, 32));
  const expect = dec.readUInt16LE(4);
  return { off, seed, dec, crc, expect, ok: crc === expect };
}

if (require.main === module) {
  const buf = fs.readFileSync(process.argv[2]);
  const base = parseInt(process.argv[3] || '0x7f400', 16);
  console.log('scanning key blobs around 0x' + base.toString(16));
  for (let off = base - 0x100; off <= base + 0x100; off += 0x10) {
    if (off < 0 || off + 32 > buf.length) continue;
    const r = tryMkey(buf, off);
    if (r.ok) {
      console.log('CRC MATCH @0x' + off.toString(16), 'seed=0x' + r.seed.toString(16));
      console.log('  decoded:', r.dec.toString('hex'));
      console.log('  ascii  :', JSON.stringify(r.dec.toString('latin1')));
    }
  }
  // also show the base candidate details
  const r = tryMkey(buf, base);
  console.log('\nbase candidate @0x' + base.toString(16), 'seed=0x' + r.seed.toString(16), 'crc=0x' + r.crc.toString(16), 'expect=0x' + r.expect.toString(16), 'ok=' + r.ok);
  console.log('  decoded:', r.dec.toString('hex'));
}

module.exports = { doe, chipCrc16, tryMkey };
