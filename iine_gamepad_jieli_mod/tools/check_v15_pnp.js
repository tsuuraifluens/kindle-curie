const fs=require('fs');
const { sfcCipher } = require('./build_kindle_click.js');
function decryptApp(path){
  const raw=fs.readFileSync(path);
  const dec=Buffer.from(raw.slice(0x400,0x400+0x34000));
  sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
  return dec;
}
const base='C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/';
const dec=decryptApp(base+'IINE_Kindle_Click_Mod_v1.5.ufw');
console.log('v1.5 @0x223B0:', dec.slice(0x223b0,0x22400).toString('hex'));
console.log('v1.5 @0x223B0 asc:', JSON.stringify(dec.slice(0x223b0,0x22400).toString('latin1')));
const dec0=decryptApp('analysis/firmware/L1161 L1162.ufw');
console.log('orig @0x223B0:', dec0.slice(0x223b0,0x22400).toString('hex'));
