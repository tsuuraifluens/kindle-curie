const fs=require('fs');
const { sfcCipher } = require('./build_kindle_click.js');
const raw=fs.readFileSync('outputs/IINE_Kindle_Click_v19_PnPdirect.ufw');
const dec=Buffer.from(raw.slice(0x400,0x400+0x34000)); sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
const app=dec.slice(0x2120);
for(const [s,e,name] of [[0x2295c,0x22a1c,'Table2'],[0x22a1c,0x22a7c,'Table3/4'],[0x22a7c,0x22adc,'next']]){
  console.log('\n=== '+name+' 0x'+s.toString(16)+'..0x'+e.toString(16)+' ===');
  for(let o=s;o<e;o+=6) console.log('0x'+o.toString(16), app.slice(o,o+6).toString('hex'));
}
