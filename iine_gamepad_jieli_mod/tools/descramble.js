// Jieli AC632N (bd19/BR22) top-level flash-header de-scrambler.
// The header uses the constant "ENC block" keystream (key 0xFFFF).
// 32-byte periodic keystream, derived from a known plaintext/ciphertext
// pair documented by the jielie project (BR17 header, same ENC method).
const fs = require('fs');

const KS = Buffer.from([
  0xff, 0xdf, 0x9f, 0x1f, 0x1f, 0x3e, 0x7c, 0xf8,
  0xf0, 0xc1, 0xa3, 0x67, 0xce, 0xbd, 0x5b, 0x97,
  0x0f, 0x1e, 0x3c, 0x78, 0xd1, 0x83, 0x27, 0x4e,
  0x9c, 0x19, 0x13, 0x26, 0x4c, 0x98, 0x30, 0x60,
]);

function descramble(buf, start = 0, length = 128) {
  const out = Buffer.alloc(length);
  for (let i = 0; i < length; i++) {
    out[i] = buf[start + i] ^ KS[i & 31];
  }
  return out;
}

function hexdump(buf, base = 0) {
  const lines = [];
  for (let off = 0; off < buf.length; off += 16) {
    const chunk = buf.slice(off, off + 16);
    const hex = Array.from(chunk).map((b) => b.toString(16).padStart(2, '0')).join(' ');
    const asc = Array.from(chunk).map((b) => (b >= 32 && b < 127 ? String.fromCharCode(b) : '.')).join('');
    lines.push((base + off).toString(16).padStart(6, '0') + '  ' + hex.padEnd(47) + '  ' + asc);
  }
  return lines.join('\n');
}

function u16le(b, o) { return b.readUInt16LE(o); }
function u32le(b, o) { return b.readUInt32LE(o); }

if (require.main === module) {
  const file = process.argv[2];
  const len = parseInt(process.argv[3] || '128', 10);
  const buf = fs.readFileSync(file);
  const dec = descramble(buf, 0, Math.min(len, buf.length));
  console.log('file:', file, 'size:', buf.length);
  console.log(hexdump(dec));
  console.log('');
  console.log('u32 @0x00:', '0x' + u32le(dec, 0).toString(16).padStart(8, '0'));
  console.log('u32 @0x04:', '0x' + u32le(dec, 4).toString(16).padStart(8, '0'), '=', u32le(dec, 4));
  console.log('u32 @0x08:', '0x' + u32le(dec, 8).toString(16).padStart(8, '0'), '=', u32le(dec, 8));
  console.log('u32 @0x0c:', '0x' + u32le(dec, 0x0c).toString(16).padStart(8, '0'), '=', u32le(dec, 0x0c));
  console.log('pid[16] @0x10:', JSON.stringify(dec.slice(0x10, 0x20).toString('latin1').replace(/\0+$/, '')));
}

module.exports = { KS, descramble, hexdump, u16le, u32le };
