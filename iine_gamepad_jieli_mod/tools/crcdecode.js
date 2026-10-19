const fs = require('fs');
const { KS } = require('./descramble.js');
const keylcode = Buffer.from([0xc3,0xcf,0xc0,0xe8,0xce,0xd2,0xb0,0xae,0xc4,0xe3,0xa3,0xac,0xd3,0xf1,0xc1,0xd6]);
console.log('keylcode GBK:', new TextDecoder('gbk').decode(keylcode));
function crcStep(crc, byte) {
  crc ^= (byte << 8);
  for (let i = 0; i < 8; i++) crc = (crc & 0x8000) ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
  return crc & 0xffff;
}
// generate keystream: feed keylcode[i%16], output low byte
function gen(len) {
  const out = Buffer.alloc(len);
  let crc = 0xffff;
  for (let i = 0; i < len; i++) {
    crc = crcStep(crc, keylcode[i & 15]);
    out[i] = crc & 0xff;
  }
  return out;
}
const ks = gen(32);
console.log('CrcDecode keystream[32]:', ks.toString('hex'));
console.log('header keystream KS   :', KS.toString('hex'));
console.log('match:', ks.equals(KS));
// try decrypt app region with this keystream
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
for (const start of [0x2400, 0x19f10, 0x34780]) {
  const dec = Buffer.alloc(0x400);
  const k = gen(0x400);
  for (let i=0;i<0x400;i++) dec[i] = buf[start+i] ^ k[i];
  let printable = 0; for (const b of dec) if (b>=0x20 && b<0x7f) printable++;
  console.log('region 0x'+start.toString(16), 'printable/1024=', printable, 'first16=', dec.slice(0,16).toString('hex'));
}
