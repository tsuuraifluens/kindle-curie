# 固件解扰逆向结果

日期: 2026-10-19

## 1. 扰码算法（已完全破解）[已确认]

杰理 `.ufw`/`.fw` 的头部与 uboot 使用恒定 key `0xFFFF` 的 "ENC block" 扰码。

算法: **CRC16-CCITT 流**
- 多项式 `0x1021`，初值 `0xFFFF`，无反射。
- 每步先输出 `state & 0xFF`，再执行一步 CRC。
- 32 字节为一个块，密钥流周期 32 字节（验证：`encBlock(0xFFFF) == 推导 keystream`）。

实现: `tools/descramble.js`（头部/uboot）与 `tools/app_decrypt.js`（应用区按地址变 key）。

验证方法: 用 SDK 自带 `uboot.boot`(4831B) 与游戏手柄固件 0x4A0 处 4831 字节做 XOR，
结果恰为 32 字节 keystream 重复 → 两者逐字节相同。

## 2. 解出的顶层头部 [已确认]

| 字段 | 值 | 说明 |
|---|---|---|
| 0x04 | `0x0009D840` (645184) | 镜像/文件总长度 |
| 0x08 | `0x00040008` | 待进一步解释（bd19=`0x00030006`, bd29=`0x00000006`） |
| 0x0c | `0x00000200` | 512（对齐/头部尺寸） |
| 0x10 | `"AC632N"` | 芯片族名 |

对照: bd19 参考固件解出 `"BD19"`, bd29 解出 `"AC695X"`，字段结构一致。

## 3. 顶层文件表（偏移基准 = 0x400）[已确认]

| 文件 | 偏移 | 大小 | 校验 |
|---|---|---|---|
| uboot.boot | 0x4A0 | 4831 (0x12DF) | 与 SDK 原版逐字节一致 |
| isd_config.ini | 0x177F | 122 (0x7A) | 内含已解码 SYS_CFG_PARAM |
| app_dir_head | 0x2400 | 至末尾 | 应用区 |
| key_mac | 0x7F400 | 4096 (0x1000) | 疑似 chipkey/MAC 存储 |

连续性验证: 0x4A0+0x12DF=0x177F（isd_config 起点），0x177F+0x7A=0x17F9（配置末尾）。

## 4. 应用区布局 [高置信度推断]

- OTA 分区表位于 0x346C0（明文），条目 `{crc, offset, size, index, name[16]}`：
  ble_ota.bin / edr_ota2.bin / ble_app_ota.bin / uart_user.bin / usb_hid_ota.bin。
- 分区偏移连续，且五段总和恰好结束于 0x346C0（表位置）。
- 应用区含明文 pi32 代码与 SDK 库字符串（`JLBTSDK`, `AC632N_update`, `JL-BR22`, `spv21p`, `Sep 28 2022`），
  说明运行代码未做 chipkey 加密；OTA 暂存镜像为高熵（压缩/加扰）。

## 5. Flash 容量推断 [高置信度]

- `key_mac` 位于 0x7F400，长度 0x1000 → 结束于 0x80400 (525312 字节)，**已越过 512KB(0x80000) 边界**。
- 镜像总长 0x9D840 (645184) 亦大于 512KB。
- 结论: 物理 flash **至少 1MB (8Mbit)**；丝印末位 `4` 不宜直接解释为 4Mbit。
- 仍建议以 flash 芯片丝印最终确认（8 脚 SPI NOR）。

## 6. 按键 GPIO [未取得]

在明文应用区尝试匹配 SDK 三种标准按键结构均未命中：
- `iokey_platform_data {enable,num,port_ptr}`（XIP/RAM 指针扫描）
- `adkey_platform_data`（10×u16 单调阈值数组扫描）
- `matrix_key_param {row_ptr,col_ptr,row_num,col_num}`（双 XIP 指针扫描）

推断: 厂商使用自定义按键扫描（直接寄存器访问）或把键表放在加密的 OTA 镜像中。
进一步提取需要:
1. 杰理 pi32 工具链的 `llvm-objdump`（`C:\JL\pi32`，当前机器没有），或
2. 实机万用表测量。

ROM (`rom.lst`) 确认包含 `CrcDecode` / `check_key_type` / `LZ4_decompress_*`，
但无 GPIO 导出函数（GPIO 走直接寄存器访问），故无法用 ROM 调用点定位按键。

## 7. 工具链与反汇编管线（已打通）

官方 Windows 工具链: `https://pkgman.jieliapp.com/s/win-toolchain`（约 50MB，Inno Setup 5.5.7）。
因安装需管理员，改用 `innoextract` 直接解包到 `tools/toolchain/pi32`（不安装、不改系统）。

可用工具（`C$\JL\pi32\bin`）:
- `llvm-objdump.exe`（LLVM 4.0.1，注册目标 `q32s`/`pi32`/`pi32v2`/`f59`/`f95`）
- `clang.exe`（`-target q32s` 可用）
- `llvm-objcopy.exe`（仅支持 raw/hex 输出，不支持 raw→ELF）

反汇编管线（`tools/elf_vma.js` + clang `.incbin`）:
1. `clang -target q32s -c wrap.s`（`.incbin` 引入原始镜像）
2. 用 `tools/elf_vma.js` 把 `.text` 的 `sh_addr` 改为 `0x1E00000`（匹配 XIP 基址）
3. `llvm-objdump -D`，或用 `--start-address/--stop-address` 定点反汇编

产物: `work/ble_app_full.lst`（`ble_app_ota.bin` 全量反汇编，24569 行，地址已按 0x1E00000 重定位）。

## 8. 镜像布局修正（重要）

OTA 分区表（文件 0x346C0）中的偏移是**相对该表自身**（与 SDK `ota.bin` 的格式一致），
不是相对 App 区起始。因此五个真实镜像位于:

| 镜像 | 文件偏移 | 大小 | 说明 |
|---|---|---|---|
| ble_ota.bin | 0x34760 | 0xBBF4 | BT OTA 更新器 |
| edr_ota2.bin | 0x40374 | 0x978C | EDR 镜像 |
| ble_app_ota.bin | 0x49B00 | 0x10788 | BLE App（主分析对象） |
| uart_user.bin | 0x5A288 | 0x3CE4 | UART 用户区 |
| usb_hid_ota.bin | 0x5DF6C | 0x6344 | USB HID OTA |

每个镜像 = 32 字节头（`crc/大小/0x01E00000/name[16]`）+ **明文 pi32 代码**。
入口示例: `ble_app_ota.bin` 启动代码把 `.data` 从 XIP `0x1E0E224` 复制到 RAM `0x6000`，
随后跳转 `0x1E0316A`。

## 9. ROM 关键函数（已还原）

- `doe(key, data, len, addr)`: `seed = key ^ (addr>>2)`，按 CRC16-CCITT(poly 0x1021)
  生成 keystream（取低字节）与数据异或。即 App/镜像解扰算法。
- `_sdk_mkey_lock(blob32)`: 用 `blob[16..17]` 作 seed 调 `doe`，再校验
  `chip_crc16(blob[6..32]) == u16(blob[4..5])`。用于校验 32 字节 key blob。
- `check_key_type(key)`: 合法 chipkey 常量 `0x16EF` / `0x68AF` / `0x6B7C`。
- `CrcDecode`: 加载器用的 CRC 流解扰，keylcode 表在 ROM `0x106D44`
  （GBK 文本彩蛋“孟黎我爱你，玉林”），与主镜像无关。

已用上述算法与常量尝试解密 App 区，均未得到可读明文；按键配置也不匹配 SDK 的
`iokey`/`adkey`/`matrix` 标准结构，推测为自定义寄存器扫描或运行时配置。

## 10. 反汇编追踪结果（pi32 工具链）

对 5 个明文 OTA 镜像全部完成反汇编（`work/<name>.lst`），并检查了:

- GPIO 寄存器（`JL_PORTA/B/C/D` = 0x1E1400/0x1E1440/0x1E1480/0x1E14C0，
  IN/DIR/PU/PD 偏移 +4/+8/+0x10/+0x14）
- ADC 寄存器（`JL_ADC` = 0x1E7C40，CON/RES = +0/+4）
- `ls_base`(0x1E0000) 及端口偏移的运行时计算

结果: **5 个明文镜像中均未发现 GPIO 或 ADC 按键扫描代码**。
各镜像入口（`reti = r4` 设置）分别为:

| 镜像 | 入口 |
|---|---|
| ble_ota.bin | 0x1E0174A |
| edr_ota2.bin | 0x1E01CD4 |
| ble_app_ota.bin | 0x1E0316A |
| uart_user.bin | 0x1E00CF0 |
| usb_hid_ota.bin | 0x1E01440 |

这些入口均为组件初始化桩，不是带按键扫描的主循环。

结合以下事实:
1. App 区 0x2400–0x346C0（约 205KB）内容为高熵、按 pi32 线性反汇编为乱码；
2. 明文 uboot（SDK 原版，也是 0x4A0 处解扰后的内容）**不调用** `doe`/`_sdk_mkey_lock`；
3. 用官方 chipkey 常量与全 16 位 key 暴力匹配启动序言均未命中。

判断: **主 App 由 ROM 的 p11 引导阶段用芯片 fuse key 解密**，密钥不在 `.ufw` 内可恢复的
位置；`key_mac` 的格式与 `_sdk_mkey_lock` 的 32 字节 blob 不匹配（未通过 CRC 校验）。

因此，仅凭现有原厂升级包继续解出按键 GPIO 的代价很高且结果不确定；
实机万用表测量是当前最短、最可靠的路径。
