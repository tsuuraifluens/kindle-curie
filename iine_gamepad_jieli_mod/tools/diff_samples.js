const fs = require('fs');
const { KS } = require('./descramble.js');
function load(p){ return fs.readFileSync(p); }
function hdr(b){ const o=Buffer.alloc(0x40); for(let i=0;i<0x40;i++) o[i]=b[i]^KS[i&31]; return o; }
function show(name,b){
  const h=hdr(b);
  console.log(name.padEnd(38), 'sz='+b.length, 'hdrCRC=0x'+h.readUInt16LE(0).toString(16), 'listCRC=0x'+h.readUInt16LE(2).toString(16), 'len=0x'+h.readUInt32LE(4).toString(16));
}
const base='C:/Users/weish/Documents/antigravity/kind-curie/';
const orig=load('analysis/firmware/L1161 L1162.ufw');
const files=[
 ['orig', orig],
 ['Z_nopatch', load(base+'kindle_click_jieli_port/firmware/IINE_bisect_Z_nopatch.ufw')],
 ['v1.0', load(base+'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw')],
 ['v1.2', load(base+'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.2.ufw')],
 ['v1.5', load(base+'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.5.ufw')],
 ['v1.7', load(base+'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.7.ufw')],
];
for(const [n,b] of files) show(n,b);
function diff(a,b,label){
  const ranges=[]; let i=0;
  while(i<Math.min(a.length,b.length)){
    if(a[i]!==b[i]){ let j=i; while(j<Math.min(a.length,b.length)&&a[j]!==b[j]) j++; ranges.push([i,j]); i=j; }
    else i++;
  }
  console.log('\n'+label+': ranges='+ranges.length);
  for(const [s,e] of ranges.slice(0,40)) console.log('  0x'+s.toString(16)+'..0x'+e.toString(16)+' ('+(e-s)+'B)');
}
diff(orig, files[1][1], 'orig vs Z_nopatch');
diff(orig, files[4][1], 'orig vs v1.5');
