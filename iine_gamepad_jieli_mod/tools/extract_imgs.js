const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const imgs = [
  ['ble_ota', 0x34760, 0xBBF4],
  ['edr_ota2', 0x40374, 0x978C],
  ['ble_app_ota', 0x49b00, 0x10788],
  ['uart_user', 0x5a288, 0x3CE4],
  ['usb_hid_ota', 0x5df6c, 0x6344],
];
for (const [n, off, size] of imgs) {
  fs.writeFileSync('work/img_'+n+'.bin', buf.slice(off, off+size));
  console.log(n, '0x'+off.toString(16), 'size 0x'+size.toString(16));
}
