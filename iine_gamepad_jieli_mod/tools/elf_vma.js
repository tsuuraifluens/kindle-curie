// Patch the virtual address (sh_addr) of a named section in an ELF32 file.
const fs = require('fs');

function patchSectionVma(path, targetName, vma) {
  const b = fs.readFileSync(path);
  if (b.readUInt32BE(0) !== 0x7f454c46) throw new Error('not ELF');
  const shoff = b.readUInt32LE(0x20);
  const shentsize = b.readUInt16LE(0x2e);
  const shnum = b.readUInt16LE(0x30);
  const shstrndx = b.readUInt16LE(0x32);
  const strSecOff = b.readUInt32LE(shoff + shstrndx * shentsize + 0x10);
  const strSecSize = b.readUInt32LE(shoff + shstrndx * shentsize + 0x14);
  const strtab = b.slice(strSecOff, strSecOff + strSecSize);
  const nameAt = (off) => {
    let end = off;
    while (end < strtab.length && strtab[end] !== 0) end++;
    return strtab.slice(off, end).toString('latin1');
  };
  for (let i = 0; i < shnum; i++) {
    const base = shoff + i * shentsize;
    const nameOff = b.readUInt32LE(base);
    const name = nameAt(nameOff);
    if (name === targetName) {
      const old = b.readUInt32LE(base + 0x0c);
      b.writeUInt32LE(vma >>> 0, base + 0x0c);
      fs.writeFileSync(path, b);
      return { name, index: i, old, vma };
    }
  }
  throw new Error('section not found: ' + targetName);
}

if (require.main === module) {
  const [path, name, vmaStr] = process.argv.slice(2);
  const r = patchSectionVma(path, name, parseInt(vmaStr, 16));
  console.log('patched', JSON.stringify(r));
}

module.exports = { patchSectionVma };
