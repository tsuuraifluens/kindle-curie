const fs = require('fs');
const { step32 } = require('./doe32.js');
const { KS } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function crc16(data, init=0){ let c=init&0xffff; for(const b of data){ c^=(b<<8); for(let i=0;i<8;i++) c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff; } return c&0xffff; }
function ks(seed,n){ const o=Buffer.alloc(n); let s=seed>>>0; for(let i=0;i<n;i++){o[i]=s&0xff; s=step32(s);} return o; }
function decRange(fileOff,len,vmaStart){ const out=Buffer.alloc(len); for(let i=0;i<len;i+=32){ const seed=(0x190D^((vmaStart+i)>>>2))>>>0; const k=ks(seed,32); for(let j=0;j<32&&i+j<len;j++) out[i+j]=buf[fileOff+i+j]^k[j]; } return out; }
const appArea = decRange(0x2400, 0x32000, 0x1E00000); // flash 0x2000..0x34000
const appEntry = appArea.slice(0x20,0x40);
console.log('app.bin entry:', appEntry.toString('hex'));
const appData = appArea.slice(0x120, 0x120+0x308A8);
console.log('crc16(app.bin data) = 0x'+crc16(appData).toString(16), ' stored=0x'+appEntry.readUInt16LE(0).toString(16));
console.log('crc16(entry[2..32]) = 0x'+crc16(appEntry.slice(2)).toString(16));
// flash image = UFW 0x400..0x34400
const flash = buf.slice(0x400, 0x34400);
console.log('crc16(flash image)  = 0x'+crc16(flash).toString(16));
// UFW header (descrambled)
function decKs(off,len){ const o=Buffer.alloc(len); for(let i=0;i<len;i++) o[i]=buf[off+i]^KS[i&31]; return o; }
const hdr = decKs(0,0x40);
console.log('crc16(hdr[2..0x40]) = 0x'+crc16(hdr.slice(2)).toString(16), ' stored=0x'+hdr.readUInt16LE(0).toString(16));
// entry list: bytes 0x40..0x2C0 descrambled per entry
let list = Buffer.alloc(0x280);
for(let i=0;i<8;i++){ const g=Buffer.alloc(0x50); for(let j=0;j<0x50;j++) g[j]=buf[0x40+i*0x50+j]^KS[j&31]; g.copy(list,i*0x50); }
console.log('crc16(entry list)   = 0x'+crc16(list).toString(16), ' stored=0x'+hdr.readUInt16LE(2).toString(16));
