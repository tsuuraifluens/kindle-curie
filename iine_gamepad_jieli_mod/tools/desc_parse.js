const fs=require('fs');
const { sfcCipher } = require('./build_kindle_click.js');
const raw=fs.readFileSync('outputs/IINE_Kindle_Click_v19_PnPdirect.ufw');
const dec=Buffer.from(raw.slice(0x400,0x400+0x34000)); sfcCipher(dec,0x2000,0x33cba-0x2000,0x2000,0x190d);
const app=dec.slice(0x2120);
for(const [off,len,name] of [[0x2248c,380,'gamepad'],[0x22608,64,'keyboard'],[0x22648,68,'mouse'],[0x2280c,58,'reader consumer']]){
  console.log('\n=== '+name+' @0x'+off.toString(16)+' ('+len+'B) ===');
  const d=app.slice(off,off+len);
  // pretty HID descriptor parse (basic)
  let out=[]; let i=0;
  while(i<d.length){ const b=d[i]; const sz=[0,1,2,4][b&3]; const type=(b>>2)&3; const tag=b>>4;
    const val = sz? d.readUIntLE(i+1,sz):0;
    out.push('0x'+b.toString(16).padStart(2,'0')+(sz?(' '+Array.from(d.slice(i+1,i+1+sz)).map(x=>x.toString(16).padStart(2,'0')).join(' ')):'')+'  (type'+type+' tag0x'+tag.toString(16)+')');
    i+=1+sz; }
  console.log(out.join('\n'));
}
