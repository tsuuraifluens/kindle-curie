const fs=require('fs');
const orig=fs.readFileSync('work/app_dec32.bin');
const v19=fs.readFileSync('work/v18_app.bin'); // placeholder
// decrypt v19
const { sfcCipher } = require('./build_kindle_click.js');
const raw=fs.readFileSync('outputs/IINE_Kindle_Click_v19_PnPdirect.ufw');
const dec=Buffer.from(raw.slice(0x400,0x400+0x34000)); sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
const app=dec.slice(0x2120);
console.log('Table1 region 0x2289C..0x2295C (entry: orig -> v19)');
for(let o=0x2289c;o<0x2295c;o+=6){
  const a=orig.slice(o,o+6).toString('hex');
  const b=app.slice(o,o+6).toString('hex');
  const mark = a!==b ? '  <== patched' : '';
  console.log('0x'+o.toString(16), a, '->', b, mark);
}
