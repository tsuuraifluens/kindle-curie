const fs = require('fs');
const game = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const sdkU = fs.readFileSync('analysis/reference/fw-AC63_BT_SDK/cpu/bd19/tools/uboot.boot');
const { KS } = require('./descramble.js');
console.log('SDK uboot.boot size:', sdkU.length, '= 0x' + sdkU.length.toString(16));
console.log('gamepad uboot region (base 0x400 + 0xA0 = 0x4A0), size 4831');
console.log('SDK  first32:', sdkU.slice(0,32).toString('hex'));
console.log('RAW  first32:', game.slice(0x4A0,0x4C0).toString('hex'));
// XOR raw with SDK to see keystream if any
const x = Buffer.alloc(32);
for (let i=0;i<32;i++) x[i] = game[0x4A0+i] ^ sdkU[i];
console.log('RAW^SDK      :', x.toString('hex'));
// test XOR with periodic KS
const d = Buffer.alloc(32);
for (let i=0;i<32;i++) d[i] = game[0x4A0+i] ^ KS[i];
console.log('RAW^KS       :', d.toString('hex'));
// count equal bytes
let eq=0; for(let i=0;i<Math.min(sdkU.length,4831);i++){ if(game[0x4A0+i]===sdkU[i]) eq++; }
console.log('raw vs sdk identical bytes:', eq, '/', Math.min(sdkU.length,4831));
