const fs = require('fs');
for (let n = 0; n < 16; n++) {
  const p = '\\\\.\\PhysicalDrive' + n;
  try {
    const fd = fs.openSync(p, 'rs');
    const buf = Buffer.alloc(512);
    fs.readSync(fd, buf, 0, 512, 0);
    const size = fs.fstatSync(fd).size;
    fs.closeSync(fd);
    console.log(`PhysicalDrive${n} size=${size} first32=${buf.slice(0,32).toString('hex')}`);
  } catch (e) {
    console.log(`PhysicalDrive${n} FAIL: ${e.code || e.message}`);
  }
}
