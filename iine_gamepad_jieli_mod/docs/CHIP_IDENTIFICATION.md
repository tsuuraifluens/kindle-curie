# 芯片识别证据与结论

最终结论: **珠海杰理 (Jieli) AC6321A** — AC632N/BR22 家族 BLE SoC, QFN32(4×4mm) 封装, pi32 内核, 73KB data RAM + 8KB I-cache + 1KB Ro-cache。

## 决定性证据: 芯片丝印
丝印: `BP3F723-21A4`
- `BP` = 杰理 BLE 产品线丝印前缀
- `3F723` = 批次/日期码
- `21A` = AC6321A (6321 = 系列, A = 封装 A = QFN32)
- `4`：数据手册 "Package Type Specification" 把 `4` 列为 4Mbit，但**固件布局与 4Mbit(512KB) 矛盾**（见下）。需以 flash 芯片丝印最终确认。

### Flash 容量：固件布局推断为 1MB (8Mbit)
解扰后顶层文件表显示 `key_mac` 位于 0x7F400、长 0x1000，结束于 0x80400（越过 512KB 的 0x80000 边界）；镜像总长 0x9D840 (645184) 亦大于 512KB。因此物理 flash **至少 1MB**。丝印末位 `4` 不宜直接当作 4Mbit。最终仍以板上 8 脚 SPI NOR 芯片丝印为准。

## 佐证证据
### 实机 USB (升级模式, 只读) [已确认]
`USB\VID_4C4A&PID_3442&REV_0300`, Mass Storage 类 (08/06/50)。VID 字节 = "JL"。

### 固件内字符串 [已确认]
`AC632N_update`/`632N_LE_UPDATE`/`JL-BR22`/`br22xx`/`spv21p`/`JLBTSDK`/`2_3_0_0` 等; 构建于 2022-09-28。

### 固件编译配置 (SYS_CFG_PARAM, 已解码) [已确认]
SPI=2_3_0_0; UTTX=PA05; UTRX=PB00; RESET=PB01(长按8s,低有效); UPDATE_JUMP=0; EOFFSET=0。

### SDK 工具链 [已确认]
`cpu/bd19/tools/` (bd19=AC632N); 烧写 `isd_download ... -dev bd19 -boot 0x2000 -div8`; 内核 pi32 (`C:\JL\pi32\bin\llvm-objcopy`)。

## .ufw 容器 trailer (已解码)
文件末尾 32 字节: `[u32 CRC/参数][u32 0x42733665(固定)][8B 0x00]["JLUFW"][11B 0x00]`。
(SDK 参考 bd29 update.ufw 与游戏手柄 .ufw 结构一致, 固定字段 0x42733665 相同。)

## 待确认项
- Flash 容量: 丝印 `4` 指示 **4Mbit(512KB)**; 但 .ufw 文件 630KB > 512KB, 可能是升级包含 OTA 暂存/重复数据所致。**刷写前必须以实际 flash 芯片丝印/万用表确认**(512KB 与 1MB 二选一), 该值决定 SDK 的 CONFIG_FLASH_SIZE。
- 按键 GPIO 映射: 需万用表实测 (见 docs/GPIO_MEASUREMENT_GUIDE.md)。
