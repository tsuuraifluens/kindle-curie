const fs=require('fs');
const { crc16 } = require('./build_kindle_click.js');
const cand=fs.readFileSync('work/our_v18_pnp.ufw');
const KS=[0xff,0xdf,0x9f,0x1f,0x1f,0x3e,0x7c,0xf8,0xf0,0xc1,0xa3,0x67,0xce,0xbd,0x5b,0x97,0x0f,0x1e,0x3c,0x78,0xd1,0x83,0x27,0x4e,0x9c,0x19,0x13,0x26,0x4c,0x98,0x30,0x60];
const h=Buffer.from(cand.slice(0,0x40));
for(let i=0;i<0x40;i++) h[i]^=KS[i&31];
console.log('descrambled header:', h.toString('hex'));
console.log('stored hdrCRC=0x'+h.readUInt16LE(0).toString(16), 'stored listCRC=0x'+h.readUInt16LE(2).toString(16));
console.log('computed crc16(hdr[2:0x40])=0x'+crc16(h.slice(2,0x40)).toString(16));
console.log('computed crc16(hdr[4:0x40])=0x'+crc16(h.slice(4,0x40)).toString(16));
// also list crc
const num=h.readUInt16LE(8); const hs=0x40+num*0x50;
console.log('numents',num,'headersize 0x'+hs.toString(16));
console.log('computed list crc=0x'+crc16(cand.slice(0x40,hs)).toString(16), 'stored=0x'+h.readUInt16LE(2).toString(16));
