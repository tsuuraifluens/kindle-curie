const fs=require('fs');
const { sfcCipher } = require('./build_kindle_click.js');
const raw=fs.readFileSync('outputs/IINE_Kindle_Click_v19_PnPdirect.ufw');
const dec=Buffer.from(raw.slice(0x400,0x400+0x34000)); sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
const app=dec.slice(0x2120);
function find(v,label){ const b=Buffer.alloc(4); b.writeUInt32LE(v>>>0,0); let i=-1,hits=[]; while((i=app.indexOf(b,i+1))>=0) hits.push('0x'+(0x1E00120+i).toString(16)); console.log(label.padEnd(34), hits.join(', ')||'none'); }
find(0x1E2260A,'keyboard desc 0x1E2260A');
find(0x1E22608,'keyboard desc 0x1E22608');
find(0x1E2260C,'keyboard desc 0x1E2260C');
find(0x1E2280C,'reader desc');
find(0x1E2248E,'gamepad desc');
find(0x1E2264A,'mouse desc');
