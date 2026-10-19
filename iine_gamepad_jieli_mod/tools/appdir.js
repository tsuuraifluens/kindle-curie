const fs = require('fs');
const { KS } = require('./descramble.js');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function decAt(off,len,phase=0){ const o=Buffer.alloc(len); for(let i=0;i<len;i++) o[i]=buf[off+i]^KS[(i+phase)&31]; return o; }
// app_dir_head at UFW 0x2400, 0x120 bytes, 9 entries x 0x20; try per-entry KS reset
console.log('=== app_dir_head entries (UFW 0x2400..0x2520) ===');
for (let i=0;i<9;i++){
  const off=0x2400+i*0x20;
  const g=decAt(off,0x20,0);
  console.log('entry '+i+' @flash 0x'+(0x2000+i*0x20).toString(16));
  console.log('  hex:', g.toString('hex'));
  console.log('  +00:', g.readUInt16LE(0).toString(16), '+02:', g.readUInt16LE(2).toString(16), '+04:', g.readUInt32LE(4).toString(16), '+08:', g.readUInt32LE(8).toString(16), '+0c:', g.readUInt32LE(0x0c).toString(16), 'name:', JSON.stringify(g.slice(0x10,0x20).toString('latin1').replace(/\0+$/,'')));
}
