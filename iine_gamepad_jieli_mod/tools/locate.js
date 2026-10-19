const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
console.log('size =', buf.length, '= 0x' + buf.length.toString(16));
const keys = ['ble_ota.bin','edr_ota2.bin','ble_app_ota.bin','uart_user.bin','usb_hid_ota.bin','JLBTSDK','flash.bin','uboot.boot','isd_config.ini','app_dir_head','AC632N_update','632N_LE_UPDATE','JL-BR22','br22xx','spv21p','Sep 28 2022','2_3_0_0','PB01_08_0','UPDATE_JUMP','EOFFSET','autotest','H4_Controller','btstack','link_layer','btctrler','POWER_PIN','USB1DP','USB1DM','date','DATE'];
for (const k of keys) {
  const idx = buf.indexOf(Buffer.from(k,'ascii'));
  console.log(k.padEnd(20), idx >= 0 ? ('0x'+idx.toString(16)+'  ('+idx+')') : 'not found');
}
console.log('--- last 64 bytes ---');
console.log(buf.slice(-64).toString('hex').replace(/(..)/g,'$1 '));
