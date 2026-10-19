const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
// search LZ4 frame magic 04 22 4d 18
const magic = Buffer.from([0x04,0x22,0x4d,0x18]);
let idx=-1, hits=[];
while ((idx = buf.indexOf(magic, idx+1)) >= 0) { hits.push(idx); if (hits.length>10) break; }
console.log('LZ4 frame magic hits:', hits.map(x=>'0x'+x.toString(16)));
// scan for Jieli lz4 block header candidates? print SDK ota.bin first 64
const ota = fs.readFileSync('analysis/reference/fw-AC63_BT_SDK/cpu/bd19/tools/ota.bin');
console.log('SDK ota.bin size', ota.length, 'first64:', ota.slice(0,64).toString('hex'));
console.log('SDK ota.bin ascii:', JSON.stringify(ota.slice(0,64).toString('latin1')));
