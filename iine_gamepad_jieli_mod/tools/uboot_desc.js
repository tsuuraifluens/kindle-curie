const fs = require('fs');
const { KS } = require('./descramble.js');
const a = fs.readFileSync('analysis/reference/fw-AC63_BT_SDK/cpu/bd19/tools/uboot.boot');
const b = fs.readFileSync('analysis/reference/fw-AC63_BT_SDK/cpu/bd19/tools/uboot.boot_debug');
console.log('uboot.boot      ', a.length, a.slice(0,16).toString('hex'));
console.log('uboot.boot_debug', b.length, b.slice(0,16).toString('hex'));
// descramble debug
const d = Buffer.alloc(b.length);
for (let i=0;i<b.length;i++) d[i] = b[i] ^ KS[i & 31];
fs.writeFileSync('work/uboot_dbg_desc.bin', d);
console.log('debug descrambled first16:', d.slice(0,16).toString('hex'));
// also descramble normal uboot
const d2 = Buffer.alloc(a.length);
for (let i=0;i<a.length;i++) d2[i] = a[i] ^ KS[i & 31];
fs.writeFileSync('work/uboot_desc.bin', d2);
console.log('normal descrambled first16:', d2.slice(0,16).toString('hex'));
