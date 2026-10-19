const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
// scan for iokey_platform_data-like: 01 <num> <lo> <mid> E0 01  (pointer 0x01E0xxxx)
const hits = [];
for (let i = 0; i + 6 < buf.length; i++) {
  if (buf[i] === 0x01 && buf[i+4] === 0xE0 && buf[i+5] === 0x01) {
    const num = buf[i+1];
    const ptr = buf.readUInt32LE(i+2);
    if (ptr >= 0x01E00000 && ptr <= 0x01EFFFFF && num >= 1 && num <= 20) {
      hits.push({off:i, num, ptr});
    }
  }
}
console.log('candidate iokey_platform_data hits:', hits.length);
for (const h of hits.slice(0,40)) console.log('  @0x'+h.off.toString(16), 'num='+h.num, 'port_ptr=0x'+h.ptr.toString(16), 'file_off=0x'+(h.ptr-0x01E00000).toString(16));
