const fs=require('fs');
const dec=fs.readFileSync('work/app_dec32.bin'); // VMA base 0x1E00120
function findHex(hex,label){ const p=Buffer.from(hex,'hex'); let i=-1,hits=[]; while((i=dec.indexOf(p,i+1))>=0) hits.push(i+0x1E00120); console.log(label, hits.map(o=>'0x'+o.toString(16)).join(', ')||'none'); }
// little-endian 32-bit immediates as stored in mov rX, imm32 (byte order?)
findHex('dc24e201','imm 0x1E224DC LE bytes dc 24 e2 01');
findHex('d424e201','imm 0x1E224D4');
findHex('bc23e201','imm 0x1E223BC');
findHex('c023e201','imm 0x1E223C0');
// print strings near 0x1E1FAA0
const off=0x1E1FAA0-0x1E00120;
console.log('\n@0x1E1FAA0:', JSON.stringify(dec.slice(off,off+64).toString('latin1')));
