const fs=require('fs');
const { sfcCipher } = require('./build_kindle_click.js');
const raw=fs.readFileSync('outputs/IINE_Kindle_Click_v18_PnP.ufw');
const dec=Buffer.from(raw.slice(0x400,0x400+0x34000));
sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
fs.writeFileSync('work/v18_app.bin', dec.slice(0x2120));
console.log('wrote work/v18_app.bin');
