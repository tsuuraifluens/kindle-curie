import hashlib, os, glob

files_to_check = [
    'raw_pkg/IINE_L1161_L1162_firmware_pkg.zip',
    'extracted/L1161兼容阅星瞳/L1161 固件升级程序_1.4.40.exe',
    'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw',
    'extracted/L1161兼容阅星瞳/升级步骤.doc',
    'extracted/ufw_unpacked/decrypted.bin',
    'extracted/ufw_unpacked/top/uboot.boot',
    'extracted/ufw_unpacked/top/isd_config.ini',
    'extracted/ufw_unpacked/files/app.bin',
    'extracted/ufw_unpacked/files/cfg_tool.bin',
    'extracted/ufw_unpacked/files/p11_code.bin',
    'candidate/IINE_Kindle_Mod_Candidate_v0.1.ufw',
]

print(f"| {'File Path':<55} | {'Size':<12} | {'SHA-256':<64} |")
print('|' + '-'*57 + '|' + '-'*14 + '|' + '-'*66 + '|')
for p in files_to_check:
    if os.path.exists(p):
        h = hashlib.sha256(open(p, 'rb').read()).hexdigest().upper()
        sz = os.path.getsize(p)
        print(f"| {p:<55} | {sz:<12} | {h:<64} |")
    else:
        print(f"| {p:<55} | {'MISSING':<12} | {'-':<64} |")
