const fs = require('fs');
function analyze(p) {
  const buf = fs.readFileSync(p);
  const magic = buf.lastIndexOf(Buffer.from('JLUFW','ascii'));
  console.log('FILE', p);
  console.log('  size', buf.length, '= 0x' + buf.length.toString(16));
  console.log('  JLUFW at', magic >= 0 ? '0x'+magic.toString(16) : 'NOT FOUND');
  console.log('  first 48:', buf.slice(0,48).toString('hex'));
  if (magic >= 0) {
    console.log('  trailer 32B:', buf.slice(buf.length-32).toString('hex'));
    console.log('  pre-trailer 16B:', buf.slice(buf.length-48, buf.length-32).toString('hex'));
  }
}
analyze('analysis/reference/fw-AC63_BT_SDK/cpu/bd29/tools/update.ufw');
analyze('analysis/reference/fw-AC63_BT_SDK/cpu/bd19/tools/AC632N_config_tool/conf/output/default/default_cfg.fw');
analyze('analysis/firmware/L1161 L1162.ufw');
