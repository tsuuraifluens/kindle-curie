const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
for (const [off,len,label] of [[0x17840,0x100,'ble_app_ota start'],[0x24a0,0x100,'ble_ota start'],[0x27fc8,0x80,'uart_user start'],[0x2bcac,0x80,'usb_hid_ota start'],[0x34700,0x100,'post-table']]) {
  console.log('\n=== '+label+' @0x'+off.toString(16)+' ===');
  for (let o=off;o<off+len;o+=16){
    const c=buf.slice(o,o+16);
    const hex=Array.from(c).map(b=>b.toString(16).padStart(2,'0')).join(' ');
    const asc=Array.from(c).map(b=>(b>=32&&b<127)?String.fromCharCode(b):'.').join('');
    console.log(o.toString(16).padStart(6,'0')+'  '+hex.padEnd(47)+'  '+asc);
  }
}
