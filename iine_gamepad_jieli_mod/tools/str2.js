const fs=require('fs');const d=fs.readFileSync('work/app_dec32.bin');
for(const v of [0x1E1FAA0,0x1E1FAFC]){const o=v-0x1E00120;console.log('0x'+v.toString(16),JSON.stringify(d.slice(o,o+48).toString('latin1')));}
