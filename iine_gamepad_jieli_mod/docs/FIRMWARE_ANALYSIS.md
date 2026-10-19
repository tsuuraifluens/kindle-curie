# 固件结构、分区与升级格式分析

对象: `L1161 L1162.ufw` (645184 字节, 0x9D840)

## 1. 文件总体结构 [已确认 + 高置信推断]

| 偏移 | 内容 | 依据 |
|---|---|---|
| 0x000000 - 0x9D81F | 固件主体 (分区数据; 代码区为加扰/LZ4) | 熵分析 + 明文区定位 |
| 0x9D820 - 0x9D827 | 8 字节 (疑似 CRC/参数) | 十六进制观察 |
| 0x9D828 - 0x9D82F | 8 字节 00 | 观察 |
| 0x9D830 - 0x9D834 | `4A 4C 55 46 57` = "JLUFW" 魔数 | 观察 |
| 0x9D835 - 0x9D83F | 11 字节 00 | 观察 |

## 2. 已解码: SYS_CFG_PARAM 编译配置 (偏移 0x17A0-0x17F9) [已确认]

格式: [值长度 1B][键名 NUL 结尾][值], 与 SDK `isd_config_rule.c` 的 [SYS_CFG_PARAM] 顺序一致。

| 键 | 值 | 说明 |
|---|---|---|
| SPI | `2_3_0_0` | width=2, clk_div=3, mode=0, port=A(CS:PD3 CLK:PD0 D0:PD1...) |
| UTTX | `PA05` | uboot 调试串口 TX, 波特率 1M |
| UTRX | `PP00`(=PB00) | 串口升级 RX |
| RESET | `PB01_08_0` | 复位脚 PB01, 长按 8 秒, 电平 0 |
| UPDATE_JUMP | 0 | 关 |
| EOFFSET | 0 | 无 4K 偏移 |

其余区域 (0x1800-0x2400) 为 0xFF 空白; 0x0-0x17A0 与 0x2400 之后为加扰/LZ4 数据。

## 3. 分区名 (明文区) [已确认]
- 0x346d0: `ble_ota.bin` / `edr_ota2.bin` / `ble_app_ota.bin` / `uart_user.bin` / `usb_hid_ota.bin` (JLBTSDK OTA 分区)
- 0x3ab60: uboot 字符串表 (XIP 指针 0x01E0xxxx → flash 映射基址 0x1E00000), 分区 `VM`/`BTIF`/`flash.bin`/`uboot.boot`/`isd_config.ini`/`app_dir_head`; flash 部件 ID `1H22/1Hc08/1211/1214/111/112/1c08/222D` 等

## 4. 生成管线 (SDK 工具链, 已确认)
1. `llvm-objcopy` 切出 text/data/data_code + overlay bank, `lz4_packet.exe` 打包 `bank.bin` → 合并 `app.bin`。
2. `isd_download.exe isd_config.ini -tonorflash -dev bd19 -boot 0x2000 -div8 -wait 300 -uboot uboot.boot -app app.bin cfg_tool.bin -res p11_code.bin -uboot_compress -flash-params flash_params.bin` → 生成量产文件 `jl_isd.fw`。
3. `fw_add.exe -noenc -fw jl_isd.fw -add ota.bin -type 100` + `-add script.ver` 注入 OTA 与版本。
4. `ufw_maker.exe -fw_to_ufw jl_isd.fw` → `update.ufw` (即 .ufw 升级包)。

## 5. 升级包尾部 trailer [已确认存在, 校验算法待验证]
32 字节: `[8B CRC/参数][8B 00]["JLUFW"][11B 00]`。精确校验算法需反汇编 `ufw_maker.exe` 或获取杰理内部格式文档后确认。

## 6. 安全与设备唯一数据
- 原厂升级勾选 "擦除整个FLASH" → 整片擦除 (约 1MB)。
- 未在明文区发现蓝牙地址/配对密钥/校准数据 (可能位于 VM/BTIF 分区, 加扰区不可见)。
- 刷写前必须确认: 蓝牙地址是否由 VM 区保存、原厂恢复路径是否可用。

## 7. 固件布局表 (分区级)
| 分区/区域 | 偏移(近似) | 性质 | 置信度 |
|---|---|---|---|
| 主体起始区(疑似 uboot/加密头) | 0x0000-0x17A0 | 加扰 | 高置信推断 |
| SYS_CFG_PARAM 编译配置 | 0x17A0-0x17F9 | 明文 | **已确认(已解码)** |
| 空白区 | 0x1800-0x2400 | 0xFF | 已确认 |
| 代码/数据区 | 0x2400-0x346D0 | 加扰/LZ4 | 高置信推断 |
| OTA 分区名表 | 0x346D0 | 明文 | 已确认 |
| uboot 字符串/分区表 | 0x3AB60-0x3ACC0 | 明文 | 已确认 |
| 构建时间戳 | 0x642B9 | 明文 | 已确认 |
| .ufw trailer | 0x9D820 | "JLUFW" | 已确认 |