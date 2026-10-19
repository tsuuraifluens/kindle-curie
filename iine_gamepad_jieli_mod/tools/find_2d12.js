const fs=require('fs');
const lines=fs.readFileSync('work/app_dec.lst','utf8').split(/\r?\n/);
console.log('=== references to 0x2D12 (RAM config) ===');
lines.filter(l=>/2d12/.test(l)).slice(0,20).forEach(l=>console.log('  '+l.trim()));
console.log('=== references to 11184 (0x2BB0 base) ===');
lines.filter(l=>/11184|0x2BB0/.test(l)).slice(0,10).forEach(l=>console.log('  '+l.trim()));
