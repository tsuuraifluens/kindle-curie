const fs=require('fs');
const a=fs.readFileSync('work/our_candidate_pnp.ufw');
const b=fs.readFileSync('C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.7.ufw');
let r=[],i=0;while(i<a.length){if(a[i]!==b[i]){let j=i;while(j<a.length&&a[j]!==b[j])j++;r.push([i,j]);i=j;}else i++;}
console.log('diff ranges:',r.length);for(const [s,e] of r)console.log('  0x'+s.toString(16)+'..0x'+e.toString(16)+' ('+(e-s)+'B)');
const c=require('crypto');console.log('sha:',c.createHash('sha256').update(a).digest('hex').toUpperCase());
