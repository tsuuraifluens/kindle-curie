const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
fs.writeFileSync('work/app_area.bin', buf.slice(0x2400, 0x4400));
console.log('wrote work/app_area.bin', 0x2000, 'bytes');
