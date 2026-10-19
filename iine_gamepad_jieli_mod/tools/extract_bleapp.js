const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
fs.writeFileSync('work/ble_app_ota.bin', buf.slice(0x49b00, 0x49b00+0x3000));
console.log('wrote work/ble_app_ota.bin');
