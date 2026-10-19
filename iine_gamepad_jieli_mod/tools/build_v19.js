const fs=require('fs');
const { build, defaultPatches } = require('./build_kindle_click.js');
const patches = defaultPatches();
// Direct PnP value write in the read branch (bypass helper / early-return)
patches.push(['PnP direct VID=0x0171', 0x44ae, Buffer.from([0x00,0xfb,0x71,0x01])]);
patches.push(['PnP direct PID=0x0443', 0x44c0, Buffer.from([0x00,0xfb,0x43,0x04])]);
patches.push(['PnP helper call NOP',   0x44ce, Buffer.from([0x00,0x00])]);
const r = build('analysis/firmware/L1161 L1162.ufw','outputs/IINE_Kindle_Click_v19_PnPdirect.ufw',{patches});
console.log(JSON.stringify(r,null,2));
