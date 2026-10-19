const fs=require('fs');
const { crc16, encCipher } = require('./build_kindle_click.js');
const raw=Buffer.from(fs.readFileSync('analysis/firmware/L1161 L1162.ufw'));
const hdr=Buffer.from(raw.slice(0,0x40));
encCipher(hdr,0,0x40,0xffff);
console.log('desc:',hdr.toString('hex'));
const listCrc=crc16(raw.slice(0x40,0x2c0));
console.log('listCrc=0x'+listCrc.toString(16));
hdr.writeUInt16LE(listCrc,2);
console.log('after list:',hdr.toString('hex'));
console.log('crc16(2..0x40)=0x'+crc16(hdr.slice(2,0x40)).toString(16));
console.log('crc16(2..0x40) explicit bytes len', hdr.slice(2,0x40).length);
// recompute from candidate
const cand=fs.readFileSync('work/our_v18_pnp.ufw');
const h2=Buffer.from(cand.slice(0,0x40)); encCipher(h2,0,0x40,0xffff);
console.log('cand desc:',h2.toString('hex'));
console.log('cand crc16(2..0x40)=0x'+crc16(h2.slice(2,0x40)).toString(16));
console.log('equal hdr?',hdr.equals(h2));
