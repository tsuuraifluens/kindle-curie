const fs = require('fs');
const dec = fs.readFileSync('work/app_dec32.bin'); // starts at VMA 0x1E00120
function hex(off,n){ return dec.slice(off,off+n).toString('hex'); }
console.log('=== Table1 @app.bin 0x2289C (16 entries) ===');
for (let i=0;i<16;i++){ const o=0x2289C+i*6; console.log('0x'+o.toString(16), hex(o,6)); }
console.log('\n=== PnP ID @app.bin 0x223BC ===', hex(0x223BC, 12));
console.log('=== DIS Mfg @app.bin 0x22384 ===', hex(0x22384, 13), JSON.stringify(dec.slice(0x22384,0x22384+13).toString('latin1')));
console.log('\n=== Table3 @app.bin 0x22A1C (first 12) ===');
for (let i=0;i<12;i++){ const o=0x22A1C+i*6; console.log('0x'+o.toString(16), hex(o,6)); }
