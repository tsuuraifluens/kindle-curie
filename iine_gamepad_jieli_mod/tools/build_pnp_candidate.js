const fs=require('fs');
const { build, defaultPatches } = require('./build_kindle_click.js');
const patches = defaultPatches();
// correct PnP ID: 7 bytes at app.bin 0x223BC (read handler source)
patches.push(['DIS PnP ID (7B @0x223BC)', 0x223bc, Buffer.from([0x02,0x71,0x01,0x43,0x04,0x00,0x01])]);
const r = build('analysis/firmware/L1161 L1162.ufw','work/our_candidate_pnp.ufw',{patches});
console.log(JSON.stringify(r,null,2));
