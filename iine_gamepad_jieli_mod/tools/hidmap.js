const fs=require('fs');
const t=fs.readFileSync('work/hid-input.c','utf8');
const m=t.match(/static const unsigned char hid_keyboard\[256\] = \{([\s\S]*?)\};/);
if(!m){console.log('array not found');process.exit(1);}
const nums=m[1].match(/\d+/g).map(Number);
console.log('count',nums.length);
for(const idx of [0x4b,0x4e,0x66,0x6f,0x70,0x71,0x72,0x73,0x74]){
  console.log('usage 0x'+idx.toString(16)+' -> evdev', nums[idx]);
}
