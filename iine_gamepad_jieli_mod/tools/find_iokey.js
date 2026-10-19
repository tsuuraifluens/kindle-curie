const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
// scan for runs of iokey_port-like entries: [port,0,0|1,key], port 0..63 distinct-ish
const hits = [];
for (let i = 0x2400; i + 4*4 < 0x7f400; i++) {
  let n = 0; const ports = [];
  while (n < 16) {
    const o = i + n*4;
    if (o + 4 > buf.length) break;
    const port = buf[o], out = buf[o+1], cw = buf[o+2], kv = buf[o+3];
    if (port > 63 || out !== 0 || cw > 1) break;
    ports.push(port); n++;
  }
  if (n >= 6) hits.push({off:i, n, ports: ports.slice(0,16)});
}
console.log('iokey-run candidates (n>=6):', hits.length);
for (const h of hits.slice(0,40)) console.log('@0x'+h.off.toString(16).padStart(6,'0'), 'n='+h.n, 'ports=['+h.ports.join(',')+']');
