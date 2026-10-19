const fs=require('fs');
const { crc16, crc32, encCipher, sfcCipher } = require('./build_kindle_click.js');
const orig=fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const v17=fs.readFileSync('C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.7.ufw');
const v18=fs.readFileSync('outputs/IINE_Kindle_Click_v18_PnP.ufw');
let P=0,F=0;
function ck(n,c,x=''){ if(c){P++;console.log('PASS '+n+(x?'  '+x:''));}else{F++;console.log('FAIL '+n+(x?'  '+x:''));} }
console.log('=== 1. file ===');
ck('v18 size 645184', v18.length===645184, v18.length);
console.log('=== 2. critical untouched regions vs factory ===');
const regions=[
 ['flash header 0x0-0x20',0x400,0x420],
 ['boot vector 0x20-0xA0',0x420,0x4a0],
 ['uboot.boot',0x4a0,0x137f],
 ['isd_config',0x177f,0x17f9],
 ['gap 0x13F9-0x2000',0x17f9,0x2400],
 ['cfg_tool.bin',0x32dc8,0x3309a],
 ['p11_code.bin',0x3309a,0x3409a],
 ['tail FF 0x33CBA-0x34000',0x340ba,0x34400],
];
for(const [n,s,e] of regions){ ck(n, v18.slice(s,e).equals(orig.slice(s,e))); }
console.log('=== 3. CRC layers ===');
const flash=v18.slice(0x400,0x400+212992);
const e0=Buffer.from(v18.slice(0x40,0x90)); encCipher(e0,0,0x50,0xffff);
ck('flash image CRC -> entry0+4', crc16(flash)===e0.readUInt32LE(4), '0x'+crc16(flash).toString(16));
const h=Buffer.from(v18.slice(0,0x40)); encCipher(h,0,0x40,0xffff);
const num=h.readUInt16LE(8), hs=0x40+num*0x50;
ck('entry list CRC', crc16(v18.slice(0x40,hs))===h.readUInt16LE(2), '0x'+h.readUInt16LE(2).toString(16));
ck('container header CRC', crc16(h.slice(2,0x40))===h.readUInt16LE(0), '0x'+h.readUInt16LE(0).toString(16));
const dec=Buffer.from(v18.slice(0x400,0x400+0x34000)); sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
const app=dec.slice(0x2120);
ck('JLFS app data CRC', crc16(app.slice(0,198824))===dec.readUInt16LE(0x2022), '0x'+dec.readUInt16LE(0x2022).toString(16));
ck('JLFS app entry header CRC', crc16(dec.slice(0x2022,0x2040))===dec.readUInt16LE(0x2020));
ck('JLFS area data CRC', crc16(dec.slice(0x2020,0x2020+0x30c7a))===dec.readUInt16LE(0x2002));
ck('JLFS area header CRC', crc16(dec.slice(0x2002,0x2020))===dec.readUInt16LE(0x2000));
console.log('=== 4. diff v18 vs v1.7 ===');
let r=[],i=0; while(i<v18.length){ if(v18[i]!==v17[i]){ let j=i; while(j<v18.length&&v18[j]!==v17[j]) j++; r.push([i,j]); i=j; } else i++; }
console.log('diff ranges:', r.map(([s,e])=>'0x'+s.toString(16)+'-0x'+e.toString(16)+'('+(e-s)+'B)').join(', '));
ck('only expected ranges', r.every(([s,e])=> (s<4) || (s>=0x44&&e<=0x46) || (s>=0x2400&&e<=0x2404) || (s>=0x2420&&e<=0x2424) || (s>=0x4160&&e<=0x4190)));
console.log('=== 5. UI checksums ===');
console.log('file checksum:', crc16(v18).toString(16).toUpperCase()+'-'+crc32(v18).toString(16).toUpperCase());
console.log('v1.7 checksum:', crc16(v17).toString(16).toUpperCase()+'-'+crc32(v17).toString(16).toUpperCase());
console.log('=== 6. entry table (descrambled) ===');
for(let k=0;k<num;k++){ const g=Buffer.from(v18.slice(0x40+k*0x50,0x40+(k+1)*0x50)); encCipher(g,0,0x50,0xffff);
 console.log('entry'+k, 'idx='+g.readUInt16LE(2), 'crc=0x'+g.readUInt32LE(4).toString(16), 'off=0x'+g.readUInt32LE(8).toString(16), 'size=0x'+g.readUInt32LE(0x0c).toString(16)); }
console.log(`\nSUMMARY: ${P} PASS, ${F} FAIL`);
