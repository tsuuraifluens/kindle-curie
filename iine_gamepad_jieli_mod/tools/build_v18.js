const fs=require('fs');
const { build, defaultPatches } = require('./build_kindle_click.js');
const patches = defaultPatches();
// PnP builder constants: Microsoft VID 0x045E -> Amazon 0x0171; Xbox PIDs -> 0x0443
patches.push(['PnP VID 0x045E -> 0x0171', 0x4168, Buffer.from([0x04,0xfb,0x71,0x01])]);
patches.push(['PnP PID 0x02FD -> 0x0443', 0x4174, Buffer.from([0x00,0xfb,0x43,0x04])]);
patches.push(['PnP PID 0x0B13 -> 0x0443', 0x417a, Buffer.from([0x00,0xfb,0x43,0x04])]);
patches.push(['PnP PID 0x028E -> 0x0443', 0x4180, Buffer.from([0x00,0xfb,0x43,0x04])]);
patches.push(['PnP PID +0x5C path NOP', 0x4188, Buffer.from([0x00,0x00])]);
const r = build('analysis/firmware/L1161 L1162.ufw','work/our_v18_pnp.ufw',{patches});
console.log(JSON.stringify(r,null,2));
