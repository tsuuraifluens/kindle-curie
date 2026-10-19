const fs = require('fs');
const UFW = process.argv[2];
function jlEncCipher(buf, off, size, key) {
  let k = key & 0xffff;
  for (let i = 0; i < size; i++) {
    buf[off + i] ^= k & 0xff;
    k = (((k << 1) ^ ((k & 0x8000) ? 0x1021 : 0)) & 0xffff);
  }
}
function jlSfcCipher(buf, off, size, base, key, bs = 32) {
  for (let i = 0; i < size; i += bs) {
    const seed = (key ^ ((off + i - base) >> 2)) & 0xffff;
    jlEncCipher(buf, off + i, Math.min(bs, size - i), seed);
  }
}
const raw = fs.readFileSync(UFW);
const app = Buffer.from(raw.slice(0x400 + 0x2000, 0x400 + 0x34000));
jlSfcCipher(app, 0, app.length, 0, 0x190d);
const A = 0x120;
const h = (off, n) => app.slice(A + off, A + off + n).toString('hex');
const a = (off, n) => app.slice(A + off, A + off + n).toString('latin1').replace(/\x00+$/, '');

console.log('== adv_setup_init patches (app offsets) ==');
for (const [off, n] of [[0x04D36,6],[0x04D58,2],[0x04D5E,2],[0x04D90,2]]) {
  console.log(`  @0x${off.toString(16)}: ${h(off,n)}`);
}
console.log('  rodata PID str @0x22EF0:', h(0x22EF0, 8));
console.log('  rodata @0x23010      :', h(0x23010, 8));
console.log('== mode redirect @0x0712E ==');
console.log('  ', h(0x0712E, 6));
console.log('  target str @0x2130B:', JSON.stringify(a(0x211EB, 14)));

console.log('== PnP area 0x223B0..0x223D0 ==');
for (let o = 0x223B0; o < 0x223D0; o += 8) {
  console.log(`  @0x${o.toString(16)}: ${h(o, 8)}`);
}

console.log('== Table 3 (0x22A1C..0x22A52) ==');
for (let o = 0x22A1C; o < 0x22A52; o += 6) {
  const b = app.slice(A + o, A + o + 6);
  console.log(`  @0x${o.toString(16)}: ${b.toString('hex')} usage=0x${b[3].toString(16).padStart(2,'0')}`);
}
