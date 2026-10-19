const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
// search raw for AC632N / chip series strings
for (const s of ['AC632N','AC63','632N','BD19','bd19']) {
  let i = -1, n = 0, first = -1;
  while ((i = buf.indexOf(Buffer.from(s,'ascii'), i+1)) >= 0) { n++; if (first<0) first=i; }
  console.log(s.padEnd(8), 'count='+n, first>=0 ? 'first=0x'+first.toString(16) : '');
}
// dump raw around key_mac region (base 0x400 + 0x7F000)
for (const base of [0x400, 0x4A0]) {
  const off = base + 0x7F000;
  console.log('\n=== raw @0x'+off.toString(16)+' (key_mac, base=0x'+base.toString(16)+') ===');
  for (let o = off; o < off + 0x100; o += 16) {
    const chunk = buf.slice(o, o+16);
    const hex = Array.from(chunk).map(b=>b.toString(16).padStart(2,'0')).join(' ');
    const asc = Array.from(chunk).map(b=> (b>=32&&b<127)?String.fromCharCode(b):'.').join('');
    console.log(o.toString(16).padStart(6,'0') + '  ' + hex.padEnd(47) + '  ' + asc);
  }
}
