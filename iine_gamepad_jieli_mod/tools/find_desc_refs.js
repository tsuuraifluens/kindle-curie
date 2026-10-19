const fs=require('fs');
const d=fs.readFileSync('work/v18_app.bin');
function find(v,label){ const b=Buffer.alloc(4); b.writeUInt32LE(v>>>0,0); let i=-1,hits=[]; while((i=d.indexOf(b,i+1))>=0) hits.push('0x'+(0x1E00120+i).toString(16)); console.log(label.padEnd(28), hits.join(', ')||'none'); }
find(0x1E225AC,'HID Gamepad desc');
find(0x1E22728,'HID Keyboard desc');
find(0x1E22768,'HID Mouse desc');
find(0x1E227C6,'HID Touch desc');
find(0x1E2292C,'HID Reader Consumer desc');
find(0x1E2248C,'desc table base?');
