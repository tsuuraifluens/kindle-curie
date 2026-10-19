const fs = require('fs');
const { PDFParse } = require('pdf-parse');
(async () => {
  const buf = fs.readFileSync(process.argv[2]);
  const parser = new PDFParse({ data: buf });
  const r = await parser.getText();
  fs.writeFileSync(process.argv[3], r.text, 'utf8');
  console.log('pages:', r.pages?.length ?? '?', 'chars:', r.text.length);
  console.log(r.text.slice(0, 400));
})().catch(e => { console.error('ERR', e.message); process.exit(1); });
