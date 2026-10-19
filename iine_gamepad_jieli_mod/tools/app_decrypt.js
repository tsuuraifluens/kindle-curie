// Jieli "ENC block" = CRC16-CCITT stream (poly 0x1021, init 0xFFFF),
// emitting the low byte of the state before each step.
// App-area scramble: per 32-byte block, state = chipkey ^ (addr >> 2).
const fs = require('fs');

function crc16Step(state) {
  const msb = state & 0x8000;
  state = ((state << 1) & 0xffff);
  if (msb) state ^= 0x1021;
  return state & 0xffff;
}

function encBlock(seed) {
  const out = Buffer.alloc(32);
  let s = seed & 0xffff;
  for (let i = 0; i < 32; i++) {
    out[i] = s & 0xff;
    s = crc16Step(s);
  }
  return out;
}

// Decrypt a region. addrOf(fileOffset) returns the "absolute address" used in
// the seed formula. blockBase aligns to 32-byte blocks.
function decryptRegion(buf, start, length, chipkey, addrOf) {
  const out = Buffer.alloc(length);
  for (let i = 0; i < length; i += 32) {
    const absAddr = addrOf(start + i);
    const seed = (chipkey ^ (absAddr >>> 2)) & 0xffff;
    const ks = encBlock(seed);
    for (let j = 0; j < 32 && i + j < length; j++) {
      out[i + j] = buf[start + i + j] ^ ks[j];
    }
  }
  return out;
}

function strings(buf, minLen = 6) {
  const res = [];
  let cur = '', start = 0;
  for (let i = 0; i < buf.length; i++) {
    const b = buf[i];
    if (b >= 0x20 && b < 0x7f) { if (!cur) start = i; cur += String.fromCharCode(b); }
    else { if (cur.length >= minLen) res.push([start, cur]); cur = ''; }
  }
  if (cur.length >= minLen) res.push([start, cur]);
  return res;
}

if (require.main === module) {
  const file = process.argv[2];
  const buf = fs.readFileSync(file);
  const start = parseInt(process.argv[3] || '0x17840', 16);
  const length = parseInt(process.argv[4] || '0x800', 16);
  const chipkey = parseInt(process.argv[5] || '0xffff', 16);
  const addrMode = process.argv[6] || 'offset';
  const addrOf = (off) => (addrMode === 'xip' ? 0x01e00000 + off : off);
  const dec = decryptRegion(buf, start, length, chipkey, addrOf);
  console.log(`start=0x${start.toString(16)} len=0x${length.toString(16)} chipkey=0x${chipkey.toString(16)} addr=${addrMode}`);
  console.log('first 64 bytes:', dec.slice(0, 64).toString('hex'));
  const ss = strings(dec, 6);
  console.log('strings:', ss.length);
  for (const [o, s] of ss.slice(0, 30)) console.log('  0x' + (start + o).toString(16), JSON.stringify(s));
}

module.exports = { crc16Step, encBlock, decryptRegion, strings };
