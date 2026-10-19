const fs=require('fs');
const dec=fs.readFileSync('work/app_dec32.bin');
const pats=[
 ['01 3B 05 07 10 01 00','013b0507100100'],
 ['3B 05 07 10','3b050710'],
 ['01 71 01 43 04 00 01','01710143040001'],
];
for(const [label,hex] of pats){
  const p=Buffer.from(hex,'hex');
  let i=-1, hits=[];
  while((i=dec.indexOf(p,i+1))>=0) hits.push(i);
  console.log(label, 'hits:', hits.map(o=>'0x'+o.toString(16)).join(', ')||'none');
}
// dump around 0x223B0..0x22400
const off=0x223b0;
console.log('\napp.bin 0x223B0..0x22400:');
console.log(dec.slice(off,off+0x50).toString('hex'));
console.log(JSON.stringify(dec.slice(off,off+0x50).toString('latin1')));
