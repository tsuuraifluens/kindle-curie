const fs = require('fs');
const { KS } = require('./descramble.js');
const base='C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/';
const names=['IINE_bisect_Z_nopatch','IINE_bisect_A_dataonly','IINE_bisect_B_noredirect','IINE_bisect_I','IINE_bisect_K','IINE_bisect_M','IINE_bisect_N','IINE_bisect_P','IINE_bisect_R','IINE_Kindle_Click_Mod_v1.0','IINE_Kindle_Click_Mod_v1.5','IINE_Kindle_Click_Mod_v1.7'];
const samples=[];
for(const n of names){ const p=base+n+'.ufw'; if(!fs.existsSync(p)) continue; const b=fs.readFileSync(p);
  const h=Buffer.alloc(0x2c0);
  for(let i=0;i<0x40;i++) h[i]=b[i]^KS[i&31];
  for(let i=0;i<8;i++) for(let j=0;j<0x50;j++) h[0x40+i*0x50+j]=b[0x40+i*0x50+j]^KS[j&31];
  samples.push({n,b,h});
}
function c16(d,init){let c=init&0xffff;for(const x of d){c^=(x<<8);for(let i=0;i<8;i++)c=(c&0x8000)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff;}return c&0xffff;}
function c16r(d,init){let c=init&0xffff;for(const x of d){c^=x;for(let i=0;i<8;i++)c=(c&1)?((c>>>1)^0x8408)&0xffff:(c>>>1);}return c&0xffff;}
const algs=[['c16-0',d=>c16(d,0)],['c16-ff',d=>c16(d,0xffff)],['c16r-0',d=>c16r(d,0)],['c16r-ff',d=>c16r(d,0xffff)]];
let hitsH=[], hitsL=[];
for(let a=0;a<=6;a++) for(let b=0x20;b<=0x2c0;b++){
  if(b<=a) continue;
  for(const [nm,f] of algs){
    let okH=true, okL=true;
    for(const s of samples){ const v=f(s.h.slice(a,b)); if(v!==s.h.readUInt16LE(0)) okH=false; if(v!==s.h.readUInt16LE(2)) okL=false; }
    if(okH) hitsH.push(nm+' ['+a+','+b+')');
    if(okL) hitsL.push(nm+' ['+a+','+b+')');
  }
}
console.log('HDR all-match:', hitsH.join(' | ')||'none');
console.log('LIST all-match:', hitsL.join(' | ')||'none');
console.log('samples:', samples.length);
