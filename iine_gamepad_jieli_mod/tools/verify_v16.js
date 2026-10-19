// Independently verify the IINE Kindle Click mod firmware using the recovered
// JieLi SFC cipher (ChipKey 0x190D).
const fs = require('fs');

const UFW = process.argv[2];
const OUT = process.argv[3] || null;

function jlEncCipher(buf, off, size, key) {
  let k = key & 0xffff;
  for (let i = 0; i < size; i++) {
    buf[off + i] ^= k & 0xff;
    k = (((k << 1) ^ ((k & 0x8000) ? 0x1021 : 0)) & 0xffff);
  }
}

function jlSfcCipher(buf, off, size, base, key, blocksize = 32) {
  for (let i = 0; i < size; i += blocksize) {
    const seed = (key ^ ((off + i - base) >> 2)) & 0xffff;
    jlEncCipher(buf, off + i, Math.min(blocksize, size - i), seed);
  }
}

const raw = fs.readFileSync(UFW);
const FLASH_BASE = 0x400;
const APP_BASE = 0x2000;
const APP_END = 0x34000;
const CHIPKEY = 0x190d;

const app = Buffer.from(raw.slice(FLASH_BASE + APP_BASE, FLASH_BASE + APP_END));
jlSfcCipher(app, 0, app.length, 0, CHIPKEY);
if (OUT) fs.writeFileSync(OUT, app);

// app.bin starts at buffer offset 0x120; app_offset X -> buffer X + 0x120
const A = 0x120;
function hex(off, n) { return app.slice(off, off + n).toString('hex'); }
function ascii(off, n) { return app.slice(off, off + n).toString('latin1').replace(/\x00+$/, ''); }

console.log('firmware:', UFW);
console.log('app area decrypted:', app.length, 'bytes; first16:', hex(0, 16));

const keys = [
  ['Button A', 0x2289C], ['Button B', 0x228A2], ['Button X', 0x228A8], ['Button Y', 0x228AE],
  ['D-pad Up', 0x228FC], ['D-pad Down', 0x22902], ['D-pad Left', 0x22908], ['D-pad Right', 0x2290E],
];
console.log('\n-- Table 1 key entries (6 bytes each) --');
for (const [name, off] of keys) {
  const b = app.slice(A + off, A + off + 6);
  console.log(`${name.padEnd(11)} @app+0x${off.toString(16)}  ${b.toString('hex')}  usage=0x${b[3].toString(16).padStart(2,'0')}`);
}

console.log('\n-- DIS / GAP --');
console.log('PnP ID      @app+0x223BC:', hex(A + 0x223BC, 7));
console.log('Manufacturer@app+0x22384:', JSON.stringify(ascii(A + 0x22384, 13)));
console.log('GAP reader  @app+0x211C6:', JSON.stringify(ascii(A + 0x211C6, 12)));
console.log('GAP keyboard@app+0x211EB:', JSON.stringify(ascii(A + 0x211EB, 14)));

console.log('\n-- SM IO capability table (expect 03 00 00 00) --');
for (const off of [0x16508, 0x1650C, 0x16518, 0x16530, 0x16534, 0x16538, 0x16540]) {
  console.log(`@app+0x${off.toString(16)}: ${hex(A + off, 4)}`);
}
