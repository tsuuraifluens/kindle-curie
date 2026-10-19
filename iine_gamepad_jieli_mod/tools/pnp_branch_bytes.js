const fs=require('fs');const d=fs.readFileSync('work/v18_app.bin');
const o=0x1E045B4-0x1E00120;
console.log(d.slice(o,o+0x60).toString('hex'));
