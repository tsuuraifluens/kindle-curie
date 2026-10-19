const fs=require('fs');const d=fs.readFileSync('work/app_dec32.bin');
for(const [h,l] of [['c023e201','0x1E223C0'],['c423e201','0x1E223C4'],['bc23e201','0x1E223BC'],['dc24e201','0x1E224DC']]){const p=Buffer.from(h,'hex');let i=-1,r=[];while((i=d.indexOf(p,i+1))>=0)r.push('0x'+(0x1E00120+i).toString(16));console.log(l,r.join(', ')||'none');}
