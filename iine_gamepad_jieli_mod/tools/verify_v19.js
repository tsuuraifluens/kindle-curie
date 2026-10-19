const fs=require('fs');const {sfcCipher}=require('./build_kindle_click.js');
const raw=fs.readFileSync('outputs/IINE_Kindle_Click_v19_PnPdirect.ufw');
const dec=Buffer.from(raw.slice(0x400,0x400+0x34000));sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
const app=dec.slice(0x2120);
console.log('PnP branch @0x44AE..0x44D2:');
console.log(app.slice(0x44ae,0x44d2).toString('hex'));
console.log('Table1 A/B:', app.slice(0x2289c,0x228a8).toString('hex'));
console.log('GAP:', app.slice(0x211c6,0x211cf).toString());
