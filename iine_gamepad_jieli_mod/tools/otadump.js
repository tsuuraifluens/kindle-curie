const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
function dump(off,len,label){
  console.log('\n=== '+label+' @0x'+off.toString(16)+' ===');
  for(let o=off;o<off+len;o+=16){
    const c=buf.slice(o,o+16);
    const hex=Array.from(c).map(b=>b.toString(16).padStart(2,'0')).join(' ');
    const asc=Array.from(c).map(b=>(b>=32&&b<127)?String.fromCharCode(b):'.').join('');
    console.log(o.toString(16).padStart(6,'0')+'  '+hex.padEnd(47)+'  '+asc);
  }
}
dump(0x34760,0x100,'ble_ota start (table base+0xA0)');
dump(0x49b00,0x100,'ble_app_ota start (table base+0x15440)');
dump(0x5a288,0x80,'uart_user start');
dump(0x5df6c,0x80,'usb_hid_ota start');
