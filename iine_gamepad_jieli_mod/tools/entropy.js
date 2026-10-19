const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
// entropy per 256-byte block
function entropy(b) { const c = new Array(256).fill(0); for (const x of b) c[x]++; let h=0; for (const n of c) if(n){const p=n/b.length; h-=p*Math.log2(p);} return h; }
const total = entropy(buf);
console.log('file size:', buf.length);
console.log('overall entropy:', total.toFixed(3), 'bits/byte');
// first blocks entropy
console.log('first 16 blocks (256B each) entropy:');
for (let i=0;i<16;i++){ console.log(i, entropy(buf.slice(i*256,(i+1)*256)).toFixed(3)); }
// where does low-entropy region start? sample 1KB blocks
console.log('1KB block entropy samples:');
for (let off=0; off<buf.length; off+=4096){ console.log(off, entropy(buf.slice(off,off+1024)).toFixed(3)); }
