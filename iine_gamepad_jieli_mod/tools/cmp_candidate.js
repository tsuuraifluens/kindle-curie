const fs=require('fs');
const a=fs.readFileSync('work/our_candidate.ufw');
const b=fs.readFileSync('C:/Users/weish/Documents/antigravity/kind-curie/kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.7.ufw');
console.log('sizes',a.length,b.length);
let ranges=[],i=0;
while(i<Math.min(a.length,b.length)){ if(a[i]!==b[i]){let j=i;while(j<Math.min(a.length,b.length)&&a[j]!==b[j])j++;ranges.push([i,j]);i=j;} else i++; }
console.log('diff ranges:',ranges.length);
for(const [s,e] of ranges.slice(0,30)) console.log('  0x'+s.toString(16)+'..0x'+e.toString(16)+' ('+(e-s)+'B)');
const crypto=require('crypto');
console.log('sha ours:',crypto.createHash('sha256').update(a).digest('hex').toUpperCase());
console.log('sha v1.7:',crypto.createHash('sha256').update(b).digest('hex').toUpperCase());
