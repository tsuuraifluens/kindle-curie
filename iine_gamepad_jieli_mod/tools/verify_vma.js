const fs = require('fs');
const dec = fs.readFileSync('work/app_dec.bin');
const base = 0x1E00120;
function dump(vma, len, label){
  const off = vma - base;
  console.log('\n=== '+label+' VMA 0x'+vma.toString(16)+' (file 0x'+off.toString(16)+') ===');
  console.log('hex:', dec.slice(off, off+len).toString('hex'));
  console.log('asc:', JSON.stringify(dec.slice(off, off+len).toString('latin1')));
}
dump(0x1E212E6, 12, 'IINE_reader');
dump(0x1E212F2, 25, 'Xbox Controller');
dump(0x1E224DC, 10, 'PNP_ID');
dump(0x1E224A4, 13, 'DIS Manufacturer');
dump(0x1E2292C, 58, 'HID Reader Consumer Descriptor');
