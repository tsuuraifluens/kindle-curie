const fs=require('fs');
const d=fs.readFileSync('work/app_dec32.bin');
// call rel encodings are relative; instead scan the disassembly text for target 1e04272
const lines=fs.readFileSync('work/app_dec.lst','utf8').split(/\r?\n/);
const hits=lines.filter(l=>/1e04272/.test(l));
console.log('references to 0x1E04272 in disassembly:');
hits.forEach(l=>console.log('  '+l.trim()));
