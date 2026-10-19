const fs = require('fs');
const base='C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/';
const orig=fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const v15=fs.readFileSync(base+'IINE_Kindle_Click_Mod_v1.5.ufw');
function c16(d,init=0){let c=init&0xffff;for(const b of d){c^=(b<<8);for(let i=0;i<8;i++)c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff;}return c&0xffff;}
function c16r(d,init=0){let c=init&0xffff;for(const b of d){c^=b;for(let i=0;i<8;i++)c=(c&1)?((c>>>1)^0x8408)&0xffff:(c>>>1);}return c&0xffff;}
const tests=[
 ['whole',0,0x9d840],['body',0,0x9d820],['from4',4,0x9d840],['from0x40',0x40,0x9d840],
 ['hdr+list',0,0x2c0],['entry0data',0x400,0x34400],['entry3data',0x346c0,0x346c0+0x35f78],
 ['app_flash',0x400,0x34400],
];
for(const [nm,s,e] of tests){
  const a=orig.slice(s,e), b=v15.slice(s,e);
  console.log(nm.padEnd(12), 'c16A=0x'+c16(a).toString(16), 'c16B=0x'+c16(b).toString(16), 'c16rA=0x'+c16r(a).toString(16), 'c16rB=0x'+c16r(b).toString(16));
}
console.log('want H 0xb9c1/0x795b, L 0x41c9/0xcb94');
