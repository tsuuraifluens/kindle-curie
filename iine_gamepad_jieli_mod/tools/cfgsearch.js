const fs = require('fs');
const buf = fs.readFileSync('analysis/firmware/L1161 L1162.ufw');
const keys = ['CHIP_NAME','FLASH_SIZE','NEW_FLASH_FS','RESERVED_OPT','DOWNLOAD_MODEL','ENTRY','PID','VID','PDCTNAME','BOOT_FIRST','UPVR_CTL','VM_ADR','VM_LEN','BTIF_ADR','BTIF_LEN','PRCT_ADR','PRCT_LEN','EXIF_ADR','EXIF_LEN','BR22_TWS','FORCE_4K','AREA_ALIGN','SPECIAL_OPT','PLL_SRC','VLVD','SYS_CFG_PARAM','EXTRA_CFG_PARAM','RESERVED_CONFIG','AC6321','AC6322','AC6323','AC6324','AC6328','AC6329','AC632A','AC632B','AC632C','AC632D','AC632E','bd19','BD19'];
for (const k of keys) {
  let idx = -1, count = 0, first = -1;
  while ((idx = buf.indexOf(Buffer.from(k, 'ascii'), idx + 1)) >= 0) { count++; if (first < 0) first = idx; }
  if (count) console.log(k.padEnd(18), 'count=' + count, 'first=0x' + first.toString(16));
}
