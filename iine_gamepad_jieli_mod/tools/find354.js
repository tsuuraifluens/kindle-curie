const fs=require('fs');
const lines=fs.readFileSync('work/app_dec.lst','utf8').split(/\r?\n/);
console.log('=== writes/read near 0x2D12 (offset 354 from 0x2BB0) ===');
lines.filter(l=>/\+\s*354|0x2d12|2d12\]/.test(l)).slice(0,30).forEach(l=>console.log('  '+l.trim()));
console.log('=== find function prologue before 0x1E044C0 ===');
