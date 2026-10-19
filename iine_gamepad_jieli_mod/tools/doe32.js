// Correct 32-bit `doe` from ROM 0x106B44:
//   seed = key ^ (addr >>> 2)   (full 32-bit)
//   keystream[i] = state & 0xFF; state = LFSR step (32-bit, tap bit15, poly 0x1021)
const fs = require('fs');

function step32(s) {
  const bit15 = (s >>> 15) & 1;
  let n = ((s << 1) >>> 0) & 0xffffefde;
  n = (n | bit15) >>> 0;
  n = (n | ((((s >>> 11) ^ (s >>> 15)) << 12) & 0x1000)) >>> 0;
  n = (n | ((((s >>> 4) ^ (s >>> 15)) << 5) & 0x20)) >>> 0;
  return n >>> 0;
}

function doe32(key, data, addr) {
  let s = ((key ^ (addr >>> 2)) >>> 0);
  const out = Buffer.alloc(data.length);
  for (let i = 0; i < data.length; i++) {
    out[i] = (data[i] ^ (s & 0xff)) & 0xff;
    s = step32(s);
  }
  return out;
}

function strings(buf, min = 6) {
  const res = [];
  let cur = '', start = 0;
  for (let i = 0; i < buf.length; i++) {
    const b = buf[i];
    if (b >= 0x20 && b < 0x7f) { if (!cur) start = i; cur += String.fromCharCode(b); }
    else { if (cur.length >= min) res.push([start, cur]); cur = ''; }
  }
  if (cur.length >= min) res.push([start, cur]);
  return res;
}

if (require.main === module) {
  const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
  const start = parseInt(process.argv[2] || '0x2400', 16);
  const len = parseInt(process.argv[3] || '0x400', 16);
  const keys = [0xffff, 0x0000, 0x16ef, 0x68af, 0x6b7c];
  const modes = [
    ['xip', (o) => 0x01e00000 + o],
    ['flash', (o) => o],
    ['zero', () => 0],
  ];
  for (const key of keys) {
    for (const [name, fn] of modes) {
      const dec = doe32(key, buf.slice(start, start + len), fn(start));
      const ss = strings(dec, 8);
      const lz4 = dec[0] === 4 && dec[1] === 0x22 && dec[2] === 0x4d && dec[3] === 0x18;
      console.log(`key=0x${key.toString(16).padStart(4,'0')} addr=${name} strings=${ss.length} lz4=${lz4} first=${dec.slice(0,16).toString('hex')}`);
      if (ss.length > 3) for (const [o, s] of ss.slice(0, 6)) console.log('    0x' + (start+o).toString(16), JSON.stringify(s));
    }
  }
}

module.exports = { doe32, step32 };
