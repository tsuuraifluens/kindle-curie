const fs=require('fs');
const dec=fs.readFileSync('work/app_dec32.bin');
const p=Buffer.from('502a','hex');
let i=-1,hits=[];
while((i=dec.indexOf(p,i+1))>=0) hits.push(i);
console.log('0x2A50 (LE 50 2A) occurrences:', hits.map(o=>'0x'+o.toString(16)).join(', '));
for(const o of hits.slice(0,10)){
  console.log('\n@0x'+o.toString(16));
  console.log(dec.slice(Math.max(0,o-16),o+32).toString('hex'));
}
// dump GATT table head
console.log('\nGATT table @0x22050 first 0x100:');
console.log(dec.slice(0x22050,0x22150).toString('hex'));
