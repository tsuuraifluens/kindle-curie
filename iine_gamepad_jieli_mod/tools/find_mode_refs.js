const fs=require('fs');
const d=fs.readFileSync('work/v18_app.bin');
function find(v,label){
  // imm32 little-endian bytes
  const b=Buffer.alloc(4); b.writeUInt32LE(v>>>0,0);
  let i=-1,hits=[];
  while((i=d.indexOf(b,i+1))>=0) hits.push('0x'+(0x1E00120+i).toString(16));
  console.log(label, hits.join(', ')||'none');
}
find(0x1E212E6,'ref IINE_reader 0x1E212E6');
find(0x1E2130B,'ref IINE_keyboard 0x1E2130B');
find(0x1E21319,'ref IINE_mouse');
find(0x1E2132F,'ref IINE_control');
