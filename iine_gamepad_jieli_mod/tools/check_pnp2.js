const fs=require('fs');
const { sfcCipher } = require('./build_kindle_click.js');
function appView(path){
  const raw=fs.readFileSync(path);
  const dec=Buffer.from(raw.slice(0x400,0x400+0x34000));
  sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
  return dec.slice(0x2120); // app.bin-relative
}
const base='C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/';
for(const [n,p] of [['orig','analysis/firmware/L1161 L1162.ufw'],['v1.0',base+'IINE_Kindle_Click_Mod_v1.0.ufw'],['v1.5',base+'IINE_Kindle_Click_Mod_v1.5.ufw'],['v1.7',base+'IINE_Kindle_Click_Mod_v1.7.ufw']]){
  const a=appView(p);
  console.log(n.padEnd(5), '0x223B0:', a.slice(0x223b0,0x223f8).toString('hex'));
}
