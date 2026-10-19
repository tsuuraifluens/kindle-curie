const fs=require('fs');
const { crc16, sfcCipher } = require('./build_kindle_click.js');
const orig=fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const cand=fs.readFileSync('work/our_v18_pnp.ufw');
let pass=0, fail=0;
function check(name,cond,extra=''){ if(cond){pass++;console.log('PASS '+name+' '+extra);}else{fail++;console.log('FAIL '+name+' '+extra);} }
check('size 645184', cand.length===645184, cand.length);
// bootloader + isd_config identical
check('uboot.boot identical', cand.slice(0x4a0,0x4a0+4831).equals(orig.slice(0x4a0,0x4a0+4831)));
check('isd_config identical', cand.slice(0x177f,0x177f+122).equals(orig.slice(0x177f,0x177f+122)));
// flash image CRC vs entry0
const flash=cand.slice(0x400,0x400+212992);
const e0=Buffer.from(cand.slice(0x40,0x90));
for(let i=0;i<0x50;i++) e0[i]^=[0xff,0xdf,0x9f,0x1f,0x1f,0x3e,0x7c,0xf8,0xf0,0xc1,0xa3,0x67,0xce,0xbd,0x5b,0x97,0x0f,0x1e,0x3c,0x78,0xd1,0x83,0x27,0x4e,0x9c,0x19,0x13,0x26,0x4c,0x98,0x30,0x60][i&31];
check('flash CRC == entry0+4', crc16(flash)===e0.readUInt32LE(4), '0x'+crc16(flash).toString(16));
// header/list
const h=Buffer.from(cand.slice(0,0x40));
const KS=[0xff,0xdf,0x9f,0x1f,0x1f,0x3e,0x7c,0xf8,0xf0,0xc1,0xa3,0x67,0xce,0xbd,0x5b,0x97,0x0f,0x1e,0x3c,0x78,0xd1,0x83,0x27,0x4e,0x9c,0x19,0x13,0x26,0x4c,0x98,0x30,0x60];
for(let i=0;i<0x40;i++) h[i]^=KS[i&31];
const num=h.readUInt16LE(8), hs=0x40+num*0x50;
check('list CRC', crc16(cand.slice(0x40,hs))===h.readUInt16LE(2), '0x'+h.readUInt16LE(2).toString(16));
check('header CRC', crc16(h.slice(2,0x40))===h.readUInt16LE(0), '0x'+h.readUInt16LE(0).toString(16));
// decrypt app and check patches
const dec=Buffer.from(cand.slice(0x400,0x400+0x34000));
sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
const app=dec.slice(0x2120);
check('app data CRC', crc16(app.slice(0,198824))===dec.readUInt16LE(0x2022));
check('PnP VID', app.slice(0x4168,0x416c).toString('hex')==='04fb7101');
check('PnP PID', app.slice(0x417a,0x417e).toString('hex')==='00fb4304');
check('GAP reader', app.slice(0x211c6,0x211c6+9).toString()==='Kindle-PT');
check('DIS Amazon', app.slice(0x22384,0x22384+6).toString()==='Amazon');
check('ADV r6=4', app.slice(0x4d90,0x4d92).toString('hex')==='4670');
check('T1 A PageDown', app.slice(0x2289c,0x228a2).toString('hex')==='0200004e0000');
check('T1 B PageUp', app.slice(0x228a2,0x228a8).toString('hex')==='0200004b0000');
console.log(`\nRESULT: ${pass} passed, ${fail} failed`);
